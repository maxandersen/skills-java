package sh.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import sh.skills.model.ParsedSource;
import sh.skills.providers.*;
import sh.skills.util.SourceParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for source URL parsing via provider registry and SourceParser.
 * Driven by test-fixtures/source-parser-cases.json
 */
@DisplayName("Source Parser")
class SourceParserTest {

    record TestCase(String description, String source, String expectedProvider,
                    String expectedOwner, String expectedRepo,
                    String expectedBranch, String expectedPath,
                    String expectedRef, String expectedSkillFilter) {}

    static Stream<TestCase> loadCases() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = SourceParserTest.class.getResourceAsStream("/test-fixtures/source-parser-cases.json");
        if (is == null) {
            is = java.nio.file.Files.newInputStream(
                java.nio.file.Paths.get("test-fixtures/source-parser-cases.json"));
        }
        JsonNode root = mapper.readTree(is);
        List<TestCase> cases = new ArrayList<>();
        for (JsonNode c : root.get("cases")) {
            cases.add(new TestCase(
                c.get("description").asText(),
                c.get("source").asText(),
                c.get("expectedProvider").asText(),
                c.has("expectedOwner") ? c.get("expectedOwner").asText(null) : null,
                c.has("expectedRepo") ? c.get("expectedRepo").asText(null) : null,
                c.has("expectedBranch") && !c.get("expectedBranch").isNull() ? c.get("expectedBranch").asText() : null,
                c.has("expectedPath") && !c.get("expectedPath").isNull() ? c.get("expectedPath").asText() : null,
                c.has("expectedRef") && !c.get("expectedRef").isNull() ? c.get("expectedRef").asText() : null,
                c.has("expectedSkillFilter") && !c.get("expectedSkillFilter").isNull() ? c.get("expectedSkillFilter").asText() : null
            ));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("loadCases")
    void testProviderMatching(TestCase tc) {
        // Strip fragment for provider matching (providers strip it internally)
        HostProvider provider = ProviderRegistry.findProvider(tc.source());

        assertThat(provider)
            .as("Provider found for: " + tc.source())
            .isNotNull();

        assertThat(provider.getSourceType())
            .as(tc.description())
            .isEqualTo(tc.expectedProvider());

        // Additional assertions for GitHub provider
        if ("github".equals(tc.expectedProvider()) && provider instanceof GitHubProvider ghp) {
            GitHubProvider.ParsedGitHubSource parsed = ghp.parse(tc.source());
            if (tc.expectedOwner() != null) {
                assertThat(parsed.owner).as("owner").isEqualTo(tc.expectedOwner());
            }
            if (tc.expectedRepo() != null) {
                assertThat(parsed.repo).as("repo").isEqualTo(tc.expectedRepo());
            }
            assertThat(parsed.branch).as("branch").isEqualTo(tc.expectedBranch());
            assertThat(parsed.skillPath).as("skillPath").isEqualTo(tc.expectedPath());
        }
    }

    @ParameterizedTest(name = "{0} (SourceParser)")
    @MethodSource("loadCases")
    void testSourceParser(TestCase tc) {
        ParsedSource parsed = SourceParser.parseSource(tc.source());

        assertThat(parsed.getType())
            .as(tc.description() + " type")
            .isEqualTo(mapProviderToType(tc.expectedProvider()));

        if (tc.expectedRef() != null) {
            assertThat(parsed.getRef())
                .as(tc.description() + " ref")
                .isEqualTo(tc.expectedRef());
        }

        if (tc.expectedSkillFilter() != null) {
            assertThat(parsed.getSkillFilter())
                .as(tc.description() + " skillFilter")
                .isEqualTo(tc.expectedSkillFilter());
        }
    }

    private String mapProviderToType(String provider) {
        return switch (provider) {
            case "wellknown" -> "well-known";
            default -> provider;
        };
    }

    @Nested
    @DisplayName("Fragment ref parsing")
    class FragmentRefTests {

        @Test
        void noFragment() {
            var result = SourceParser.parseFragmentRef("owner/repo");
            assertThat(result.inputWithoutFragment).isEqualTo("owner/repo");
            assertThat(result.ref).isNull();
            assertThat(result.skillFilter).isNull();
        }

        @Test
        void branchRefOnly() {
            var result = SourceParser.parseFragmentRef("owner/repo#develop");
            assertThat(result.inputWithoutFragment).isEqualTo("owner/repo");
            assertThat(result.ref).isEqualTo("develop");
            assertThat(result.skillFilter).isNull();
        }

        @Test
        void branchRefWithSkillFilter() {
            var result = SourceParser.parseFragmentRef("owner/repo#main@my-skill");
            assertThat(result.inputWithoutFragment).isEqualTo("owner/repo");
            assertThat(result.ref).isEqualTo("main");
            assertThat(result.skillFilter).isEqualTo("my-skill");
        }

        @Test
        void wellKnownUrlFragmentNotTreatedAsRef() {
            var result = SourceParser.parseFragmentRef("https://example.com/.well-known/skills/#section");
            // Should NOT strip fragment for non-git sources
            assertThat(result.ref).isNull();
        }

        @Test
        void githubUrlWithRef() {
            var result = SourceParser.parseFragmentRef("https://github.com/owner/repo#v2.0");
            assertThat(result.inputWithoutFragment).isEqualTo("https://github.com/owner/repo");
            assertThat(result.ref).isEqualTo("v2.0");
        }

        @Test
        void encodedFragmentValue() {
            var result = SourceParser.parseFragmentRef("owner/repo#feature%2Fbranch");
            assertThat(result.ref).isEqualTo("feature/branch");
        }
    }
}
