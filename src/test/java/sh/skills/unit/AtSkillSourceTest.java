package sh.skills.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sh.skills.model.ParsedSource;
import sh.skills.providers.GitHubProvider;
import sh.skills.providers.ProviderRegistry;
import sh.skills.util.SourceParser;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that owner/repo@skill format (from find output) works with add.
 */
@DisplayName("owner/repo@skill source format")
class AtSkillSourceTest {

    @Test
    @DisplayName("GitHubProvider should match owner/repo@skill")
    void providerMatchesAtSkill() {
        assertThat(new GitHubProvider().matches("decebals/claude-code-java@java-code-review"))
            .as("owner/repo@skill should be recognized as GitHub source")
            .isTrue();
    }

    @Test
    @DisplayName("ProviderRegistry should find provider for owner/repo@skill")
    void registryFindsProvider() {
        assertThat(ProviderRegistry.findProvider("decebals/claude-code-java@java-code-review"))
            .as("should find a provider for owner/repo@skill")
            .isNotNull();
    }

    @Test
    @DisplayName("SourceParser should extract skill filter from owner/repo@skill")
    void parserExtractsSkillFilter() {
        ParsedSource parsed = SourceParser.parseSource("decebals/claude-code-java@java-code-review");
        assertThat(parsed.getType()).isEqualTo("github");
        assertThat(parsed.getUrl()).isEqualTo("https://github.com/decebals/claude-code-java.git");
        assertThat(parsed.getSkillFilter()).isEqualTo("java-code-review");
    }
}
