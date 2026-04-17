package sh.skills.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import sh.skills.commands.AddCommand;
import sh.skills.tui.InteractiveFind;
import sh.skills.util.Console;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Implements `skills find [query]`.
 * Searches for skills via the skills.sh API.
 * Mirrors src/find.ts from the TypeScript source.
 */
@Command(
    name = "find",
    aliases = {"search", "f", "s"},
    description = "Search for skills in the skills ecosystem",
    mixinStandardHelpOptions = true
)
public class FindCommand implements Callable<Integer> {

    private static final String SKILLS_API = "https://skills.sh/api/search";
    private static final String SKILLS_BASE = "https://skills.sh";

    /**
     * Format install count for display.
     * Mirrors formatInstalls() from upstream find.ts.
     */
    public static String formatInstalls(int count) {
        if (count <= 0) return "";
        if (count >= 1_000_000) {
            String val = String.format(Locale.US, "%.1f", count / 1_000_000.0).replaceAll("\\.0$", "");
            return val + "M installs";
        }
        if (count >= 1_000) {
            String val = String.format(Locale.US, "%.1f", count / 1_000.0).replaceAll("\\.0$", "");
            return val + "K installs";
        }
        return count + (count == 1 ? " install" : " installs");
    }

    @Parameters(arity = "0..*", description = "Search query")
    private List<String> queryWords = new ArrayList<>();

    @Option(names = {"--json"}, description = "Output as JSON")
    private boolean json;

    @Option(names = {"-n", "--limit"}, description = "Maximum number of results (default: 6)", defaultValue = "6")
    private int limit;

    @Override
    public Integer call() {
        try {
            return execute();
        } catch (Exception e) {
            Console.error(e.getMessage());
            return 1;
        }
    }

    private int execute() throws Exception {
        Console.showLogo();
        Console.log("");
        String query = queryWords.isEmpty() ? null : String.join(" ", queryWords);

        // Interactive mode when no query and TTY is available
        if (query == null && System.console() != null && !json) {
            return executeInteractive();
        }
        String url = SKILLS_API;
        if (query != null && !query.isEmpty()) {
            url += "?q=" + java.net.URLEncoder.encode(query, "UTF-8");
        }
        url += (url.contains("?") ? "&" : "?") + "limit=" + limit;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(
                HttpRequest.newBuilder().uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
        } catch (Exception e) {
            Console.error("Could not reach skills.sh: " + e.getMessage());
            Console.log("Check your internet connection or try browsing https://skills.sh");
            return 1;
        }

        if (response.statusCode() != 200) {
            Console.error("skills.sh API returned " + response.statusCode());
            return 1;
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());

        if (json) {
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            return 0;
        }

        JsonNode skills = root.isArray() ? root : root.path("skills");

        // Sort by install count descending (upstream #546)
        if (skills.isArray() && skills.size() > 1) {
            List<JsonNode> sorted = new ArrayList<>();
            skills.forEach(sorted::add);
            sorted.sort((a, b) -> b.path("installs").asInt(0) - a.path("installs").asInt(0));
            skills = mapper.valueToTree(sorted);
        }

        if (!skills.isArray() || skills.size() == 0) {
            Console.log(Console.dim("No skills found" + (query != null ? " for \"" + query + "\"" : "")) + ".");
            return 0;
        }

        Console.log(Console.dim("Install with") + " skills add <owner/repo@skill>");
        Console.log("");

        // Show up to 6 results in non-interactive mode (matching upstream)
        int shown = 0;
        for (JsonNode skill : skills) {
            if (shown >= limit) break;
            String name = skill.path("name").asText("unknown");
            String slug = skill.path("id").asText(skill.path("slug").asText(""));
            String source = skill.path("source").asText("");
            int installs = skill.path("installs").asInt(0);

            // Format: source@skill-name <installs>
            String pkg = !source.isEmpty() ? source : slug;
            String installsStr = formatInstalls(installs);
            Console.log(pkg + "@" + name
                + (!installsStr.isEmpty() ? " " + Console.cyan(installsStr) : ""));
            // URL line
            if (!slug.isEmpty()) {
                Console.log(Console.dim("\u2514 " + SKILLS_BASE + "/" + slug));
            }
            Console.log("");
            shown++;
        }
        return 0;
    }

    /**
     * Interactive fzf-style search using TamboUI.
     * Matches upstream interactive find behavior.
     */
    private int executeInteractive() throws Exception {
        InteractiveFind finder = new InteractiveFind(null);
        finder.run();

        InteractiveFind.SkillResult selected = finder.getSelectedResult();
        if (selected == null) {
            Console.log(Console.dim("Search cancelled"));
            Console.log("");
            return 0;
        }

        // Show what was selected and install it
        String pkg = selected.source().isEmpty() ? selected.slug() : selected.source();
        Console.log("");
        Console.log("Installing " + Console.bold(selected.name())
            + " from " + Console.dim(pkg) + "...");
        Console.log("");

        // Run add command
        String addSource = selected.addSource();
        picocli.CommandLine addCmd = new picocli.CommandLine(new AddCommand());
        return addCmd.execute(addSource);
    }
}
