package sh.skills.providers;

import sh.skills.util.GitUtils;
import sh.skills.model.Skill;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provider for GitHub Enterprise Server (GHES) repositories.
 * Handles:
 *   - "ghe:hostname/owner/repo" shorthand
 *   - "ghe:hostname/owner/repo/tree/branch/path"
 *   - "https://ghe-hostname/owner/repo"
 *   - "https://ghe-hostname/owner/repo/tree/branch/path/to/skill"
 */
public class GitHubEnterpriseProvider implements HostProvider {

    // Matches ghe:hostname/owner/repo[/tree/branch[/path]]
    // owner and repo must start with alphanumeric to match GitHub naming rules
    private static final Pattern GHE_PREFIX = Pattern.compile(
        "^ghe:([^/]+)/([a-zA-Z0-9][a-zA-Z0-9_.-]*)/([a-zA-Z0-9][a-zA-Z0-9_.-]*)(?:/tree/([^/]+)(?:/(.+))?)?$"
    );

    // Matches https://hostname/owner/repo[/tree/branch[/path]]
    // owner and repo must start with alphanumeric to exclude .well-known style paths
    private static final Pattern GHE_URL = Pattern.compile(
        "^https?://([^/]+)/([a-zA-Z0-9][a-zA-Z0-9_.-]*)/([a-zA-Z0-9][a-zA-Z0-9_.-]*)(?:/tree/([^/]+)(?:/(.+))?)?$"
    );

    // Hosts handled by other dedicated providers
    static final Set<String> EXCLUDED_HOSTS = Set.of(
        "github.com", "gitlab.com", "raw.githubusercontent.com"
    );

    private final SkillDiscovery discovery = new SkillDiscovery();

    @Override
    public boolean matches(String source) {
        if (source == null) return false;
        String s = stripFragment(source.trim());
        if (GHE_PREFIX.matcher(s).matches()) return true;
        Matcher m = GHE_URL.matcher(s);
        if (m.matches()) {
            String host = m.group(1).toLowerCase();
            return !EXCLUDED_HOSTS.contains(host);
        }
        return false;
    }

    @Override
    public List<Skill> fetchSkills(String source, Path tempDir) throws ProviderException {
        ParsedGHESource parsed = parse(source);
        String url = "https://" + parsed.host + "/" + parsed.owner + "/" + parsed.repo + ".git";
        try {
            Path cloneDir = GitUtils.cloneToTemp(url, parsed.branch);
            try {
                Path searchRoot = parsed.skillPath != null ? cloneDir.resolve(parsed.skillPath) : cloneDir;
                List<Skill> skills = discovery.discover(searchRoot, null, false);
                copyDirectory(cloneDir, tempDir);
                return skills;
            } finally {
                GitUtils.deleteTempDir(cloneDir.toFile());
            }
        } catch (GitUtils.GitCloneException | IOException e) {
            throw new ProviderException("Failed to fetch from GitHub Enterprise: " + e.getMessage(), e);
        }
    }

    @Override
    public String getLatestHash(String source, String skillPath) {
        return null; // GHE hash tracking not yet implemented
    }

    @Override
    public String canonicalUrl(String source) {
        ParsedGHESource parsed = parse(source);
        return "https://" + parsed.host + "/" + parsed.owner + "/" + parsed.repo;
    }

    @Override
    public String getSourceType() { return "github-enterprise"; }

    public ParsedGHESource parse(String source) {
        source = stripFragment(source.trim());
        Matcher m = GHE_PREFIX.matcher(source);
        if (m.matches()) {
            String repo = m.group(3).replaceAll("\\.git$", "");
            return new ParsedGHESource(m.group(1), m.group(2), repo, m.group(4), m.group(5));
        }
        m = GHE_URL.matcher(source);
        if (m.matches()) {
            String repo = m.group(3).replaceAll("\\.git$", "");
            return new ParsedGHESource(m.group(1), m.group(2), repo, m.group(4), m.group(5));
        }
        throw new IllegalArgumentException("Not a valid GitHub Enterprise source: " + source);
    }

    private static String stripFragment(String source) {
        int hash = source.indexOf('#');
        return hash >= 0 ? source.substring(0, hash) : source;
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        java.nio.file.Files.walkFileTree(source, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                java.nio.file.Files.createDirectories(target.resolve(source.relativize(dir)));
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                java.nio.file.Files.copy(file, target.resolve(source.relativize(file)),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    public static class ParsedGHESource {
        public final String host;
        public final String owner;
        public final String repo;
        public final String branch;
        public final String skillPath;

        public ParsedGHESource(String host, String owner, String repo, String branch, String skillPath) {
            this.host = host;
            this.owner = owner;
            this.repo = repo;
            this.branch = branch;
            this.skillPath = skillPath;
        }
    }
}
