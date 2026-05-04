package sh.skills.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import sh.skills.agents.AgentRegistry;
import sh.skills.lock.LocalLock;
import sh.skills.lock.SkillLock;
import sh.skills.model.AgentConfig;
import sh.skills.model.Skill;
import sh.skills.model.SkillLockEntry;
import sh.skills.providers.SkillDiscovery;
import sh.skills.util.Console;
import sh.skills.util.FrontmatterParser;
import sh.skills.util.Sanitize;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * Implements `skills list` (alias: `skills ls`).
 * Lists all installed skills for each agent.
 * Mirrors src/list.ts from the TypeScript source.
 */
@Command(
    name = "list",
    aliases = {"ls"},
    description = "List installed skills for all agents",
    mixinStandardHelpOptions = true
)
public class ListCommand implements Callable<Integer> {

    @Option(names = {"-g", "--global"}, description = "List globally installed skills instead of project-level")
    private boolean global;

    @Option(names = {"--json"}, description = "Output as JSON")
    private boolean json;

    @Option(names = {"-a", "--agent"}, description = "Filter to a specific agent")
    private String agentFilter;

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
        List<AgentConfig> agents = AgentRegistry.getAgents();
        if (agentFilter != null) {
            agents = new ArrayList<>();
            AgentRegistry.findByName(agentFilter).ifPresent(agents::add);
            if (agents.isEmpty()) {
                Console.error("Unknown agent: " + agentFilter);
                return 1;
            }
        }

        Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();
        String home = System.getProperty("user.home");
        String projectDir = System.getProperty("user.dir");

        // Track which directories we've already scanned
        Set<Path> scannedDirs = new HashSet<>();

