package sh.skills.util;

import sh.skills.model.ParsedSource;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses skill source strings into structured ParsedSource objects.
 * Ports src/source-parser.ts from the TypeScript source.
 *
 * Supports: local paths, GitHub URLs, GitLab URLs, GitHub shorthand,
 * well-known URLs, direct git URLs, and #ref fragments.
 */
public class SourceParser {

    // Source aliases: map common shorthand to canonical source
    private static final Map<String, String> SOURCE_ALIASES = Map.of(
        "coinbase/agentWallet", "coinbase/agentic-wallet-skills"
    );

    // Patterns
    private static final Pattern GITHUB_TREE_WITH_PATH = Pattern.compile(
        "github\\.com/([^/]+)/([^/]+)/tree/([^/]+)/(.+)");
    private static final Pattern GITHUB_TREE = Pattern.compile(
        "github\\.com/([^/]+)/([^/]+)/tree/([^/]+)$");
    private static final Pattern GITHUB_REPO = Pattern.compile(
        "github\\.com/([^/]+)/([^/]+)");
    private static final Pattern GITLAB_TREE_WITH_PATH = Pattern.compile(
        "^(https?)://([^/]+)/(.+?)/-/tree/([^/]+)/(.+)");
    private static final Pattern GITLAB_TREE = Pattern.compile(
        "^(https?)://([^/]+)/(.+?)/-/tree/([^/]+)$");
    private static final Pattern GITLAB_REPO = Pattern.compile(
        "gitlab\\.com/(.+?)(?:\\.git)?/?$");
    private static final Pattern AT_SKILL = Pattern.compile(
        "^([^/]+)/([^/@]+)@(.+)$");
    private static final Pattern SHORTHAND = Pattern.compile(
        "^([^/]+)/([^/]+)(?:/(.+?))?/?$");
    private static final Pattern GIT_SSH = Pattern.compile(
        "^git@[^:]+:.+\\.git$");
    private static final Pattern GIT_HTTPS = Pattern.compile(
        "^https?://.+\\.git$");

    /**
     * Parse a source string into a structured ParsedSource.
     */
    public static ParsedSource parseSource(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Source input cannot be empty");
        }
        input = input.trim();

        // Local path: absolute, relative, or current directory
        if (isLocalPath(input)) {
            Path resolved = Paths.get(input).toAbsolutePath().normalize();
            return ParsedSource.builder("local", resolved.toString())
                .localPath(resolved.toString())
                .build();
        }

        // Extract fragment ref (#branch, #branch@skill)
        FragmentRef frag = parseFragmentRef(input);
        input = frag.inputWithoutFragment;

        // Resolve source aliases
        String alias = SOURCE_ALIASES.get(input);
        if (alias != null) {
            input = alias;
        }

        // Prefix shorthand: github:owner/repo
        if (input.startsWith("github:")) {
            return parseSource(appendFragmentRef(input.substring(7), frag.ref, frag.skillFilter));
        }

        // Prefix shorthand: gitlab:owner/repo
        if (input.startsWith("gitlab:")) {
            return parseSource(appendFragmentRef(
                "https://gitlab.com/" + input.substring(7), frag.ref, frag.skillFilter));
        }

        // GitHub URL with path: .../tree/branch/path
        Matcher m = GITHUB_TREE_WITH_PATH.matcher(input);
        if (m.find()) {
            return ParsedSource.builder("github",
                    "https://github.com/" + m.group(1) + "/" + m.group(2) + ".git")
                .ref(m.group(3) != null ? m.group(3) : frag.ref)
                .subpath(sanitizeSubpath(m.group(4)))
                .build();
        }

        // GitHub URL with branch only: .../tree/branch
        m = GITHUB_TREE.matcher(input);
        if (m.find()) {
            return ParsedSource.builder("github",
                    "https://github.com/" + m.group(1) + "/" + m.group(2) + ".git")
                .ref(m.group(3) != null ? m.group(3) : frag.ref)
                .build();
        }

