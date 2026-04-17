package sh.skills.blob;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import sh.skills.util.Console;
import sh.skills.util.FrontmatterParser;
import sh.skills.model.Skill;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Blob-based skill download utilities.
 * Ports src/blob.ts from the TypeScript source.
 *
 * Enables fast skill installation by fetching pre-built skill snapshots
 * from the skills.sh download API instead of cloning git repos.
 *
 * Flow:
 *   1. GitHub Trees API → discover SKILL.md locations
 *   2. raw.githubusercontent.com → fetch frontmatter to get skill names
 *   3. skills.sh/api/download → fetch full file contents from cached blob
 */
public class BlobDownloader {

    private static final String DOWNLOAD_BASE_URL =
        System.getenv("SKILLS_DOWNLOAD_URL") != null
            ? System.getenv("SKILLS_DOWNLOAD_URL")
            : "https://skills.sh";

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);

    private static final ObjectMapper mapper = new ObjectMapper();

    /** Known directories where SKILL.md files are commonly found */
    private static final List<String> PRIORITY_PREFIXES = List.of(
        "", "skills/", "skills/.curated/", "skills/.experimental/", "skills/.system/",
        ".agents/skills/", ".claude/skills/", ".cline/skills/", ".codebuddy/skills/",
        ".codex/skills/", ".commandcode/skills/", ".continue/skills/", ".github/skills/",
        ".goose/skills/", ".iflow/skills/", ".junie/skills/", ".kilocode/skills/",
        ".kiro/skills/", ".mux/skills/", ".neovate/skills/", ".opencode/skills/",
        ".openhands/skills/", ".pi/skills/", ".qoder/skills/", ".roo/skills/",
        ".trae/skills/", ".windsurf/skills/", ".zencoder/skills/"
    );

    // ─── Slug computation ───

    /**
     * Convert a skill name to a URL-safe slug.
     * Must match the server-side toSkillSlug() exactly.
     */
    public static String toSkillSlug(String name) {
        return name.toLowerCase()
            .replaceAll("[\\s_]+", "-")
            .replaceAll("[^a-z0-9-]", "")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }

    // ─── GitHub Trees API ───

    /**
     * Fetch the full recursive tree for a GitHub repo.
     * Tries branches in order: ref (if specified), then HEAD, main, master.
     */
    public static RepoTree fetchRepoTree(String ownerRepo, String ref, String token) {
        List<String> branches = ref != null ? List.of(ref) : List.of("HEAD", "main", "master");

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(FETCH_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        for (String branch : branches) {
            try {
                String url = "https://api.github.com/repos/" + ownerRepo + "/git/trees/"
                    + URLEncoder.encode(branch, StandardCharsets.UTF_8) + "?recursive=1";

                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "skills-cli")
                    .timeout(FETCH_TIMEOUT);

                if (token != null && !token.isEmpty()) {
                    reqBuilder.header("Authorization", "Bearer " + token);
                }

                HttpResponse<String> response = client.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) continue;

                JsonNode data = mapper.readTree(response.body());
                String sha = data.path("sha").asText();
                List<TreeEntry> entries = new ArrayList<>();
                for (JsonNode entry : data.path("tree")) {
                    entries.add(new TreeEntry(
                        entry.path("path").asText(),
                        entry.path("type").asText(),
                        entry.path("sha").asText(),
                        entry.has("size") ? entry.path("size").asInt() : null
                    ));
                }

                return new RepoTree(sha, branch, entries);
            } catch (Exception e) {
                continue;
            }
        }
        return null;
    }

    /**
     * Extract the folder hash (tree SHA) for a specific skill path from a repo tree.
     */
    public static String getSkillFolderHashFromTree(RepoTree tree, String skillPath) {
        String folderPath = skillPath.replace('\\', '/');

        if (folderPath.endsWith("/SKILL.md")) {
            folderPath = folderPath.substring(0, folderPath.length() - 9);
        } else if (folderPath.endsWith("SKILL.md")) {
            folderPath = folderPath.substring(0, folderPath.length() - 8);
        }
        if (folderPath.endsWith("/")) {
            folderPath = folderPath.substring(0, folderPath.length() - 1);
        }

        if (folderPath.isEmpty()) {
            return tree.sha();
        }

        String finalFolderPath = folderPath;
        return tree.tree().stream()
            .filter(e -> "tree".equals(e.type()) && e.path().equals(finalFolderPath))
            .findFirst()
            .map(TreeEntry::sha)
            .orElse(null);
    }

    // ─── Skill discovery from tree ───

    /**
     * Find all SKILL.md file paths in a repo tree.
     * Applies priority directory logic matching discoverSkills().
     */
    public static List<String> findSkillMdPaths(RepoTree tree, String subpath) {
        List<String> allSkillMds = tree.tree().stream()
            .filter(e -> "blob".equals(e.type()) && e.path().endsWith("SKILL.md"))
            .map(TreeEntry::path)
            .toList();

        String prefix = subpath != null
            ? (subpath.endsWith("/") ? subpath : subpath + "/")
            : "";

        List<String> filtered;
        if (!prefix.isEmpty()) {
            filtered = allSkillMds.stream()
                .filter(p -> p.startsWith(prefix) || p.equals(prefix + "SKILL.md"))
                .toList();
        } else {
            filtered = allSkillMds;
        }

        if (filtered.isEmpty()) return List.of();

        // Check priority directories first
        List<String> priorityResults = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String priorityPrefix : PRIORITY_PREFIXES) {
            String fullPrefix = prefix + priorityPrefix;
            for (String skillMd : filtered) {
                if (!skillMd.startsWith(fullPrefix)) continue;
                String rest = skillMd.substring(fullPrefix.length());

                // Direct SKILL.md in the priority dir
                if (rest.equals("SKILL.md")) {
                    if (seen.add(skillMd)) priorityResults.add(skillMd);
                    continue;
                }

                // SKILL.md one level deep (e.g., "skills/react-best-practices/SKILL.md")
                String[] parts = rest.split("/");
                if (parts.length == 2 && parts[1].equals("SKILL.md")) {
                    if (seen.add(skillMd)) priorityResults.add(skillMd);
                }
            }
        }

        if (!priorityResults.isEmpty()) return priorityResults;

        // Fallback: all SKILL.md files limited to 5 levels deep
        return filtered.stream()
            .filter(p -> p.split("/").length <= 6)
            .toList();
    }

    // ─── Fetching skill content ───

    /**
     * Fetch a single SKILL.md from raw.githubusercontent.com.
     */
    static String fetchSkillMdContent(HttpClient client, String ownerRepo,
                                       String branch, String skillMdPath) {
        try {
            String url = "https://raw.githubusercontent.com/" + ownerRepo + "/"
                + branch + "/" + skillMdPath;
            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder().uri(URI.create(url)).timeout(FETCH_TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString()
            );
            return response.statusCode() == 200 ? response.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetch a skill's full file contents from the skills.sh download API.
     */
    static SkillDownloadResponse fetchSkillDownload(HttpClient client, String source, String slug) {
        try {
            String[] parts = source.split("/");
            if (parts.length < 2) return null;
            String url = DOWNLOAD_BASE_URL + "/api/download/"
                + URLEncoder.encode(parts[0], StandardCharsets.UTF_8) + "/"
                + URLEncoder.encode(parts[1], StandardCharsets.UTF_8) + "/"
                + URLEncoder.encode(slug, StandardCharsets.UTF_8);

            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder().uri(URI.create(url)).timeout(FETCH_TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() != 200) return null;

            JsonNode data = mapper.readTree(response.body());
            List<SkillSnapshotFile> files = new ArrayList<>();
            for (JsonNode f : data.path("files")) {
                files.add(new SkillSnapshotFile(
                    f.path("path").asText(),
                    f.path("contents").asText()
                ));
            }
            return new SkillDownloadResponse(files, data.path("hash").asText());
        } catch (Exception e) {
            return null;
        }
    }

    // ─── Main entry point ───

    /**
     * Attempt to resolve skills from blob storage instead of cloning.
     *
     * Returns resolved BlobSkills + tree data on success, or null on failure
     * (caller should fall back to git clone).
     */
    public static BlobInstallResult tryBlobInstall(String ownerRepo, BlobInstallOptions options) {
        // 1. Fetch the full repo tree
        RepoTree tree = fetchRepoTree(ownerRepo, options.ref(), options.token());
        if (tree == null) {
            Console.log(Console.dim("  Could not fetch repo tree"));
            return null;
        }

        // 2. Discover SKILL.md paths in the tree
        List<String> skillMdPaths = new ArrayList<>(findSkillMdPaths(tree, options.subpath()));
        if (skillMdPaths.isEmpty()) {
            Console.log(Console.dim("  No SKILL.md files found in tree"));
            return null;
        }

        // 3. If skill filter is set, narrow down by folder name
        if (options.skillFilter() != null) {
            String filterSlug = toSkillSlug(options.skillFilter());
            List<String> filtered = skillMdPaths.stream()
                .filter(p -> {
                    String[] parts = p.split("/");
                    if (parts.length < 2) return false;
                    String folderName = parts[parts.length - 2];
                    return toSkillSlug(folderName).equals(filterSlug);
                })
                .toList();
            if (!filtered.isEmpty()) {
                skillMdPaths = new ArrayList<>(filtered);
            }
        }

        // 4. Fetch SKILL.md content from raw.githubusercontent.com
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(FETCH_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        // Fetch SKILL.md content in parallel
        List<CompletableFuture<ParsedSkillInfo>> futures = skillMdPaths.stream()
            .map(mdPath -> CompletableFuture.supplyAsync(() -> {
                String content = fetchSkillMdContent(client, ownerRepo, tree.branch(), mdPath);
                if (content == null) return null;

                FrontmatterParser.FrontmatterResult fm = FrontmatterParser.parseFrontmatter(content);
                String name = fm.data().get("name");
                String description = fm.data().get("description");
                if (name == null || description == null) return null;

                return new ParsedSkillInfo(mdPath, name, description, content, toSkillSlug(name));
            }))
            .toList();

        List<ParsedSkillInfo> parsedSkills = futures.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(ArrayList::new));

        if (parsedSkills.isEmpty()) {
            Console.log(Console.dim("  No valid skills found in SKILL.md files"));
            return null;
        }

        // Apply skill filter by name
        if (options.skillFilter() != null) {
            String filterSlug = toSkillSlug(options.skillFilter());
            List<ParsedSkillInfo> nameFiltered = parsedSkills.stream()
                .filter(s -> s.slug().equals(filterSlug))
                .toList();
            if (!nameFiltered.isEmpty()) {
                parsedSkills = new ArrayList<>(nameFiltered);
            }
            if (parsedSkills.isEmpty()) {
                Console.log(Console.dim("  No skills matched filter: " + options.skillFilter()));
                return null;
            }
        }

        // 5. Fetch full snapshots from skills.sh download API
        String source = ownerRepo.toLowerCase();
        List<BlobSkill> blobSkills = new ArrayList<>();

        for (ParsedSkillInfo skill : parsedSkills) {
            SkillDownloadResponse download = fetchSkillDownload(client, source, skill.slug());
            if (download == null) {
                // If ANY download failed, fall back to clone
                Console.log(Console.dim("  Download API unavailable for: " + skill.name()));
                return null;
            }

            String folderPath = skill.mdPath().endsWith("/SKILL.md")
                ? skill.mdPath().substring(0, skill.mdPath().length() - 9)
                : skill.mdPath().equals("SKILL.md")
                    ? ""
                    : skill.mdPath().substring(0, skill.mdPath().length() - (1 + "SKILL.md".length()));

            blobSkills.add(new BlobSkill(
                skill.name(), skill.description(), skill.content(),
                download.files(), download.hash(), skill.mdPath()
            ));
        }

        return new BlobInstallResult(blobSkills, tree);
    }

    // ─── Records ───

    public record TreeEntry(String path, String type, String sha, Integer size) {}

    public record RepoTree(String sha, String branch, List<TreeEntry> tree) {}

    public record SkillSnapshotFile(String path, String contents) {}

    public record SkillDownloadResponse(List<SkillSnapshotFile> files, String hash) {}

    public record BlobSkill(
        String name,
        String description,
        String rawContent,
        List<SkillSnapshotFile> files,
        String snapshotHash,
        String repoPath
    ) {}

    public record BlobInstallResult(List<BlobSkill> skills, RepoTree tree) {}

    public record BlobInstallOptions(
        String subpath,
        String skillFilter,
        String ref,
        String token,
        boolean includeInternal
    ) {
        public BlobInstallOptions() {
            this(null, null, null, null, false);
        }
    }

    private record ParsedSkillInfo(
        String mdPath, String name, String description,
        String content, String slug
    ) {}
}
