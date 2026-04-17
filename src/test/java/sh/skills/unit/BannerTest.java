package sh.skills.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import sh.skills.Skills;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the SKILLS logo banner is shown when running without arguments.
 */
@DisplayName("Banner")
class BannerTest {

    @Test
    @DisplayName("should show SKILLS logo when no arguments given")
    void showsBannerWithNoArgs() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(out));
        try {
            new CommandLine(new Skills()).execute();
        } finally {
            System.setOut(oldOut);
        }
        String output = out.toString();
        assertThat(output).as("should contain SKILLS logo block chars")
            .contains("███████╗██╗");
    }
}
