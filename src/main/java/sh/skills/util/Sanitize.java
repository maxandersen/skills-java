package sh.skills.util;

/**
 * Sanitizes untrusted strings before terminal output.
 * Ports src/sanitize.ts from the TypeScript source.
 */
public final class Sanitize {

    private Sanitize() {}

    // CSI sequences: ESC[ followed by parameters, intermediates, and a final byte.
    private static final String CSI_RE = "\u001B\\[[\\x30-\\x3f]*[\\x20-\\x2f]*[\\x40-\\x7e]";

    // OSC sequences: ESC] ... terminated by BEL or ST.
    private static final String OSC_RE = "\u001B\\][\\s\\S]*?(?:\\u0007|\\u001B\\\\)";

    // DCS, PM, APC sequences: ESC P|^|_ ... terminated by ST.
    private static final String DCS_PM_APC_RE = "\u001B[P^_][\\s\\S]*?(?:\\u001B\\\\)";

    // Simple escape sequences: ESC + one printable char.
    private static final String SIMPLE_ESC_RE = "\u001B[\\x20-\\x7e]";

    // C1 control codes.
    private static final String C1_RE = "[\\x80-\\x9f]";

    // Raw control characters except tab and newline.
    private static final String CONTROL_RE = "[\\x00-\\x06\\x07\\x08\\x0b\\x0c\\x0d-\\x1a\\x1c-\\x1f\\x7f]";

    /**
     * Strip terminal escape sequences and dangerous control characters.
     */
    public static String stripTerminalEscapes(String value) {
        if (value == null) return "";
        return value
            .replaceAll(OSC_RE, "")
            .replaceAll(DCS_PM_APC_RE, "")
            .replaceAll(CSI_RE, "")
            .replaceAll(SIMPLE_ESC_RE, "")
            .replaceAll(C1_RE, "")
            .replaceAll(CONTROL_RE, "");
    }

    /**
     * Sanitize skill metadata for safe terminal display.
     */
    public static String sanitizeMetadata(String value) {
        return stripTerminalEscapes(value)
            .replaceAll("[\\r\\n]+", " ")
            .trim();
    }
}
