package sh.skills.util;

/**
 * Utilities for building source arguments during skill updates.
 * Ports src/update-source.ts from the TypeScript source.
 */
public class UpdateSource {

    /**
     * Format a source URL with an optional ref fragment.
     */
    public static String formatSourceInput(String sourceUrl, String ref) {
        if (ref == null || ref.isEmpty()) {
            return sourceUrl;
        }
        return sourceUrl + "#" + ref;
    }

    /**
     * Build the source argument for `skills add` during global update.
     * Uses shorthand form for path-targeted updates to avoid branch/path ambiguity.
     */
    public static String buildUpdateInstallSource(String source, String sourceUrl,
                                                   String ref, String skillPath) {
        if (skillPath == null || skillPath.isEmpty()) {
            return formatSourceInput(sourceUrl, ref);
        }

        // Extract skill folder from skillPath (remove /SKILL.md suffix)
        String skillFolder = skillPath;
        if (skillFolder.endsWith("/SKILL.md")) {
            skillFolder = skillFolder.substring(0, skillFolder.length() - 9);
        } else if (skillFolder.endsWith("SKILL.md")) {
            skillFolder = skillFolder.substring(0, skillFolder.length() - 8);
        }
        if (skillFolder.endsWith("/")) {
            skillFolder = skillFolder.substring(0, skillFolder.length() - 1);
        }

        String installSource = !skillFolder.isEmpty() ? source + "/" + skillFolder : source;
        if (ref != null && !ref.isEmpty()) {
            installSource = installSource + "#" + ref;
        }
        return installSource;
    }

    /**
     * Build the source argument for `skills add` during project-level update.
     * Local lock entries only have source and ref (no skillPath or sourceUrl).
     */
    public static String buildLocalUpdateSource(String source, String ref) {
        return formatSourceInput(source, ref);
    }
}