        for (AgentConfig agent : agents) {
            Path skillsDir;
            if (global) {
                skillsDir = agent.resolveGlobalSkillsPath();
            } else {
                skillsDir = agent.resolveProjectSkillsPath();
            }

            scannedDirs.add(skillsDir);
            if (!Files.isDirectory(skillsDir)) continue;

            List<Skill> skills = discoverInstalledSkills(skillsDir);
            if (skills.isEmpty()) continue;

            List<Map<String, String>> skillList = new ArrayList<>();
            for (Skill skill : skills) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("name", skill.getName());
                entry.put("description", skill.getDescription());
                entry.put("path", skill.getFilePath() != null
                    ? skill.getFilePath().getParent().toString()
                    : skillsDir.resolve(skill.getName()).toString());
                skillList.add(entry);
            }
            result.put(agent.getName(), skillList);
        }

        // Also scan skill directories for agents NOT in the check list (upstream #656)
        // In case skills were installed with --agent but the agent is no longer detected
        if (agentFilter == null) {
            for (AgentConfig agent : AgentRegistry.getAgents()) {
                Path skillsDir;
                if (global) {
                    skillsDir = agent.resolveGlobalSkillsPath();
                } else {
                    skillsDir = agent.resolveProjectSkillsPath();
                }

                if (scannedDirs.contains(skillsDir) || !Files.isDirectory(skillsDir)) continue;
                scannedDirs.add(skillsDir);

                List<Skill> skills = discoverInstalledSkills(skillsDir);
                if (skills.isEmpty()) continue;

                List<Map<String, String>> skillList = new ArrayList<>();
                for (Skill skill : skills) {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("name", skill.getName());
                    entry.put("description", skill.getDescription());
                    entry.put("path", skill.getFilePath() != null
                        ? skill.getFilePath().getParent().toString()
                        : skillsDir.resolve(skill.getName()).toString());
                    skillList.add(entry);
                }
                result.put(agent.getName(), skillList);
            }
        }

        if (result.isEmpty()) {
            String scopeLabel = global ? "global" : "project";
            Console.log(Console.dim("No " + scopeLabel + " skills found."));
            if (global) {
                Console.log(Console.dim("Try listing project skills without -g"));
            } else {
                Console.log(Console.dim("Try listing global skills with -g"));
            }
            return 0;
        }

        // Group by skill name (upstream format): deduplicate across agents
        // Skills with the same name are merged into one entry with all agents listed
        Map<String, SkillInfo> bySkill = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, String>>> entry : result.entrySet()) {
            String agentName = entry.getKey();
            AgentConfig agent = AgentRegistry.findByName(agentName).orElse(null);
            String displayName = agent != null ? agent.getDisplayName() : agentName;

            for (Map<String, String> skill : entry.getValue()) {
                String name = skill.get("name");
                String desc = skill.get("description");
                String path = skill.get("path");
                SkillInfo info = bySkill.computeIfAbsent(name,
                    k -> new SkillInfo(name, desc, path));
                if (!info.agents.contains(displayName)) {
                    info.agents.add(displayName);
                }
            }
        }

        // JSON output: flat array matching upstream schema
        if (json) {
            String scopeKey = global ? "global" : "project";
            List<Map<String, Object>> jsonList = new ArrayList<>();
            for (SkillInfo info : bySkill.values()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", info.name);
                entry.put("path", info.path);
                entry.put("scope", scopeKey);
                entry.put("agents", info.agents);
                jsonList.add(entry);
            }
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            System.out.println(mapper.writeValueAsString(jsonList));
            return 0;
        }

        // Read lock entries for plugin grouping
        Map<String, SkillLockEntry> lockEntries = new HashMap<>();
        try {
            if (global) {
                lockEntries.putAll(new SkillLock().readAll());
            } else {
                lockEntries.putAll(new LocalLock(Paths.get(projectDir)).readAll());
            }
        } catch (Exception ignored) {}

        // Resolve pluginName for each skill from lock entries
        Map<String, String> skillPluginNames = new HashMap<>();
        for (Map.Entry<String, SkillLockEntry> le : lockEntries.entrySet()) {
            String key = le.getKey(); // format: "agent:skillName"
            SkillLockEntry entry = le.getValue();
            if (entry.getPluginName() != null) {
                int colon = key.indexOf(':');
                String skillName = colon >= 0 ? key.substring(colon + 1) : key;
                skillPluginNames.put(skillName, entry.getPluginName());
            }
        }

        // Group skills by plugin
        Map<String, List<SkillInfo>> groupedSkills = new LinkedHashMap<>();
        List<SkillInfo> ungroupedSkills = new ArrayList<>();
        for (SkillInfo info : bySkill.values()) {
            String plugin = skillPluginNames.get(info.name);
            if (plugin != null) {
                groupedSkills.computeIfAbsent(plugin, k -> new ArrayList<>()).add(info);
            } else {
                ungroupedSkills.add(info);
            }
        }

        boolean hasGroups = !groupedSkills.isEmpty();
        String scopeLabel = global ? "Global" : "Project";
        Console.log(Console.bold(scopeLabel + " Skills"));
        Console.log("");

        if (hasGroups) {
            // Print groups sorted alphabetically
            List<String> sortedGroups = new ArrayList<>(groupedSkills.keySet());
            Collections.sort(sortedGroups);
            for (String group : sortedGroups) {
                Console.log(Console.bold(kebabToTitle(group)));
                for (SkillInfo info : groupedSkills.get(group)) {
                    printSkillInfo(info, true);
                }
                Console.log("");
            }
            // Ungrouped under "General"
            if (!ungroupedSkills.isEmpty()) {
                Console.log(Console.bold("General"));
                for (SkillInfo info : ungroupedSkills) {
                    printSkillInfo(info, true);
                }
                Console.log("");
            }
        } else {
            // No groups, flat list
            for (SkillInfo info : bySkill.values()) {
                printSkillInfo(info, false);
            }
            Console.log("");
        }
        return 0;
    }

    /** Print a single skill entry */
    private void printSkillInfo(SkillInfo info, boolean indent) {
        String prefix = indent ? "  " : "";
        String shortPath = shortenPath(info.path);
        Console.log(prefix + Console.cyan(Sanitize.sanitizeMetadata(info.name)) + " " + Console.dim(shortPath));
        if (info.description != null && !info.description.isEmpty()) {
            Console.log(prefix + "  " + Console.dim(Sanitize.sanitizeMetadata(info.description)));
        }
        String agentList = info.agents.size() <= 5
            ? String.join(", ", info.agents)
            : String.join(", ", info.agents.subList(0, 5))
                + " +" + (info.agents.size() - 5) + " more";
        Console.log(prefix + "  " + Console.dim("Agents:") + " " + agentList);
    }

    /** Convert kebab-case to Title Case: "my-awesome-plugin" → "My Awesome Plugin" */
    static String kebabToTitle(String kebab) {
        if (kebab == null || kebab.isEmpty()) return kebab;
        String[] words = kebab.split("-");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            if (!words[i].isEmpty()) {
                sb.append(Character.toUpperCase(words[i].charAt(0)));
                sb.append(words[i].substring(1));
            }
        }
        return sb.toString();
    }

    /** Shorten path for display: replace home with ~, cwd with . */
    private String shortenPath(String fullPath) {
        if (fullPath == null) return "";
        // Normalize separators for consistent output across platforms
        fullPath = fullPath.replace('\\', '/');
        String home = System.getProperty("user.home").replace('\\', '/');
        String cwd = System.getProperty("user.dir").replace('\\', '/');
        if (fullPath.startsWith(cwd)) {
            return "." + fullPath.substring(cwd.length());
        }
        if (fullPath.startsWith(home)) {
            return "~" + fullPath.substring(home.length());
        }
        return fullPath;
    }

    private static class SkillInfo {
        final String name;
        final String description;
        final String path;
        final List<String> agents = new ArrayList<>();

        SkillInfo(String name, String description, String path) {
            this.name = name;
            this.description = description;
            this.path = path;
        }
    }

    private List<Skill> discoverInstalledSkills(Path skillsDir) {
        List<Skill> skills = new ArrayList<>();
        if (!Files.isDirectory(skillsDir)) return skills;

        try (Stream<Path> entries = Files.list(skillsDir)) {
            entries.filter(Files::isDirectory).forEach(subdir -> {
                Path skillFile = subdir.resolve("SKILL.md");
                if (Files.exists(skillFile)) {
                    try {
                        String content = Files.readString(skillFile);
                        Skill skill = FrontmatterParser.parse(content, skillFile);
                        if (skill != null) skills.add(skill);
                    } catch (IOException ignored) {}
                }
            });
        } catch (IOException ignored) {}

        // Also check for SKILL.md directly in the skills dir
        Path rootSkillFile = skillsDir.resolve("SKILL.md");
        if (Files.exists(rootSkillFile)) {
            try {
                String content = Files.readString(rootSkillFile);
                Skill skill = FrontmatterParser.parse(content, rootSkillFile);
                if (skill != null) skills.add(skill);
            } catch (IOException ignored) {}
        }

        skills.sort(Comparator.comparing(Skill::getName));
        return skills;
    }
}
