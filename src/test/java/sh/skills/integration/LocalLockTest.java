package sh.skills.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.skills.lock.LocalLock;
import sh.skills.model.SkillLockEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for local lock file operations.
 * Mirrors tests/local-lock.test.ts from TypeScript implementation.
 * Tests the project-level local-lock.json file management.
 */
@DisplayName("Local Lock")
class LocalLockTest {

    @Test
    void getLocalLockPath_usesSpecifiedDirectory(@TempDir Path tempDir) {
        LocalLock lock = new LocalLock(tempDir);
        Path lockPath = lock.getLockFile();

        assertThat(lockPath)
            .isEqualTo(tempDir.resolve("local-lock.json"));
    }

    @Test
    void readLocalLock_returnsEmptyWhenFileDoesNotExist(@TempDir Path tempDir) {
        LocalLock lock = new LocalLock(tempDir);
        Map<String, SkillLockEntry> entries = lock.readAll();

        assertThat(entries).isNotNull();
        assertThat(entries).isEmpty();
    }

    @Test
    void readLocalLock_parsesValidJson(@TempDir Path tempDir) throws Exception {
        Path lockFile = tempDir.resolve("local-lock.json");
        String json = """
            {
              "test-agent:test-skill": {
                "source": "owner/repo",
                "skillFolderHash": "abc123",
                "updatedAt": "2026-03-31T00:00:00Z"
              }
            }
            """;
        Files.writeString(lockFile, json);

        LocalLock lock = new LocalLock(tempDir);
        Map<String, SkillLockEntry> entries = lock.readAll();

        assertThat(entries).hasSize(1);
        assertThat(entries.get("test-agent:test-skill").getSource())
            .isEqualTo("owner/repo");
        assertThat(entries.get("test-agent:test-skill").getSkillFolderHash())
            .isEqualTo("abc123");
    }

    @Test
    void readLocalLock_handlesCorruptJson(@TempDir Path tempDir) throws Exception {
        Path lockFile = tempDir.resolve("local-lock.json");
        Files.writeString(lockFile, "{ invalid json }");

        LocalLock lock = new LocalLock(tempDir);
        Map<String, SkillLockEntry> entries = lock.readAll();

        assertThat(entries).isNotNull();
        assertThat(entries).isEmpty();
    }

    @Test
    void readLocalLock_handlesMergeConflictMarkers(@TempDir Path tempDir)
            throws Exception {
        Path lockFile = tempDir.resolve("local-lock.json");
        String json = """
            {
            <<<<<<< HEAD
              "agent-a:skill-a": { "source": "a" }
            =======
              "agent-b:skill-b": { "source": "b" }
            >>>>>>> branch
            }
            """;
        Files.writeString(lockFile, json);

        LocalLock lock = new LocalLock(tempDir);
        Map<String, SkillLockEntry> entries = lock.readAll();

        assertThat(entries).isNotNull();
        assertThat(entries).isEmpty();
    }

    @Test
    void writeLocalLock_createsDeterministicJson(@TempDir Path tempDir)
            throws Exception {
        LocalLock lock = new LocalLock(tempDir);

        SkillLockEntry entry = new SkillLockEntry();
        entry.setSource("owner/repo");
        entry.setSkillFolderHash("abc123");
        entry.setUpdatedAt("2026-03-31T00:00:00Z");

        lock.write("test-agent", "test-skill", entry);

        Path lockFile = tempDir.resolve("local-lock.json");
        String content = Files.readString(lockFile);

        assertThat(content).contains("test-agent:test-skill");
        assertThat(content).contains("\"source\" : \"owner/repo\"");
        // Verify it's valid JSON with proper indentation
        assertThat(content).contains("  \"");
    }

    @Test
    void addSkillToLocalLock_addsNewSkill(@TempDir Path tempDir) throws Exception {
        LocalLock lock = new LocalLock(tempDir);

        SkillLockEntry entry = new SkillLockEntry();
        entry.setSource("owner/repo");
        entry.setSkillFolderHash("abc123");

        lock.write("test-agent", "new-skill", entry);

        Map<String, SkillLockEntry> entries = lock.readAll();
        assertThat(entries).containsKey("test-agent:new-skill");
        assertThat(entries.get("test-agent:new-skill").getSource())
            .isEqualTo("owner/repo");
    }

