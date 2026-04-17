package sh.skills.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sh.skills.commands.FindCommand;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that formatInstalls produces consistent output regardless of locale.
 */
@DisplayName("formatInstalls locale independence")
class FormatInstallsLocaleTest {

    @Test
    @DisplayName("should use dot separator even in German locale")
    void dotSeparatorInGermanLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            // German locale uses comma as decimal separator
            // We must always get dot: "260.8K installs" not "260,8K installs"
            assertThat(FindCommand.formatInstalls(260837)).isEqualTo("260.8K installs");
            assertThat(FindCommand.formatInstalls(1500000)).isEqualTo("1.5M installs");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("should use dot separator in French locale")
    void dotSeparatorInFrenchLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            assertThat(FindCommand.formatInstalls(10058)).isEqualTo("10.1K installs");
        } finally {
            Locale.setDefault(original);
        }
    }
}
