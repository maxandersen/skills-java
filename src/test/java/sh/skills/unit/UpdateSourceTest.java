package sh.skills.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sh.skills.util.UpdateSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for UpdateSource utility.
 * Mirrors src/update-source.test.ts from the TypeScript source.
 */
@DisplayName("UpdateSource")
class UpdateSourceTest {

    @Test
    void formatSourceInput_withoutRef() {
        assertThat(UpdateSource.formatSourceInput("vercel-labs/agent-skills", null))
            .isEqualTo("vercel-labs/agent-skills");
    }

    @Test
    void formatSourceInput_withRef() {
        assertThat(UpdateSource.formatSourceInput("vercel-labs/agent-skills", "develop"))
            .isEqualTo("vercel-labs/agent-skills#develop");
    }

    @Test
    void formatSourceInput_withEmptyRef() {
        assertThat(UpdateSource.formatSourceInput("vercel-labs/agent-skills", ""))
            .isEqualTo("vercel-labs/agent-skills");
    }

    @Test
    void buildUpdateInstallSource_noSkillPath() {
        assertThat(UpdateSource.buildUpdateInstallSource(
            "vercel-labs/agent-skills",
            "https://github.com/vercel-labs/agent-skills",
            null, null
        )).isEqualTo("https://github.com/vercel-labs/agent-skills");
    }

    @Test
    void buildUpdateInstallSource_noSkillPathWithRef() {
        assertThat(UpdateSource.buildUpdateInstallSource(
            "vercel-labs/agent-skills",
            "https://github.com/vercel-labs/agent-skills",
            "develop", null
        )).isEqualTo("https://github.com/vercel-labs/agent-skills#develop");
    }

    @Test
    void buildUpdateInstallSource_withSkillPath() {
        assertThat(UpdateSource.buildUpdateInstallSource(
            "vercel-labs/agent-skills",
            "https://github.com/vercel-labs/agent-skills",
            null, "skills/react-best-practices/SKILL.md"
        )).isEqualTo("vercel-labs/agent-skills/skills/react-best-practices");
    }

    @Test
    void buildUpdateInstallSource_withSkillPathAndRef() {
        assertThat(UpdateSource.buildUpdateInstallSource(
            "vercel-labs/agent-skills",
            "https://github.com/vercel-labs/agent-skills",
            "develop", "skills/react-best-practices/SKILL.md"
        )).isEqualTo("vercel-labs/agent-skills/skills/react-best-practices#develop");
    }

    @Test
    void buildLocalUpdateSource_withoutRef() {
        assertThat(UpdateSource.buildLocalUpdateSource("vercel-labs/agent-skills", null))
            .isEqualTo("vercel-labs/agent-skills");
    }

    @Test
    void buildLocalUpdateSource_withRef() {
        assertThat(UpdateSource.buildLocalUpdateSource("vercel-labs/agent-skills", "main"))
            .isEqualTo("vercel-labs/agent-skills#main");
    }
}
