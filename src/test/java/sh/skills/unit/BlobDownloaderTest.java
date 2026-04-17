package sh.skills.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import sh.skills.blob.BlobDownloader;
import sh.skills.blob.BlobDownloader.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for BlobDownloader pure functions.
 * Mirrors blob-related test logic from the TypeScript source.
 */
@DisplayName("BlobDownloader")
class BlobDownloaderTest {

    @Nested
    @DisplayName("toSkillSlug")
    class ToSkillSlugTests {
        @Test
        void simpleNameToSlug() {
            assertThat(BlobDownloader.toSkillSlug("React Best Practices"))
                .isEqualTo("react-best-practices");
        }

        @Test
        void underscoresToHyphens() {
            assertThat(BlobDownloader.toSkillSlug("my_skill_name"))
                .isEqualTo("my-skill-name");
        }

        @Test
        void stripSpecialChars() {
            assertThat(BlobDownloader.toSkillSlug("My Skill! @v2"))
                .isEqualTo("my-skill-v2");
        }

        @Test
        void collapseMultipleHyphens() {
            assertThat(BlobDownloader.toSkillSlug("a---b___c   d"))
                .isEqualTo("a-b-c-d");
        }

        @Test
        void trimLeadingTrailingHyphens() {
            assertThat(BlobDownloader.toSkillSlug("--hello--"))
                .isEqualTo("hello");
        }

        @Test
        void alreadySlug() {
            assertThat(BlobDownloader.toSkillSlug("already-a-slug"))
                .isEqualTo("already-a-slug");
        }
    }

    @Nested
    @DisplayName("findSkillMdPaths")
    class FindSkillMdPathsTests {

        private RepoTree makeTree(List<String> blobPaths) {
            List<TreeEntry> entries = blobPaths.stream()
                .map(p -> new TreeEntry(p, "blob", "abc123", null))
                .toList();
            return new RepoTree("rootsha", "main", entries);
        }

        @Test
        void findsRootSkillMd() {
            RepoTree tree = makeTree(List.of("SKILL.md"));
            List<String> paths = BlobDownloader.findSkillMdPaths(tree, null);
            assertThat(paths).containsExactly("SKILL.md");
        }

        @Test
        void findsPriorityDirSkills() {
            RepoTree tree = makeTree(List.of(
                "skills/react/SKILL.md",
                "skills/vue/SKILL.md",
                "random/deep/nested/SKILL.md"
            ));
            List<String> paths = BlobDownloader.findSkillMdPaths(tree, null);
            assertThat(paths).containsExactly(
                "skills/react/SKILL.md",
                "skills/vue/SKILL.md"
            );
        }

        @Test
        void fallbackToAllWhenNoPriority() {
            RepoTree tree = makeTree(List.of(
                "custom/dir/SKILL.md",
                "another/path/SKILL.md"
            ));
            List<String> paths = BlobDownloader.findSkillMdPaths(tree, null);
            assertThat(paths).containsExactlyInAnyOrder(
                "custom/dir/SKILL.md",
                "another/path/SKILL.md"
            );
        }

        @Test
        void respectsSubpath() {
            RepoTree tree = makeTree(List.of(
                "skills/react/SKILL.md",
                "skills/vue/SKILL.md",
                "other/SKILL.md"
            ));
            List<String> paths = BlobDownloader.findSkillMdPaths(tree, "skills/react");
            assertThat(paths).containsExactly("skills/react/SKILL.md");
        }

        @Test
        void limitsDepthInFallback() {
            RepoTree tree = makeTree(List.of(
                "a/b/c/d/e/f/g/SKILL.md",  // too deep (7 levels)
                "a/b/SKILL.md"              // 3 levels, OK
            ));
            List<String> paths = BlobDownloader.findSkillMdPaths(tree, null);
            assertThat(paths).containsExactly("a/b/SKILL.md");
        }
    }

    @Nested
    @DisplayName("getSkillFolderHashFromTree")
    class GetSkillFolderHashTests {

        @Test
        void returnsTreeShaForRootSkill() {
            RepoTree tree = new RepoTree("rootsha", "main", List.of());
            String hash = BlobDownloader.getSkillFolderHashFromTree(tree, "SKILL.md");
            assertThat(hash).isEqualTo("rootsha");
        }

        @Test
        void returnsFolderSha() {
            RepoTree tree = new RepoTree("rootsha", "main", List.of(
                new TreeEntry("skills/react", "tree", "reactsha", null),
                new TreeEntry("skills/vue", "tree", "vuesha", null)
            ));
            String hash = BlobDownloader.getSkillFolderHashFromTree(tree, "skills/react/SKILL.md");
            assertThat(hash).isEqualTo("reactsha");
        }

        @Test
        void returnsNullForMissingFolder() {
            RepoTree tree = new RepoTree("rootsha", "main", List.of(
                new TreeEntry("skills/react", "tree", "reactsha", null)
            ));
            String hash = BlobDownloader.getSkillFolderHashFromTree(tree, "skills/missing/SKILL.md");
            assertThat(hash).isNull();
        }

        @Test
        void stripsTrailingSlash() {
            RepoTree tree = new RepoTree("rootsha", "main", List.of(
                new TreeEntry("skills/react", "tree", "reactsha", null)
            ));
            String hash = BlobDownloader.getSkillFolderHashFromTree(tree, "skills/react/");
            assertThat(hash).isEqualTo("reactsha");
        }
    }
}
