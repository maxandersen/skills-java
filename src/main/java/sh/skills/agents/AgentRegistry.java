package sh.skills.agents;

import sh.skills.model.AgentConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Registry of all supported coding agents.
 * Mirrors src/agents.ts from the TypeScript source.
 * Includes all 40+ agents with their skills directory paths.
 */
public class AgentRegistry {

    // All agents synced with upstream agents.ts (v1.5.1, commit 7c0a9af3f8)
    // Format: name, displayName, skillsDir (project), globalSkillsDir, detectPath
    private static final List<AgentConfig> AGENTS = Collections.unmodifiableList(Arrays.asList(
        new AgentConfig("claude-code", "Claude Code",
            ".claude/skills", ".claude/skills", ".claude"),
        new AgentConfig("cursor", "Cursor",
            ".agents/skills", ".cursor/skills", ".cursor"),
        new AgentConfig("opencode", "OpenCode",
            ".agents/skills", ".opencode/skills", ".opencode"),
        new AgentConfig("cline", "Cline",
            ".agents/skills", ".agents/skills", ".cline"),
        new AgentConfig("codex", "Codex",
            ".agents/skills", ".codex/skills", ".codex"),
        new AgentConfig("codebuddy", "CodeBuddy",
            ".codebuddy/skills", ".codebuddy/skills", ".codebuddy"),
        new AgentConfig("command-code", "Command Code",
            ".commandcode/skills", ".commandcode/skills", ".commandcode"),
        new AgentConfig("continue", "Continue",
            ".continue/skills", ".continue/skills", ".continue"),
        new AgentConfig("cortex", "Cortex Code",
            ".cortex/skills", ".snowflake/cortex/skills", ".cortex"),
        new AgentConfig("crush", "Crush",
            ".crush/skills", ".config/crush/skills", ".crush"),
        new AgentConfig("droid", "Droid",
            ".factory/skills", ".factory/skills", ".factory"),
        new AgentConfig("gemini-cli", "Gemini CLI",
            ".agents/skills", ".gemini/skills", ".gemini"),
        new AgentConfig("github-copilot", "GitHub Copilot",
            ".agents/skills", ".copilot/skills", ".copilot"),
        new AgentConfig("goose", "Goose",
            ".goose/skills", ".goose/skills", ".goose"),
        new AgentConfig("iflow-cli", "iFlow CLI",
            ".iflow/skills", ".iflow/skills", ".iflow"),
        new AgentConfig("kimi-cli", "Kimi Code CLI",
            ".agents/skills", ".config/agents/skills", ".kimi"),
        new AgentConfig("kiro-cli", "Kiro CLI",
            ".kiro/skills", ".kiro/skills", ".kiro"),
        new AgentConfig("kode", "Kode",
            ".kode/skills", ".kode/skills", ".kode"),
        new AgentConfig("mcpjam", "MCPJam",
            ".mcpjam/skills", ".mcpjam/skills", ".mcpjam"),
        new AgentConfig("mistral-vibe", "Mistral Vibe",
            ".vibe/skills", ".vibe/skills", ".vibe"),
        new AgentConfig("mux", "Mux",
            ".mux/skills", ".mux/skills", ".mux"),
        new AgentConfig("neovate", "Neovate",
            ".neovate/skills", ".neovate/skills", ".neovate"),
        new AgentConfig("openclaw", "OpenClaw",
            "skills", ".openclaw/skills", ".openclaw"),
        new AgentConfig("openhands", "OpenHands",
            ".openhands/skills", ".openhands/skills", ".openhands"),
        new AgentConfig("pi", "Pi",
            ".pi/skills", ".pi/agent/skills", ".pi"),
        new AgentConfig("qoder", "Qoder",
            ".qoder/skills", ".qoder/skills", ".qoder"),
        new AgentConfig("qwen-code", "Qwen Code",
            ".qwen/skills", ".qwen/skills", ".qwen"),
        new AgentConfig("replit", "Replit",
            ".agents/skills", ".replit/skills", ".replit"),
        new AgentConfig("roo", "Roo Code",
            ".roo/skills", ".roo/skills", ".roo"),
        new AgentConfig("trae", "Trae",
            ".trae/skills", ".trae/skills", ".trae"),
        new AgentConfig("trae-cn", "Trae CN",
            ".trae/skills", ".trae-cn/skills", ".trae-cn"),
        new AgentConfig("windsurf", "Windsurf",
            ".windsurf/skills", ".codeium/windsurf/skills", ".windsurf"),
        new AgentConfig("zencoder", "Zencoder",
            ".zencoder/skills", ".zencoder/skills", ".zencoder"),
        new AgentConfig("pochi", "Pochi",
            ".pochi/skills", ".pochi/skills", ".pochi"),
        new AgentConfig("adal", "AdaL",
            ".adal/skills", ".adal/skills", ".adal"),
        new AgentConfig("amp", "Amp",
            ".agents/skills", ".amp/skills", ".amp"),
        new AgentConfig("antigravity", "Antigravity",
            ".agents/skills", ".gemini/antigravity/skills", ".gemini/antigravity"),
        new AgentConfig("augment", "Augment",
            ".augment/skills", ".augment/skills", ".augment"),
        new AgentConfig("bob", "IBM Bob",
            ".bob/skills", ".bob/skills", ".bob"),
        new AgentConfig("deepagents", "Deep Agents",
            ".agents/skills", ".deepagents/agent/skills", ".deepagents"),
        new AgentConfig("firebender", "Firebender",
            ".agents/skills", ".firebender/skills", ".firebender"),
        new AgentConfig("junie", "Junie",
            ".junie/skills", ".junie/skills", ".junie"),
        new AgentConfig("kilo", "Kilo Code",
            ".kilocode/skills", ".kilocode/skills", ".kilocode"),
        new AgentConfig("warp", "Warp",
            ".agents/skills", ".agents/skills", ".warp"),
        new AgentConfig("aider-desk", "AiderDesk",
            ".aider-desk/skills", ".aider-desk/skills", ".aider-desk"),
        new AgentConfig("codearts-agent", "CodeArts Agent",
            ".codeartsdoer/skills", ".codeartsdoer/skills", ".codeartsdoer"),
        new AgentConfig("codemaker", "Codemaker",
            ".codemaker/skills", ".codemaker/skills", ".codemaker"),
        new AgentConfig("codestudio", "Code Studio",
            ".codestudio/skills", ".codestudio/skills", ".codestudio"),
        new AgentConfig("devin", "Devin for Terminal",
            ".devin/skills", ".devin/skills", ".devin"),
        new AgentConfig("dexto", "Dexto",
            ".agents/skills", ".agents/skills", ".dexto"),
        new AgentConfig("forgecode", "ForgeCode",
            ".forge/skills", ".forge/skills", ".forge"),
        new AgentConfig("rovodev", "Rovo Dev",
            ".rovodev/skills", ".rovodev/skills", ".rovodev"),
        new AgentConfig("tabnine-cli", "Tabnine CLI",
            ".tabnine/agent/skills", ".tabnine/agent/skills", ".tabnine"),
        // Universal agent — canonical .agents/skills dir
        new AgentConfig("universal", "Universal",
            ".agents/skills", ".agents/skills", ".agents")
    ));

    public static List<AgentConfig> getAgents() {
        return AGENTS;
    }

    /**
     * Find an agent by its name identifier.
     */
    public static Optional<AgentConfig> findByName(String name) {
        return AGENTS.stream()
                .filter(a -> a.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    /**
     * Returns agents that appear to be installed on this machine.
     */
    public static List<AgentConfig> getInstalledAgents() {
        return AGENTS.stream()
                .filter(AgentConfig::isInstalled)
                .collect(java.util.stream.Collectors.toList());
    }
}
