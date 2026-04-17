package sh.skills.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import sh.skills.agents.AgentRegistry;
import sh.skills.blob.BlobDownloader;
import sh.skills.blob.BlobDownloader.*;
import sh.skills.lock.LocalLock;
import sh.skills.lock.SkillLock;
import sh.skills.model.AgentConfig;
import sh.skills.model.Skill;
import sh.skills.model.SkillLockEntry;
import sh.skills.providers.HostProvider;
import sh.skills.providers.ProviderRegistry;
import sh.skills.model.ParsedSource;
import sh.skills.tui.Prompts;
import sh.skills.tui.Prompts.SelectOption;
import sh.skills.util.Console;
import sh.skills.util.PathUtils;
import sh.skills.util.SourceParser;
import sh.skills.util.Telemetry;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Implements `skills add <source>`.
 * Installs skills from GitHub/GitLab/local sources to one or more agents.
 * Mirrors src/add.ts from the TypeScript source.
 */
@Command(
    name = "add",
    aliases = {"install", "a", "i"},
    description = "Install skills from a source (GitHub, GitLab, local path)",
    mixinStandardHelpOptions = true
)
public class AddCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Skill source: owner/repo, GitHub URL, GitLab URL, or local path")
    private String source;

    @Option(names = {"-a", "--agent"}, description = "Target agent(s). Can be specified multiple times.")
    private List<String> agents = new ArrayList<>();

    @Option(names = {"-g", "--global"}, description = "Install globally to ~/.<agent>/skills/ instead of project-level")
    private boolean global;

    @Option(names = {"--skill"}, description = "Install only a specific skill by name. Can be specified multiple times.")
    private List<String> skillNames = new ArrayList<>();

    @Option(names = {"--all"}, description = "Install all skills from the source without prompting")
    private boolean installAll;

    @Option(names = {"-y", "--yes"}, description = "Skip all confirmation prompts")
    private boolean yes;

    @Option(names = {"--copy"}, description = "Copy skill files instead of symlinking (default: symlink)")
    private boolean copy;

    @Option(names = {"--dry-run"}, description = "Show what would be installed without making changes")
    private boolean dryRun;

    @Option(names = {"--dangerously-accept-openclaw-risks"}, description = "Bypass openclaw source warning", hidden = true)
    private boolean dangerouslyAcceptOpenclawRisks;

    @Override
    public Integer call() {
        try {
            return execute();
        } catch (Exception e) {
            Console.error(e.getMessage());
            return 1;
        }
    }

    private int execute() throws Exception {
        // Find the provider
        HostProvider provider = ProviderRegistry.findProvider(source);
        if (provider == null) {
            Console.error("Unsupported source: " + source);
            Console.log("Supported formats:");
            Console.log("  owner/repo           - GitHub shorthand");
            Console.log("  https://github.com/owner/repo");
            Console.log("  https://gitlab.com/owner/repo");
            Console.log("  git@github.com:owner/repo.git");
            Console.log("  ./local-path");
            return 1;
        }

        // Block openclaw sources unless explicitly opted in (upstream #865)
        String normalizedSource = source.toLowerCase();
        if (normalizedSource.contains("openclaw/") || normalizedSource.startsWith("openclaw/")) {
            String sourceOwner = null;
            // Extract owner from owner/repo, github.com/owner/repo, etc.
            if (normalizedSource.contains("github.com/")) {
                String[] parts = normalizedSource.split("github.com/");
                if (parts.length > 1) sourceOwner = parts[1].split("/")[0];
            } else if (!normalizedSource.contains("://") && !normalizedSource.startsWith("git@")) {
                sourceOwner = normalizedSource.split("/")[0];
            }
            if ("openclaw".equals(sourceOwner) && !dangerouslyAcceptOpenclawRisks) {
                Console.error("⚠️  openclaw skills are blocked due to a high number of duplicate and malicious skills.");
                Console.log("If you understand the risks, re-run with " +
                    Console.cyan("--dangerously-accept-openclaw-risks"));
                return 1;
            }
        }

        // Parse source for ref/blob support (upstream #814, #853)
        ParsedSource parsedSource = null;
        try {
            parsedSource = SourceParser.parseSource(source);
        } catch (Exception e) {
            Console.log(Console.dim("Source parsing note: " + e.getMessage()));
        }

        // Preserve SSH URLs in lock files (upstream #588)
        String lockSource = source;
        if (parsedSource != null) {
            String ownerRepo = SourceParser.getOwnerRepo(parsedSource);
            boolean isSSH = parsedSource.getUrl() != null && parsedSource.getUrl().startsWith("git@");
            if (ownerRepo != null && !isSSH) {
                lockSource = ownerRepo;
            }
        }

        // Use spinner for fetching
        Console.log("");
        Console.step("Fetching skills from " + Console.cyan(source) + "...");

        // Try blob-based fast install for GitHub sources (upstream #853)
        BlobInstallResult blobResult = null;
        if (parsedSource != null && "github".equals(parsedSource.getType())) {
            String ownerRepo = SourceParser.getOwnerRepo(parsedSource);
            if (ownerRepo != null) {
                String token = System.getenv("GITHUB_TOKEN");
                // Use --skill filter for blob download if no @skill in source
                String blobSkillFilter = parsedSource.getSkillFilter();
                if (blobSkillFilter == null && skillNames.size() == 1) {
                    blobSkillFilter = skillNames.get(0);
                }
                blobResult = BlobDownloader.tryBlobInstall(ownerRepo, new BlobInstallOptions(
                    parsedSource.getSubpath(),
                    blobSkillFilter,
                    parsedSource.getRef(),
                    token,
                    !skillNames.isEmpty()
                ));
                if (blobResult == null) {
                    Console.log(Console.dim("Blob download unavailable, falling back to clone..."));
                }
            }
        }

        // Fetch skills into a temp directory (fallback if blob failed)
        Path tempDir = null;
        List<Skill> availableSkills;
        if (blobResult != null) {
            availableSkills = blobResult.skills().stream()
                .map(bs -> new Skill(bs.name(), bs.description(), bs.rawContent(), null, false))
                .collect(Collectors.toList());
        } else {
            try {
                tempDir = Files.createTempDirectory("skills-add-");
                availableSkills = provider.fetchSkills(source, tempDir);
            } catch (HostProvider.ProviderException e) {
                Console.error("Failed to fetch skills: " + e.getMessage());
                return 1;
            }
        }

        if (availableSkills.isEmpty()) {
            Console.warn("No skills found in " + source);
            return 0;
        }

        Console.log("Found " + Console.bold(String.valueOf(availableSkills.size())) + " skill(s):");
        for (Skill skill : availableSkills) {
            Console.log("  " + Console.green("•") + " " + Console.bold(skill.getName())
                + " - " + Console.dim(skill.getDescription()));
        }

        // Filter by specific skill names if provided
        List<Skill> skillsToInstall;
        if (!skillNames.isEmpty()) {
            skillsToInstall = availableSkills.stream()
                .filter(s -> skillNames.stream().anyMatch(n ->
                    n.equalsIgnoreCase(s.getName()) || n.equalsIgnoreCase(s.getName().replace("-", " "))))
                .collect(Collectors.toList());
            if (skillsToInstall.isEmpty()) {
                Console.error("None of the requested skills (" + String.join(", ", skillNames) + ") were found.");
                return 1;
            }
        } else if (installAll || yes) {
            skillsToInstall = new ArrayList<>(availableSkills);
        } else {
            // Interactive selection
            skillsToInstall = promptSkillSelection(availableSkills);
            if (skillsToInstall == null) {
                Console.log("Installation cancelled.");
                return 0;
            }
        }

        // Determine target agents
        List<AgentConfig> targetAgents = resolveAgents();
        if (targetAgents.isEmpty()) {
            Console.error("No agents selected for installation.");
            return 1;
        }

        // Prompt for scope if not explicitly set (matches upstream)
        if (!global && !yes && !installAll && System.console() != null) {
            boolean supportsGlobal = targetAgents.stream()
                .anyMatch(a -> a.getGlobalSkillsDir() != null);
            if (supportsGlobal) {
                Boolean scope = Prompts.select("Installation scope", List.of(
                    new SelectOption<>(false, "Project", "Install in current directory (committed with your project)"),
                    new SelectOption<>(true, "Global", "Install in home directory (available across all projects)")
                ));
                if (scope == null) {
                    Console.log("Installation cancelled.");
                    return 0;
                }
                global = scope;
            }
        }

        if (dryRun) {
            Console.log("\n" + Console.yellow("[DRY RUN]") + " Would install:");
            for (Skill skill : skillsToInstall) {
                for (AgentConfig agent : targetAgents) {
                    String dir = global ? agent.getGlobalSkillsDir() : agent.getSkillsDir();
                    Console.log("  " + skill.getName() + " → " + dir + "/" + skill.getName() + "/SKILL.md");
                }
            }
            return 0;
        }

        // Install each skill to each agent
        int installed = 0;
        SkillLock globalLock = new SkillLock();
        LocalLock localLock = new LocalLock(Paths.get(System.getProperty("user.dir")));
        String latestHash = null;
        try {
            latestHash = provider.getLatestHash(source, null);
        } catch (Exception ignored) {}

        String ref = parsedSource != null ? parsedSource.getRef() : null;

        for (Skill skill : skillsToInstall) {
            for (AgentConfig agent : targetAgents) {
                try {
                    Path targetDir = resolveTargetDir(agent);
                    Path skillTargetDir = targetDir.resolve(PathUtils.sanitizeName(skill.getName()));

                    // Install skill — blob or disk-based
                    BlobSkill blobSkill = findBlobSkill(blobResult, skill.getName());
                    if (blobSkill != null) {
                        installBlobSkill(blobSkill, skillTargetDir);
                    } else {
                        installSkill(skill, skillTargetDir, tempDir);
                    }

                    // Determine hash — prefer blob snapshot hash
                    String skillHash = latestHash;
                    String skillPath = skill.getFilePath() != null ? skill.getFilePath().toString() : null;
                    if (blobSkill != null) {
                        skillHash = blobSkill.snapshotHash();
                        skillPath = blobSkill.repoPath();
                        // Try to get folder hash from tree
                        if (blobResult != null && skillPath != null) {
                            String folderHash = BlobDownloader.getSkillFolderHashFromTree(
                                blobResult.tree(), skillPath);
                            if (folderHash != null) skillHash = folderHash;
                        }
                    }

                    // Update lock files
                    SkillLockEntry entry = new SkillLockEntry(
                        lockSource, provider.getSourceType(),
                        provider.canonicalUrl(source),
                        ref,
                        skillPath,
                        skillHash
                    );
                    if (skill.getPluginName() != null) {
                        entry.setPluginName(skill.getPluginName());
                    }
                    if (global) {
                        globalLock.write(agent.getName(), skill.getName(), entry);
                    } else {
                        localLock.write(agent.getName(), skill.getName(), entry);
                    }

                    Console.success("Installed " + Console.bold(skill.getName())
                        + " → " + Console.dim(agent.getDisplayName()));
                    installed++;
                } catch (Exception e) {
                    Console.error("Failed to install " + skill.getName() + " to "
                        + agent.getDisplayName() + ": " + e.getMessage());
                }
            }
        }

        Console.log("\n" + Console.green("✓") + " Installed " + installed + " skill(s).");
        Telemetry.track("add", source);
        return 0;
    }

    /**
     * Install a blob-downloaded skill by writing snapshot files to disk (upstream #853).
     */
    private void installBlobSkill(BlobSkill blobSkill, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        for (SkillSnapshotFile file : blobSkill.files()) {
            Path filePath = targetDir.resolve(file.path());
            // Safety: ensure the file stays within targetDir
            if (!PathUtils.isSubpathSafe(targetDir, filePath)) continue;
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, file.contents());
        }
    }

    /**
     * Find a BlobSkill by name in the blob result.
     */
    private BlobSkill findBlobSkill(BlobInstallResult blobResult, String skillName) {
        if (blobResult == null) return null;
        return blobResult.skills().stream()
            .filter(bs -> bs.name().equalsIgnoreCase(skillName))
            .findFirst()
            .orElse(null);
    }

    private void installSkill(Skill skill, Path targetDir, Path tempDir) throws IOException {
        Files.createDirectories(targetDir);
        Path sourceFile = skill.getFilePath();
        Path targetFile = targetDir.resolve("SKILL.md");

        if (copy || sourceFile == null) {
            // Write the skill content directly
            Files.writeString(targetFile, formatSkillContent(skill));
        } else {
            // Symlink to the source file (if available)
            try {
                if (Files.exists(targetFile)) Files.delete(targetFile);
                Files.createSymbolicLink(targetFile, sourceFile);
            } catch (UnsupportedOperationException | IOException e) {
                // Fall back to copy if symlinks not supported (e.g. Windows)
                Files.writeString(targetFile, formatSkillContent(skill));
            }
        }
    }

    private String formatSkillContent(Skill skill) {
        StringBuilder sb = new StringBuilder("---\n");
        sb.append("name: ").append(skill.getName()).append("\n");
        sb.append("description: ").append(skill.getDescription()).append("\n");
        sb.append("---\n");
        if (skill.getContent() != null && !skill.getContent().isEmpty()) {
            sb.append(skill.getContent());
        }
        return sb.toString();
    }

    private Path resolveTargetDir(AgentConfig agent) {
        String home = System.getProperty("user.home");
        if (global) {
            return Paths.get(home, agent.getGlobalSkillsDir() != null
                ? agent.getGlobalSkillsDir() : agent.getSkillsDir());
        }
        return Paths.get(System.getProperty("user.dir"), agent.getSkillsDir());
    }

    private List<AgentConfig> resolveAgents() {
        if (!agents.isEmpty()) {
            List<AgentConfig> result = new ArrayList<>();
            for (String agentName : agents) {
                AgentRegistry.findByName(agentName).ifPresentOrElse(
                    result::add,
                    () -> Console.warn("Unknown agent: " + agentName + " (skipping)")
                );
            }
            return result;
        }

        // Auto-detect installed agents
        List<AgentConfig> installed = AgentRegistry.getInstalledAgents();
        if (!installed.isEmpty()) {
            if (yes || installAll) {
                return installed;
            }
            return promptAgentSelection(installed);
        }

        // No agents detected; prompt from all
        if (yes || installAll) {
            return AgentRegistry.getAgents();
        }
        return promptAgentSelection(AgentRegistry.getAgents());
    }

    private List<Skill> promptSkillSelection(List<Skill> skills) {
        List<SelectOption<Skill>> options = new ArrayList<>();
        for (Skill skill : skills) {
            String hint = skill.getDescription();
            if (hint != null && hint.length() > 60) hint = hint.substring(0, 57) + "...";
            options.add(new SelectOption<>(skill, skill.getName(), hint));
        }

        // Pre-select all
        Set<Integer> preSelected = new HashSet<>();
        for (int i = 0; i < options.size(); i++) preSelected.add(i);

        List<Skill> selected = Prompts.multiselect("Select skills to install", options, preSelected);
        return selected; // null if cancelled
    }

    private List<AgentConfig> promptAgentSelection(List<AgentConfig> agents) {
        List<SelectOption<AgentConfig>> options = new ArrayList<>();
        for (AgentConfig agent : agents) {
            options.add(new SelectOption<>(agent, agent.getDisplayName()));
        }

        // Pre-select all
        Set<Integer> preSelected = new HashSet<>();
        for (int i = 0; i < options.size(); i++) preSelected.add(i);

        List<AgentConfig> selected = Prompts.multiselect("Select agents to install to", options, preSelected);
        return selected != null ? selected : List.of();
    }
}
