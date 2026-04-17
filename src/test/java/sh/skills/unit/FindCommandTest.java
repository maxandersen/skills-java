package sh.skills.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import sh.skills.commands.FindCommand;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FindCommand argument parsing.
 */
@DisplayName("FindCommand")
class FindCommandTest {

    @Test
    @DisplayName("should accept multiple words as a single query: 'find web design'")
    void multiWordQuery() {
        FindCommand cmd = new FindCommand();
        CommandLine cl = new CommandLine(cmd);
        // Should NOT throw an unmatched argument exception
        int exitCode = cl.execute("web", "design");
        // Exit code 2 = picocli usage error (unmatched argument)
        assertThat(exitCode).as("multi-word query should not cause usage error (exit 2)")
            .isNotEqualTo(2);
    }

    @Test
    @DisplayName("should accept a single word query: 'find typescript'")
    void singleWordQuery() {
        FindCommand cmd = new FindCommand();
        CommandLine cl = new CommandLine(cmd);
        int exitCode = cl.execute("typescript");
        assertThat(exitCode).as("single-word query should not cause usage error")
            .isNotEqualTo(2);
    }

    @Test
    @DisplayName("should accept no query: 'find'")
    void noQuery() {
        FindCommand cmd = new FindCommand();
        CommandLine cl = new CommandLine(cmd);
        int exitCode = cl.execute();
        assertThat(exitCode).as("no query should not cause usage error")
            .isNotEqualTo(2);
    }
}
