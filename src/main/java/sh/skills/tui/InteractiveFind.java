package sh.skills.tui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Flex;
import dev.tamboui.style.Color;
import dev.tamboui.toolkit.app.InlineApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.InlineTuiConfig;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;
import sh.skills.commands.FindCommand;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * fzf-style interactive skill search using TamboUI inline mode.
 * Matches upstream find.ts interactive behavior.
 */
public class InteractiveFind extends InlineApp {

    private static final String SKILLS_API = "https://skills.sh/api/search";
    private static final Color CYAN = Color.rgb(0, 180, 216);
    private static final Color DIM_COLOR = Color.rgb(127, 140, 141);
    private static final int MAX_VISIBLE = 8;

    private final TextInputState inputState = new TextInputState();
    private final List<SkillResult> results = new ArrayList<>();
    private int selectedIndex = 0;
    private boolean loading = false;
    private String lastSearched = "";
    private long lastKeyTime = 0;
    private final AtomicBoolean searchPending = new AtomicBoolean(false);
    private final String initialQuery;
    private SkillResult selectedResult;
    private boolean cancelled = false;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public InteractiveFind(String initialQuery) {
        this.initialQuery = initialQuery != null ? initialQuery : "";
    }

    @Override
    protected int height() {
        return MAX_VISIBLE + 4; // input + blank + results + footer
    }

    @Override
    protected InlineTuiConfig configure(int height) {
        return InlineTuiConfig.builder(height)
                .tickRate(Duration.ofMillis(100))
                .clearOnClose(true)
                .build();
    }

    @Override
    protected void onStart() {
        if (!initialQuery.isEmpty()) {
            inputState.insert(initialQuery);
            triggerSearch(initialQuery);
        }
        // Poll for debounced searches by checking inputState text
        runner().scheduleRepeating(() -> {
            runner().runOnRenderThread(() -> {
                String current = inputState.text();
                if (!current.equals(lastCheckedText)) {
                    lastCheckedText = current;
                    lastKeyTime = System.currentTimeMillis();
                    searchPending.set(true);
                }
                checkSearch();
            });
        }, Duration.ofMillis(100));
    }
    private String lastCheckedText = "";

    @Override
    protected Element render() {
        String query = inputState.text();
        List<Element> rows = new ArrayList<>();

        // Search input line
        rows.add(row(
            text("Search skills: ").fg(DIM_COLOR).fit(),
            textInput(inputState)
                .constraint(Constraint.fill(1))
        ).flex(Flex.START));

        rows.add(text("")); // blank line

        // Results area
        if (query.length() < 2) {
            rows.add(text("Start typing to search (min 2 chars)").fg(DIM_COLOR));
            for (int i = 0; i < MAX_VISIBLE - 1; i++) rows.add(text(""));
        } else if (results.isEmpty() && loading) {
            rows.add(text("Searching...").fg(DIM_COLOR));
            for (int i = 0; i < MAX_VISIBLE - 1; i++) rows.add(text(""));
        } else if (results.isEmpty()) {
            rows.add(text("No skills found").fg(DIM_COLOR));
            for (int i = 0; i < MAX_VISIBLE - 1; i++) rows.add(text(""));
        } else {
            int visible = Math.min(results.size(), MAX_VISIBLE);
            for (int i = 0; i < MAX_VISIBLE; i++) {
                if (i < visible) {
                    SkillResult skill = results.get(i);
                    boolean isSelected = i == selectedIndex;
                    String arrow = isSelected ? ">" : " ";
                    String installs = FindCommand.formatInstalls(skill.installs);

                    var nameEl = text(skill.name);
                    var arrowEl = text("  " + arrow + " ");
                    if (isSelected) {
                        nameEl = nameEl.bold();
                        arrowEl = arrowEl.bold();
                    }
                    rows.add(row(
                        arrowEl.fit(),
                        nameEl.fit(),
                        text(" ").fit(),
                        text(skill.source).fg(DIM_COLOR).fit(),
                        text(installs.isEmpty() ? "" : " " + installs).fg(CYAN).fit()
                    ).flex(Flex.START));
                } else {
                    rows.add(text(""));
                }
            }
        }

        // Footer
        rows.add(text(""));
        rows.add(text("up/down navigate | enter select | esc cancel").fg(DIM_COLOR));

        return column(rows.toArray(new Element[0]))
                .focusable()
                .onKeyEvent(this::handleKey);
    }

    private void checkSearch() {
        if (!searchPending.get()) return;
        String query = inputState.text();
        // Debounce: wait at least 150ms after last keystroke
        long elapsed = System.currentTimeMillis() - lastKeyTime;
        int debounceMs = Math.max(150, 350 - query.length() * 50);
        if (elapsed >= debounceMs) {
            searchPending.set(false);
            if (query.length() >= 2 && !query.equals(lastSearched)) {
                triggerSearch(query);
            } else if (query.length() < 2) {
                results.clear();
                selectedIndex = 0;
                lastSearched = "";
            }
        }
    }

    private void triggerSearch(String query) {
        loading = true;
        lastSearched = query;
        // Run search in background
        Thread.startVirtualThread(() -> {
            try {
                List<SkillResult> searchResults = searchApi(query);
                runner().runOnRenderThread(() -> {
                    results.clear();
                    results.addAll(searchResults);
                    selectedIndex = 0;
                    loading = false;
                });
            } catch (Exception e) {
                runner().runOnRenderThread(() -> {
                    results.clear();
                    loading = false;
                });
            }
        });
    }

    private List<SkillResult> searchApi(String query) throws Exception {
        String url = SKILLS_API + "?q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&limit=10";
        HttpResponse<String> response = httpClient.send(
            HttpRequest.newBuilder().uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() != 200) return List.of();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());
        JsonNode skills = root.isArray() ? root : root.path("skills");

        List<SkillResult> list = new ArrayList<>();
        if (skills.isArray()) {
            for (JsonNode skill : skills) {
                list.add(new SkillResult(
                    skill.path("name").asText(""),
                    skill.path("id").asText(skill.path("slug").asText("")),
                    skill.path("source").asText(""),
                    skill.path("installs").asInt(0)
                ));
            }
            list.sort((a, b) -> b.installs - a.installs);
        }
        return list;
    }

    private EventResult handleKey(KeyEvent event) {
        if (event.code() == KeyCode.ESCAPE) {
            cancelled = true;
            quit();
            return EventResult.HANDLED;
        }
        if (event.code() == KeyCode.UP) {
            selectedIndex = Math.max(0, selectedIndex - 1);
            return EventResult.HANDLED;
        }
        if (event.code() == KeyCode.DOWN) {
            selectedIndex = Math.min(Math.max(0, results.size() - 1), selectedIndex + 1);
            return EventResult.HANDLED;
        }
        if (event.isConfirm() && !results.isEmpty() && selectedIndex < results.size()) {
            selectedResult = results.get(selectedIndex);
            quit();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    /** Get the selected result, or null if cancelled */
    public SkillResult getSelectedResult() {
        return cancelled ? null : selectedResult;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public record SkillResult(String name, String slug, String source, int installs) {
        /** Returns owner/repo@skill-name format for add command */
        public String addSource() {
            String pkg = source.isEmpty() ? slug : source;
            return pkg + "@" + name;
        }
    }
}