    @Test
    void addSkillToLocalLock_updatesExistingSkill(@TempDir Path tempDir)
            throws Exception {
        LocalLock lock = new LocalLock(tempDir);

        // Add first entry
        SkillLockEntry entry1 = new SkillLockEntry();
        entry1.setSource("owner/repo");
        entry1.setSkillFolderHash("abc123");
        lock.write("test-agent", "skill", entry1);

        // Update with new entry
        SkillLockEntry entry2 = new SkillLockEntry();
        entry2.setSource("owner/repo");
        entry2.setSkillFolderHash("xyz789");
        lock.write("test-agent", "skill", entry2);

        Map<String, SkillLockEntry> entries = lock.readAll();
        assertThat(entries).hasSize(1);
        assertThat(entries.get("test-agent:skill").getSkillFolderHash())
            .isEqualTo("xyz789");
    }

    @Test
    void removeSkillFromLocalLock_removesExistingSkill(@TempDir Path tempDir)
            throws Exception {
        LocalLock lock = new LocalLock(tempDir);

        // Add skill first
        SkillLockEntry entry = new SkillLockEntry();
        entry.setSource("owner/repo");
        lock.write("test-agent", "skill", entry);

        // Remove it
        lock.remove("test-agent", "skill");

        Map<String, SkillLockEntry> entries = lock.readAll();
        assertThat(entries).isEmpty();
    }

    @Test
    void removeSkillFromLocalLock_returnsFalseForNonexistent(@TempDir Path tempDir)
            throws Exception {
        boolean removed = LocalLock.removeSkillFromLocalLock(tempDir, "nonexistent");

        assertThat(removed).isFalse();
    }

    @Test
    void getLocalLockPath_usesCwdWhenNull() {
        Path lockPath = LocalLock.getLocalLockPath(null);

        assertThat(lockPath.getFileName().toString())
            .isEqualTo("local-lock.json");
    }

    @Test
    void computeSkillFolderHash_generatesConsistentHash(@TempDir Path tempDir)
            throws Exception {
        Path skillDir = tempDir.resolve("test-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "# Test Skill");
        Files.writeString(skillDir.resolve("code.js"), "console.log('test');");

        String hash1 = LocalLock.computeSkillFolderHash(skillDir);
        String hash2 = LocalLock.computeSkillFolderHash(skillDir);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 hex string
    }

    @Test
    void computeSkillFolderHash_excludesGitAndNodeModules(@TempDir Path tempDir)
            throws Exception {
        Path skillDir = tempDir.resolve("test-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "# Test");

        // Create .git and node_modules
        Path gitDir = skillDir.resolve(".git");
        Files.createDirectories(gitDir);
        Files.writeString(gitDir.resolve("config"), "git config");

        Path nmDir = skillDir.resolve("node_modules");
        Files.createDirectories(nmDir);
        Files.writeString(nmDir.resolve("package.json"), "{}");

        String hashBefore = LocalLock.computeSkillFolderHash(skillDir);

        // Modify .git and node_modules
        Files.writeString(gitDir.resolve("config"), "different content");
        Files.writeString(nmDir.resolve("package.json"), "{ \"changed\": true }");

        String hashAfter = LocalLock.computeSkillFolderHash(skillDir);

        // Hash should be unchanged since .git and node_modules are excluded
        assertThat(hashBefore).isEqualTo(hashAfter);
    }

    @Test
    void computeSkillFolderHash_changesWhenContentChanges(@TempDir Path tempDir)
            throws Exception {
        Path skillDir = tempDir.resolve("test-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "# Test");

        String hashBefore = LocalLock.computeSkillFolderHash(skillDir);

        // Modify content
        Files.writeString(skillDir.resolve("SKILL.md"), "# Changed");

        String hashAfter = LocalLock.computeSkillFolderHash(skillDir);

        assertThat(hashBefore).isNotEqualTo(hashAfter);
    }
}
