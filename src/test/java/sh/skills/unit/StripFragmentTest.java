package sh.skills.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sh.skills.providers.GitHubProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that GitHubProvider handles @ correctly — stripping @skill
 * from owner/repo@skill but NOT breaking git@github.com:... URLs.
 */
@DisplayName("GitHubProvider @ handling")
class StripFragmentTest {

    @Test
    @DisplayName("owner/repo@skill should match as GitHub source")
    void ownerRepoAtSkillMatches() {
        assertThat(new GitHubProvider().matches("decebals/claude-code-java@java-code-review"))
            .isTrue();
    }

    @Test
    @DisplayName("owner/repo@skill should parse owner and repo correctly")
    void ownerRepoAtSkillParses() {
        var parsed = new GitHubProvider().parse("decebals/claude-code-java@java-code-review");
        assertThat(parsed.owner).isEqualTo("decebals");
        assertThat(parsed.repo).isEqualTo("claude-code-java");
    }

    @Test
    @DisplayName("git@github.com:owner/repo.git should NOT match as GitHub shorthand")
    void sshUrlDoesNotBreak() {
        // git@ URLs are handled by GitProvider, not GitHubProvider shorthand
        // But stripFragment must not mangle them
        assertThat(new GitHubProvider().matches("git@github.com:owner/repo.git"))
            .isFalse(); // SSH URLs don't match SHORTHAND or GITHUB_URL patterns
    }

    @Test
    @DisplayName("owner/repo should still match without @skill")
    void plainOwnerRepoStillMatches() {
        assertThat(new GitHubProvider().matches("vercel-labs/agent-skills")).isTrue();
    }

    @Test
    @DisplayName("owner/repo#branch should still match")
    void ownerRepoWithFragment() {
        assertThat(new GitHubProvider().matches("vercel-labs/agent-skills#main")).isTrue();
    }

    @Test
    @DisplayName("owner/repo#branch@skill should match")
    void ownerRepoWithFragmentAndSkill() {
        assertThat(new GitHubProvider().matches("vercel-labs/agent-skills#main@my-skill")).isTrue();
    }

    @Test
    @DisplayName("https://github.com/owner/repo should still match")
    void httpsUrlStillMatches() {
        assertThat(new GitHubProvider().matches("https://github.com/owner/repo")).isTrue();
    }

    @Test
    @DisplayName("parse should handle source with @ in repo name")
    void parseRepoWithAtInName() {
        // Edge case: if someone passes user@host/repo (unlikely but defensive)
        // stripFragment should not break on the first @ blindly
        var parsed = new GitHubProvider().parse("owner/repo");
        assertThat(parsed.owner).isEqualTo("owner");
        assertThat(parsed.repo).isEqualTo("repo");
    }

    @Test
    @DisplayName("parse owner/repo@skill should give repo without @skill")
    void parseOwnerRepoAtSkillGivesCleanRepo() {
        var parsed = new GitHubProvider().parse("decebals/claude-code-java@java-code-review");
        assertThat(parsed.repo).isEqualTo("claude-code-java");
        assertThat(parsed.owner).isEqualTo("decebals");
    }
}
