package sh.skills.model;

/**
 * Parsed representation of a skill source string.
 * Mirrors ParsedSource from the TypeScript source.
 */
public class ParsedSource {
    private final String type;       // "github", "gitlab", "git", "local", "well-known"
    private final String url;        // Canonical URL or resolved path
    private final String ref;        // Branch/tag ref (from #fragment or tree URL)
    private final String subpath;    // Subpath within the repo
    private final String skillFilter; // Specific skill name filter (from @skill syntax)
    private final String localPath;  // Resolved local path (only for type="local")

    private ParsedSource(Builder b) {
        this.type = b.type;
        this.url = b.url;
        this.ref = b.ref;
        this.subpath = b.subpath;
        this.skillFilter = b.skillFilter;
        this.localPath = b.localPath;
    }

    public String getType() { return type; }
    public String getUrl() { return url; }
    public String getRef() { return ref; }
    public String getSubpath() { return subpath; }
    public String getSkillFilter() { return skillFilter; }
    public String getLocalPath() { return localPath; }

    public static Builder builder(String type, String url) {
        return new Builder(type, url);
    }

    public static class Builder {
        private final String type;
        private final String url;
        private String ref;
        private String subpath;
        private String skillFilter;
        private String localPath;

        private Builder(String type, String url) {
            this.type = type;
            this.url = url;
        }

        public Builder ref(String ref) { this.ref = ref; return this; }
        public Builder subpath(String subpath) { this.subpath = subpath; return this; }
        public Builder skillFilter(String skillFilter) { this.skillFilter = skillFilter; return this; }
        public Builder localPath(String localPath) { this.localPath = localPath; return this; }

        public ParsedSource build() { return new ParsedSource(this); }
    }
}