        // GitHub URL: https://github.com/owner/repo
        m = GITHUB_REPO.matcher(input);
        if (m.find() && input.contains("github.com")) {
            String repo = m.group(2).replaceAll("\\.git$", "");
            return ParsedSource.builder("github",
                    "https://github.com/" + m.group(1) + "/" + repo + ".git")
                .ref(frag.ref)
                .build();
        }

        // GitLab URL with path
        m = GITLAB_TREE_WITH_PATH.matcher(input);
        if (m.find() && !"github.com".equals(m.group(2))) {
            return ParsedSource.builder("gitlab",
                    m.group(1) + "://" + m.group(2) + "/" + m.group(3).replaceAll("\\.git$", "") + ".git")
                .ref(m.group(4) != null ? m.group(4) : frag.ref)
                .subpath(sanitizeSubpath(m.group(5)))
                .build();
        }

        // GitLab URL with branch only
        m = GITLAB_TREE.matcher(input);
        if (m.find() && !"github.com".equals(m.group(2))) {
            return ParsedSource.builder("gitlab",
                    m.group(1) + "://" + m.group(2) + "/" + m.group(3).replaceAll("\\.git$", "") + ".git")
                .ref(m.group(4) != null ? m.group(4) : frag.ref)
                .build();
        }

        // GitLab.com URL
        m = GITLAB_REPO.matcher(input);
        if (m.find() && input.contains("gitlab.com")) {
            String repoPath = m.group(1);
            if (repoPath.contains("/")) {
                return ParsedSource.builder("gitlab",
                        "https://gitlab.com/" + repoPath + ".git")
                    .ref(frag.ref)
                    .build();
            }
        }

        // GitHub shorthand with @skill: owner/repo@skill-name
        m = AT_SKILL.matcher(input);
        if (m.find() && !input.contains(":") && !input.startsWith(".") && !input.startsWith("/")) {
            return ParsedSource.builder("github",
                    "https://github.com/" + m.group(1) + "/" + m.group(2) + ".git")
                .ref(frag.ref)
                .skillFilter(frag.skillFilter != null ? frag.skillFilter : m.group(3))
                .build();
        }

        // GitHub shorthand: owner/repo or owner/repo/subpath
        m = SHORTHAND.matcher(input);
        if (m.find() && !input.contains(":") && !input.startsWith(".") && !input.startsWith("/")) {
            return ParsedSource.builder("github",
                    "https://github.com/" + m.group(1) + "/" + m.group(2) + ".git")
                .ref(frag.ref)
                .subpath(m.group(3) != null ? sanitizeSubpath(m.group(3)) : null)
                .skillFilter(frag.skillFilter)
                .build();
        }

        // Well-known skills: HTTP(S) URLs that aren't GitHub/GitLab
        if (isWellKnownUrl(input)) {
            return ParsedSource.builder("well-known", input).build();
        }

