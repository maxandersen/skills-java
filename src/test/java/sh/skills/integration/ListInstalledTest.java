package sh.skills.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.skills.model.Skill;
import sh.skills.providers.SkillDiscovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for skill discovery and listing.
 * Mirrors tests/list-installed.test.ts from TypeScript implementation.
 */
@DisplayName("List Installed Skills")
class ListInstalledTest {

    @Test
    void emptyDirectory_returnsNoSkills(@TempDir Path tempDir) throws Exception {
        SkillDiscovery discovery = new SkillDiscovery();

        List<Skill> skills = discovery.discover(tempDir, null, false);

        assertThat(skills).isEmpty();
    }

    @Test
    void singleSkill_discoveredCorrectly(@TempDir Path tempDir) throws Exception {
        // Create skill directory with SKILL.md
        Path skillDir = tempDir.resolve("test-skill");
        Files.createDirectories(skillDir);
        String skillMd = """
            ---
            name: test-skill
            description: A test skill
            ---
            # Test Skill

            This is a test skill.
            """;
        Files.writeString(skillDir.resolve("SKILL.md"), skillMd);

        SkillDiscovery discovery = new SkillDiscovery();
        List<Skill> skills = discovery.discover(tempDir, null, false);

        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).getName()).isEqualTo("test-skill");
        assertThat(skills.get(0).getDescription()).isEqualTo("A test skill");
    }

    @Test
    void multipleSkills_allDiscovered(@TempDir Path tempDir) throws Exception {
        // Create multiple skill directories
        createSkill(tempDir, "skill-a", "Skill A");
        createSkill(tempDir, "skill-b", "Skill B");
        createSkill(tempDir, "skill-c", "Skill C");

        SkillDiscovery discovery = new SkillDiscovery();
        List<Skill> skills = discovery.discover(tempDir, null, false);

        assertThat(skills).hasSize(3);
        assertThat(skills).extracting(Skill::getName)
            .containsExactlyInAnyOrder("skill-a", "skill-b", "skill-c");
    }

    @Test
    void missingSkillMd_directoryIgnored(@TempDir Path tempDir) throws Exception {
        // Create directory without SKILL.md
        Path skillDir = tempDir.resolve("not-a-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("README.md"), "# Not a skill");

        SkillDiscovery discovery = new SkillDiscovery();
        List<Skill> skills = discovery.discover(tempDir, null, false);

        assertThat(skills).isEmpty();
    }

    @Test
    void malformedFrontmatter_skillIgnored(@TempDir Path tempDir) throws Exception {
        Path skillDir = tempDir.resolve("bad-skill");
        Files.createDirectories(skillDir);
        String badMd = """
            ---
            name: this is not valid yaml: [
            ---
            # Bad Skill
            """;
        Files.writeString(skillDir.resolve("SKILL.md"), badMd);

        SkillDiscovery discovery = new SkillDiscovery();
        List<Skill> skills = discovery.discover(tempDir, null, false);

        // Should handle gracefully - FrontmatterParser returns null for malformed YAML
        assertThat(skills).isEmpty();
    }

    @Test
    void nestedSkills_discoveredByRecursiveFallback(@TempDir Path tempDir)
            throws Exception {
        // Create nested skill (no root skill, so recursive fallback triggers)
        Path nestedDir = tempDir.resolve("subdir");
        Files.createDirectories(nestedDir);
        createSkill(nestedDir, "nested-skill", "Nested");

        SkillDiscovery discovery = new SkillDiscovery();
        List<Skill> skills = discovery.discover(tempDir, null, false);

        // Recursive fallback should find nested skill
        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).getName()).isEqualTo("nested-skill");
    }

    // Helper method
    private void createSkill(Path parentDir, String name, String description)
            throws Exception {
        Path skillDir = parentDir.resolve(name);
        Files.createDirectories(skillDir);
        String skillMd = String.format("""
            ---
            name: %s
            description: %s
            ---
            # %s
            """, name, description, name);
        Files.writeString(skillDir.resolve("SKILL.md"), skillMd);
    }
}
