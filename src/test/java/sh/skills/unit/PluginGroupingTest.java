package sh.skills.unit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import sh.skills.model.PluginManifest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for plugin manifest grouping logic.
 * Driven by test-fixtures/plugin-grouping-cases.json
 */
@DisplayName("Plugin Grouping")
class PluginGroupingTest {

    record TestCase(String description, JsonNode manifest,
                    List<String> expectedSkillPaths, Integer expectedSkillCount) {}

    static Stream<TestCase> loadCases() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = PluginGroupingTest.class
            .getResourceAsStream("/test-fixtures/plugin-grouping-cases.json");
        if (is == null) {
            is = java.nio.file.Files.newInputStream(
                java.nio.file.Paths.get("test-fixtures/plugin-grouping-cases.json"));
        }
        JsonNode root = mapper.readTree(is);
        List<TestCase> cases = new ArrayList<>();

        for (JsonNode c : root.get("cases")) {
            List<String> paths = new ArrayList<>();
            JsonNode expectedPaths = c.get("expectedSkillPaths");
            if (expectedPaths != null && expectedPaths.isArray()) {
                for (JsonNode p : expectedPaths) {
                    paths.add(p.asText());
                }
            }

            Integer expectedCount = null;
            JsonNode countNode = c.get("expectedSkillCount");
            if (countNode != null) {
                expectedCount = countNode.asInt();
            }

            cases.add(new TestCase(
                c.get("description").asText(),
                c.get("manifest"),
                paths,
                expectedCount
            ));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("loadCases")
    void testPluginGrouping(TestCase tc) {
        ObjectMapper mapper = new ObjectMapper();
        PluginManifest manifest = mapper.convertValue(
            tc.manifest(), PluginManifest.class);

        Map<String, List<String>> groupings =
            PluginManifest.getSkillPaths(manifest);

        List<String> allPaths = new ArrayList<>();
        groupings.values().forEach(allPaths::addAll);

        // If we have explicit expected paths, check them
        if (!tc.expectedSkillPaths().isEmpty()) {
            assertThat(allPaths)
                .as(tc.description())
                .containsExactlyInAnyOrderElementsOf(tc.expectedSkillPaths());
        }
        // Otherwise, check the count if specified
        else if (tc.expectedSkillCount() != null) {
            assertThat(allPaths)
                .as(tc.description() + " - skill count")
                .hasSize(tc.expectedSkillCount());
        }
    }
}
