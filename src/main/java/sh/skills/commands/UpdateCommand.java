package sh.skills.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import sh.skills.agents.AgentRegistry;
import sh.skills.lock.LocalLock;
import sh.skills.lock.SkillLock;
import sh.skills.model.AgentConfig;
import sh.skills.model.Skill;
import sh.skills.model.SkillLockEntry;
import sh.skills.providers.HostProvider;
import sh.skills.providers.ProviderRegistry;
import sh.skills.tui.Prompts;
import sh.skills.tui.Prompts.SelectOption;
import sh.skills.util.Console;
import sh.skills.util.PathUtils;
import sh.skills.util.UpdateSource;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Implements `skills update` (aliases: upgrade, check).
 * Updates installed skills to their latest versions.
 * Supports project-level, global, or both scopes.
 * Mirrors the updated cli.ts update command from the TypeScript source (upstream #913).
 */
@Command(
    name = "update",
    aliases = {"upgrade"},
    description = "Update installed skills to latest versions",
    mixinStandardHelpOptions = true
)
public class UpdateCommand implements Callable<Integer> {

    @Parameters(arity = "0..*", description = "Skill name(s) to update (default: all)")
    private List<String> skillNames = new ArrayList<>();

    @Option(names = {"-g", "--global"}, description = "Update global skills only")
    private boolean global;

    @Option(names = {"-p", "--project"}, description = "Update project skills only")
    private boolean project;

    @Option(names = {"-y", "--yes"}, description = "Skip scope prompt (auto-detect)")
    private boolean yes;

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
        String scope = resolveScope();

        if (!skillNames.isEmpty()) {
            Console.log("Updating " + String.join(", ", skillNames) + "...");
        } else {
            Console.log("Checking for skill updates...");
        }
        System.out.println();

        int totalSuccess = 0;
        int totalFail = 0;
        int totalFound = 0;

        // ── Global update ──
        if ("global".equals(scope) || "both".equals(scope)) {
            if ("both".equals(scope) && skillNames.isEmpty()) {
                Console.log(Console.bold("Global Skills"));
            }
            UpdateResult globalResult = updateGlobalSkills();
            totalSuccess += globalResult.successCount;
            totalFail += globalResult.failCount;
            totalFound += globalResult.checkedCount;
            if ("both".equals(scope) && skillNames.isEmpty()) {
                System.out.println();
            }
        }

        // ── Project update ──
        if ("project".equals(scope) || "both".equals(scope)) {
            if ("both".equals(scope) && skillNames.isEmpty()) {
                Console.log(Console.bold("Project Skills"));
            }
            UpdateResult projectResult = updateProjectSkills();
            totalSuccess += projectResult.successCount;
            totalFail += projectResult.failCount;
            totalFound += projectResult.checkedCount;
        }

        // If filtering by name and nothing found
        if (!skillNames.isEmpty() && totalFound == 0) {
            Console.log(Console.dim("No installed skills found matching: " + String.join(", ", skillNames)));
        }

        System.out.println();
        if (totalSuccess > 0) {
            Console.log(Console.green("✓") + " Updated " + totalSuccess + " skill(s)");
        }
        if (totalFail > 0) {
            Console.log(Console.dim("Failed to update " + totalFail + " skill(s)"));
        }

        return totalFail > 0 ? 1 : 0;
    }

    // ── Scope resolution ──

    private String resolveScope() {
        // When targeting specific skills, search both scopes unless constrained
        if (!skillNames.isEmpty()) {
            if (global) return "global";
            if (project) return "project";
            return "both";
        }

        // Explicit flags
        if (global && project) return "both";
        if (global) return "global";
        if (project) return "project";

        // Auto-detection (with -y or non-interactive)
        if (yes) {
            return hasProjectSkills() ? "project" : "global";
        }

        // Interactive prompt
        return promptScope();
    }

    private boolean hasProjectSkills() {
        Path cwd = Paths.get(System.getProperty("user.dir"));
        // Check skills-lock.json
        if (Files.exists(cwd.resolve("skills-lock.json"))) return true;
        // Check .agents/skills/ for subdirectories with SKILL.md
        Path skillsDir = cwd.resolve(".agents").resolve("skills");
        if (Files.isDirectory(skillsDir)) {
            try (var entries = Files.list(skillsDir)) {
                return entries.filter(Files::isDirectory)
                    .anyMatch(d -> Files.exists(d.resolve("SKILL.md")));
            } catch (Exception ignored) {}
        }
        return false;
    }

    private String promptScope() {
        String result = Prompts.select("Update scope", List.of(
            new SelectOption<>("project", "Project", "Update skills in current directory"),
            new SelectOption<>("global", "Global", "Update skills in home directory"),
            new SelectOption<>("both", "Both", "Update all skills")
        ));
        return result != null ? result : "project";
    }

    // ── Global skills update ──

    private UpdateResult updateGlobalSkills() throws Exception {
        SkillLock globalLock = new SkillLock();
        Map<String, SkillLockEntry> entries = globalLock.readAll();
        int successCount = 0;
        int failCount = 0;

        if (entries.isEmpty()) {
            if (skillNames.isEmpty()) {
                Console.log(Console.dim("No global skills tracked in lock file."));
                Console.log(Console.dim("Install skills with ") + Console.cyan("skills add <package> -g"));
            }
            return new UpdateResult(0, 0, 0);
        }

        // Separate checkable from skipped
        List<SkillEntry> checkable = new ArrayList<>();
        List<SkippedSkill> skipped = new ArrayList<>();

        for (Map.Entry<String, SkillLockEntry> entry : entries.entrySet()) {
            String key = entry.getKey();
            String skillName = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
            SkillLockEntry lock = entry.getValue();

            if (!matchesFilter(skillName)) continue;

            if (lock.getSkillFolderHash() == null || lock.getSkillPath() == null) {
                skipped.add(new SkippedSkill(skillName, getSkipReason(lock),
                    lock.getSourceUrl(), lock.getRef()));
                continue;
            }

            checkable.add(new SkillEntry(key, skillName, lock));
        }

        // Check for updates
        List<SkillEntry> updates = new ArrayList<>();
        for (int i = 0; i < checkable.size(); i++) {
            SkillEntry se = checkable.get(i);
            Console.printInline("\r" + Console.dim("Checking global skill " + (i + 1) + "/"
                + checkable.size() + ": " + se.name));

            try {
                String fetchSource = se.lock.getRef() != null
                    ? se.lock.getSource() + "#" + se.lock.getRef()
                    : se.lock.getSource();

                HostProvider provider = ProviderRegistry.findProvider(fetchSource);
                if (provider == null) continue;

                String latestHash = provider.getLatestHash(fetchSource, se.lock.getSkillPath());
                if (latestHash != null && !latestHash.equals(se.lock.getSkillFolderHash())) {
                    updates.add(se);
                }
            } catch (Exception ignored) {}
        }

        if (!checkable.isEmpty()) {
            Console.printInline("\r\033[K"); // Clear progress line
        }

        int checkedCount = checkable.size() + skipped.size();

        if (checkable.isEmpty() && skipped.isEmpty()) {
            if (skillNames.isEmpty()) {
                Console.log(Console.dim("No global skills to check."));
            }
            return new UpdateResult(0, 0, 0);
        }

        if (updates.isEmpty() && !checkable.isEmpty()) {
            Console.log(Console.green("✓") + " All global skills are up to date");
            printSkippedSkills(skipped);
            return new UpdateResult(0, 0, checkedCount);
        }

        if (!updates.isEmpty()) {
            Console.log("Found " + updates.size() + " global update(s)");
            System.out.println();

            for (SkillEntry update : updates) {
                Console.log("Updating " + update.name + "...");
                String installSource = UpdateSource.buildUpdateInstallSource(
                    update.lock.getSource(),
                    update.lock.getSourceUrl(),
                    update.lock.getRef(),
                    update.lock.getSkillPath()
                );

                try {
                    performUpdate(installSource, update.name, true);
                    successCount++;
                    Console.log("  " + Console.green("✓") + " Updated " + update.name);
                } catch (Exception e) {
                    failCount++;
                    Console.log("  " + Console.dim("✗ Failed to update " + update.name));
                }
            }
        }

        printSkippedSkills(skipped);
        return new UpdateResult(successCount, failCount, checkedCount);
    }

    // ── Project skills update ──

    private UpdateResult updateProjectSkills() throws Exception {
        LocalLock localLock = new LocalLock(Paths.get(System.getProperty("user.dir")));
        Map<String, SkillLockEntry> entries = localLock.readAll();
        int successCount = 0;
        int failCount = 0;

        // Filter to remote skills only (skip local and node_modules)
        List<Map.Entry<String, SkillLockEntry>> projectSkills = entries.entrySet().stream()
            .filter(e -> {
                String sourceType = e.getValue().getSourceType();
                return !"local".equals(sourceType) && !"node_modules".equals(sourceType);
            })
            .filter(e -> {
                String name = e.getKey().contains(":")
                    ? e.getKey().substring(e.getKey().indexOf(':') + 1)
                    : e.getKey();
                return matchesFilter(name);
            })
            .toList();

        if (projectSkills.isEmpty()) {
            if (skillNames.isEmpty()) {
                Console.log(Console.dim("No project skills to update."));
                Console.log(Console.dim("Install project skills with ") + Console.cyan("skills add <package>"));
            }
            return new UpdateResult(0, 0, 0);
        }

        Console.log("Refreshing " + projectSkills.size() + " project skill(s)...");
        System.out.println();

        for (Map.Entry<String, SkillLockEntry> entry : projectSkills) {
            String key = entry.getKey();
            String skillName = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
            SkillLockEntry lock = entry.getValue();

            Console.log("Updating " + skillName + "...");
            String installSource = UpdateSource.buildLocalUpdateSource(
                lock.getSource(), lock.getRef());

            try {
                performUpdate(installSource, skillName, false);
                successCount++;
                Console.log("  " + Console.green("✓") + " Updated " + skillName);
            } catch (Exception e) {
                failCount++;
                Console.log("  " + Console.dim("✗ Failed to update " + skillName));
            }
        }

        return new UpdateResult(successCount, failCount, projectSkills.size());
    }

    // ── Common helpers ──

    /**
     * Perform a skill update by re-fetching from the source and writing files.
     */
    private void performUpdate(String source, String skillName, boolean isGlobal) throws Exception {
        HostProvider provider = ProviderRegistry.findProvider(source);
        if (provider == null) {
            throw new Exception("No provider found for source: " + source);
        }

        Path tempDir = Files.createTempDirectory("skills-update-");
        try {
            List<Skill> skills = provider.fetchSkills(source, tempDir);
            Skill matchedSkill = skills.stream()
                .filter(s -> s.getName().equalsIgnoreCase(skillName))
                .findFirst()
                .orElse(null);

            if (matchedSkill == null) {
                throw new Exception("Skill '" + skillName + "' not found in source");
            }

            String home = System.getProperty("user.home");
            String projectDir = System.getProperty("user.dir");

            // Install to all agents that have the skill
            for (AgentConfig agent : AgentRegistry.getAgents()) {
                Path skillsDir = isGlobal
                    ? Paths.get(home, agent.getGlobalSkillsDir() != null
                        ? agent.getGlobalSkillsDir() : agent.getSkillsDir())
                    : Paths.get(projectDir, agent.getSkillsDir());

                Path skillDir = skillsDir.resolve(PathUtils.sanitizeName(skillName));
                if (!Files.isDirectory(skillDir)) continue; // Only update where already installed

                Path skillFile = skillDir.resolve("SKILL.md");
                String content = "---\nname: " + matchedSkill.getName()
                    + "\ndescription: " + matchedSkill.getDescription()
                    + "\n---\n" + (matchedSkill.getContent() != null ? matchedSkill.getContent() : "");
                Files.writeString(skillFile, content);
            }

            // Update lock entry hash
            SkillLock globalLock = new SkillLock();
            LocalLock localLock = new LocalLock(Paths.get(projectDir));

            String latestHash = null;
            try {
                latestHash = provider.getLatestHash(source, null);
            } catch (Exception ignored) {}

            // Update entries that match this skill name
            Map<String, SkillLockEntry> lockEntries = isGlobal ? globalLock.readAll() : localLock.readAll();
            for (Map.Entry<String, SkillLockEntry> entry : lockEntries.entrySet()) {
                String entrySkillName = entry.getKey().contains(":")
                    ? entry.getKey().substring(entry.getKey().indexOf(':') + 1)
                    : entry.getKey();
                if (entrySkillName.equalsIgnoreCase(skillName)) {
                    SkillLockEntry lock = entry.getValue();
                    lock.setUpdatedAt(java.time.Instant.now().toString());
                    if (latestHash != null) lock.setSkillFolderHash(latestHash);
                    String agentName = entry.getKey().contains(":")
                        ? entry.getKey().substring(0, entry.getKey().indexOf(':'))
                        : entry.getKey();
                    if (isGlobal) {
                        globalLock.write(agentName, skillName, lock);
                    } else {
                        localLock.write(agentName, skillName, lock);
                    }
                }
            }
        } finally {
            sh.skills.util.GitUtils.deleteTempDir(tempDir.toFile());
        }
    }

    private boolean matchesFilter(String name) {
        if (skillNames.isEmpty()) return true;
        return skillNames.stream().anyMatch(f -> f.equalsIgnoreCase(name));
    }

    private static String getSkipReason(SkillLockEntry entry) {
        if ("local".equals(entry.getSourceType())) return "Local path";
        if ("git".equals(entry.getSourceType())) return "Git URL";
        if ("well-known".equals(entry.getSourceType())) return "Well-known skill";
        if (entry.getSkillFolderHash() == null) return "Private or deleted repo";
        if (entry.getSkillPath() == null) return "No skill path recorded";
        return "No version tracking";
    }

    private static void printSkippedSkills(List<SkippedSkill> skipped) {
        if (skipped.isEmpty()) return;
        System.out.println();
        Console.log(Console.dim(skipped.size() + " skill(s) cannot be checked automatically:"));
        for (SkippedSkill skill : skipped) {
            Console.log("  • " + skill.name + " " + Console.dim("(" + skill.reason + ")"));
            String updateSource = UpdateSource.formatSourceInput(
                skill.sourceUrl != null ? skill.sourceUrl : "", skill.ref);
            Console.log("    " + Console.dim("To update: ") + Console.cyan("skills add " + updateSource + " -g -y"));
        }
    }

    // ── Internal types ──

    private record UpdateResult(int successCount, int failCount, int checkedCount) {}
    private record SkillEntry(String key, String name, SkillLockEntry lock) {}
    private record SkippedSkill(String name, String reason, String sourceUrl, String ref) {}
}
