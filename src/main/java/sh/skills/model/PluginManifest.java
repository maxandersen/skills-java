package sh.skills.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the .claude-plugin/marketplace.json or plugin.json manifest.
 * Mirrors the plugin manifest types from the TypeScript source.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PluginManifest {

    private Metadata metadata;
    private List<Plugin> plugins;

    public Metadata getMetadata() { return metadata; }
    public void setMetadata(Metadata metadata) { this.metadata = metadata; }

    public List<Plugin> getPlugins() { return plugins; }
    public void setPlugins(List<Plugin> plugins) { this.plugins = plugins; }

    /**
     * Extracts skill paths from the plugin manifest, organized by plugin name.
     * Resolves relative paths based on pluginRoot and plugin source.
     *
     * @param manifest the plugin manifest to process
     * @return a map from plugin name to list of resolved skill paths
     */
    public static Map<String, List<String>> getSkillPaths(PluginManifest manifest) {
        Map<String, List<String>> result = new HashMap<>();

        if (manifest.plugins == null) {
            return result;
        }

        String pluginRoot = manifest.metadata != null && manifest.metadata.pluginRoot != null
            ? manifest.metadata.pluginRoot
            : ".";

        for (Plugin plugin : manifest.plugins) {
            List<String> skillPaths = new ArrayList<>();
            String source = plugin.source != null ? plugin.source : plugin.name;

            if (plugin.skills != null) {
                for (String skillPath : plugin.skills) {
                    // Remove "./" prefix from skill path if present
                    String cleanedSkillPath = skillPath.startsWith("./")
                        ? skillPath.substring(2)
                        : skillPath;

                    // Construct the full path: pluginRoot/source/skillPath
                    String fullPath;
                    if (pluginRoot.equals(".")) {
                        fullPath = "./" + source + "/" + cleanedSkillPath;
                    } else {
                        fullPath = pluginRoot + "/" + source + "/" + cleanedSkillPath;
                    }
                    skillPaths.add(fullPath);
                }
            }

            result.put(plugin.name, skillPaths);
        }

        return result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        private String pluginRoot;

        public String getPluginRoot() { return pluginRoot; }
        public void setPluginRoot(String pluginRoot) { this.pluginRoot = pluginRoot; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Plugin {
        private String name;
        private String source;
        private List<String> skills;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public List<String> getSkills() { return skills; }
        public void setSkills(List<String> skills) { this.skills = skills; }
    }
}
