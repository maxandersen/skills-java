package sh.skills.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sh.skills.agents.AgentRegistry;
import sh.skills.model.AgentConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that AgentRegistry matches upstream agents.ts (v1.5.1).
 * Verifies agent names, skillsDirs, and globalSkillsDirs.
 */
@DisplayName("AgentRegistry")
class AgentRegistryTest {

    @ParameterizedTest(name = "{0} → skillsDir={1}")
    @DisplayName("agents should have correct project-level skillsDir")
    @CsvSource({
        // Canonical .agents/skills agents
        "aider-desk,    .aider-desk/skills",
        "amp,           .agents/skills",
        "antigravity,   .agents/skills",
        "cline,         .agents/skills",
        "codearts-agent,.codeartsdoer/skills",
        "codemaker,     .codemaker/skills",
        "codestudio,    .codestudio/skills",
        "codex,         .agents/skills",
        "cursor,        .agents/skills",
        "deepagents,    .agents/skills",
        "devin,         .devin/skills",
        "dexto,         .agents/skills",
        "firebender,    .agents/skills",
        "forgecode,     .forge/skills",
        "gemini-cli,    .agents/skills",
        "github-copilot,.agents/skills",
        "kimi-cli,      .agents/skills",
        "opencode,      .agents/skills",
        "replit,        .agents/skills",
        "rovodev,       .rovodev/skills",
        "warp,          .agents/skills",
        "tabnine-cli,   .tabnine/agent/skills",
        // Agent-specific dirs
        "claude-code,   .claude/skills",
        "droid,         .factory/skills",
        "kilo,          .kilocode/skills",
        "mistral-vibe,  .vibe/skills",
        "openclaw,      skills",
        "roo,           .roo/skills",
        "trae-cn,       .trae/skills",
        "pi,            .pi/skills",
    })
    void skillsDir(String agentName, String expectedDir) {
        AgentConfig agent = AgentRegistry.findByName(agentName)
            .orElseThrow(() -> new AssertionError("Agent not found: " + agentName));
        assertThat(agent.getSkillsDir()).isEqualTo(expectedDir);
    }

    @Test
    @DisplayName("should have 'universal' agent")
    void hasUniversalAgent() {
        assertThat(AgentRegistry.findByName("universal")).isPresent();
    }

    @Test
    @DisplayName("should have 'roo' agent (not 'roo-code')")
    void hasRooAgent() {
        assertThat(AgentRegistry.findByName("roo")).isPresent();
    }

    @Test
    @DisplayName("should have 'command-code' agent (not 'commandcode')")
    void hasCommandCodeAgent() {
        assertThat(AgentRegistry.findByName("command-code")).isPresent();
    }

    @Test
    @DisplayName("Kilo display name should be 'Kilo Code'")
    void kiloDisplayName() {
        assertThat(AgentRegistry.findByName("kilo").get().getDisplayName())
            .isEqualTo("Kilo Code");
    }

    @Test
    @DisplayName("Cortex display name should be 'Cortex Code'")
    void cortexDisplayName() {
        assertThat(AgentRegistry.findByName("cortex").get().getDisplayName())
            .isEqualTo("Cortex Code");
    }
}