        // Fallback: direct git URL
        return ParsedSource.builder("git", input)
            .ref(frag.ref)
            .build();
    }

    /**
     * Extract owner/repo from a parsed source for lockfile tracking.
     * Returns null for local paths or unparseable sources.
     */
    public static String getOwnerRepo(ParsedSource parsed) {
        if ("local".equals(parsed.getType())) return null;

        String url = parsed.getUrl();

        // SSH URLs: git@host:owner/repo.git
        if (url.startsWith("git@")) {
            Matcher m = Pattern.compile("^git@[^:]+:(.+)$").matcher(url);
            if (m.find()) {
                String path = m.group(1).replaceAll("\\.git$", "");
                return path.contains("/") ? path : null;
            }
            return null;
        }

        // HTTP(S) URLs
        if (url.startsWith("http://") || url.startsWith("https://")) {
            try {
                URI uri = URI.create(url);
                String path = uri.getPath();
                if (path != null && path.startsWith("/")) {
                    path = path.substring(1);
                }
                if (path != null) {
                    path = path.replaceAll("\\.git$", "");
                    if (path.contains("/")) return path;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    // --- Fragment ref parsing (upstream #814) ---

    public static class FragmentRef {
        public final String inputWithoutFragment;
        public final String ref;
        public final String skillFilter;

        FragmentRef(String inputWithoutFragment, String ref, String skillFilter) {
            this.inputWithoutFragment = inputWithoutFragment;
            this.ref = ref;
            this.skillFilter = skillFilter;
        }
    }

    public static FragmentRef parseFragmentRef(String input) {
        int hashIndex = input.indexOf('#');
        if (hashIndex < 0) {
            return new FragmentRef(input, null, null);
        }

        String inputWithoutFragment = input.substring(0, hashIndex);
        String fragment = input.substring(hashIndex + 1);

        // Only treat fragments as git refs for git-like sources
        if (fragment.isEmpty() || !looksLikeGitSource(inputWithoutFragment)) {
            return new FragmentRef(input, null, null);
        }

        int atIndex = fragment.indexOf('@');
        if (atIndex == -1) {
            return new FragmentRef(inputWithoutFragment, decodeFragment(fragment), null);
        }

        String ref = fragment.substring(0, atIndex);
        String skillFilter = fragment.substring(atIndex + 1);
        return new FragmentRef(
            inputWithoutFragment,
            ref.isEmpty() ? null : decodeFragment(ref),
            skillFilter.isEmpty() ? null : decodeFragment(skillFilter)
        );
    }

    public static boolean looksLikeGitSource(String input) {
        if (input.startsWith("github:") || input.startsWith("gitlab:") || input.startsWith("git@")) {
            return true;
        }

        if (input.startsWith("http://") || input.startsWith("https://")) {
            try {
                URI uri = URI.create(input);
                String hostname = uri.getHost();
                String path = uri.getPath();

                if ("github.com".equals(hostname)) {
                    return path != null && path.matches("^/[^/]+/[^/]+(?:\\.git)?(?:/tree/[^/]+(?:/.*)?)?/?$");
                }
                if ("gitlab.com".equals(hostname)) {
                    return path != null && path.matches("^/.+?/[^/]+(?:\\.git)?(?:/-/tree/[^/]+(?:/.*)?)?/?$");
                }
            } catch (Exception ignored) {}
        }

        if (input.matches("(?i)^https?://.+\\.git($|[/?])")) {
            return true;
        }

        return !input.contains(":") && !input.startsWith(".") && !input.startsWith("/")
            && input.matches("^([^/]+)/([^/]+)(?:/(.+)|@(.+))?$");
    }

    private static String decodeFragment(String value) {
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private static String appendFragmentRef(String input, String ref, String skillFilter) {
        if (ref == null) return input;
        return input + "#" + ref + (skillFilter != null ? "@" + skillFilter : "");
    }

    private static boolean isLocalPath(String input) {
        return input.startsWith("/") || input.startsWith("./") || input.startsWith("../")
            || input.equals(".") || input.equals("..")
            || (input.length() >= 3 && Character.isLetter(input.charAt(0)) && input.charAt(1) == ':'
                && (input.charAt(2) == '/' || input.charAt(2) == '\\'));
    }

    private static boolean isWellKnownUrl(String input) {
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            return false;
        }
        try {
            URI uri = URI.create(input);
            String host = uri.getHost();
            if ("github.com".equals(host) || "gitlab.com".equals(host)
                || "raw.githubusercontent.com".equals(host)) {
                return false;
            }
            if (input.endsWith(".git")) return false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Sanitize a subpath to prevent path traversal.
     */
    public static String sanitizeSubpath(String subpath) {
        if (subpath == null) return null;
        String normalized = subpath.replace('\\', '/');
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException(
                    "Unsafe subpath: \"" + subpath + "\" contains path traversal segments.");
            }
        }
        return subpath;
    }
}
