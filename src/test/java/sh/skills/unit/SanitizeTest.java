package sh.skills.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sh.skills.util.Sanitize;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Sanitize")
class SanitizeTest {

    @Test
    void stripTerminalEscapes_removesAnsiSequences() {
        String input = "hello\u001B[31mred\u001B[0m world";
        assertThat(Sanitize.stripTerminalEscapes(input)).isEqualTo("hellored world");
    }

    @Test
    void stripTerminalEscapes_removesOscSequences() {
        String input = "before\u001B]0;evil title\u0007after";
        assertThat(Sanitize.stripTerminalEscapes(input)).isEqualTo("beforeafter");
    }

    @Test
    void sanitizeMetadata_flattensNewlines() {
        String input = "skill\nname\r\ndescription";
        assertThat(Sanitize.sanitizeMetadata(input)).isEqualTo("skill name description");
    }
}
