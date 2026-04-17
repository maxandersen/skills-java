package sh.skills.tui;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Flex;
import dev.tamboui.style.Color;
import dev.tamboui.toolkit.app.InlineApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.InlineTuiConfig;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * TamboUI-based interactive prompts matching @clack/prompts behavior.
 * Provides select, multiselect, and spinner for the add command.
 */
public final class Prompts {

    private static final Color CYAN = Color.rgb(0, 180, 216);
    private static final Color GREEN = Color.rgb(46, 204, 113);
    private static final Color DIM = Color.rgb(127, 140, 141);
    private static final Color YELLOW = Color.rgb(241, 196, 15);

    private Prompts() {}

    // ─── Confirm (yes/no) ───

    /**
     * Show a yes/no confirmation prompt. Returns true for yes, false for no, null if cancelled.
     */
    public static Boolean confirm(String message, boolean defaultYes) {
        return select(message, List.of(
            new SelectOption<>(true, "Yes"),
            new SelectOption<>(false, "No")
        ));
    }

    // ─── Text input ───

    /**
     * Show a single-line text input prompt. Returns input or null if cancelled.
     */
    public static String promptText(String message, String placeholder) {
        if (!isTTY()) return null;
        var app = new TextPromptApp(message, placeholder);
        try {
            app.run();
        } catch (Exception e) {
            return null;
        }
        return app.getResult();
    }

    // ─── Select (single choice) ───

    public static <T> T select(String message, List<SelectOption<T>> options) {
        if (options.isEmpty()) return null;
        if (!isTTY()) {
            // Fallback: return first option
            return options.get(0).value;
        }
        var app = new SelectApp<>(message, options);
        try {
            app.run();
        } catch (Exception e) {
            return null;
        }
        return app.getResult();
    }

    // ─── Multiselect ───

    public static <T> List<T> multiselect(String message, List<SelectOption<T>> options) {
        return multiselect(message, options, Set.of());
    }

    public static <T> List<T> multiselect(String message, List<SelectOption<T>> options, Set<Integer> preSelected) {
        if (options.isEmpty()) return List.of();
        if (!isTTY()) {
            // Fallback: return all
            List<T> all = new ArrayList<>();
            for (var opt : options) all.add(opt.value);
            return all;
        }
        var app = new MultiselectApp<>(message, options, preSelected);
        try {
            app.run();
        } catch (Exception e) {
            return null;
        }
        return app.getResult();
    }

    // ─── Spinner ───

    /**
     * Run an action with a spinner. Returns the action's result.
     */
    public static <T> T spin(String message, java.util.concurrent.Callable<T> action) {
        if (!isTTY()) {
            // No spinner in non-TTY
            try {
                return action.call();
            } catch (Exception e) {
                return null;
            }
        }
        var app = new SpinnerApp<>(message, action);
        try {
            app.run();
        } catch (Exception e) {
            return null;
        }
        return app.getResult();
    }

    private static boolean isTTY() {
        return System.console() != null;
    }

    // ─── Data types ───

    public record SelectOption<T>(T value, String label, String hint) {
        public SelectOption(T value, String label) {
            this(value, label, null);
        }
    }

    // ─── Select App ───

    private static class SelectApp<T> extends InlineApp {
        private final String message;
        private final List<SelectOption<T>> options;
        private int cursor = 0;
        private T result;
        private boolean cancelled;

        SelectApp(String message, List<SelectOption<T>> options) {
            this.message = message;
            this.options = options;
        }

        @Override protected int height() { return options.size() + 3; }

        @Override
        protected InlineTuiConfig configure(int height) {
            return InlineTuiConfig.builder(height)
                    .tickRate(Duration.ofMillis(100))
                    .clearOnClose(true)
                    .build();
        }

        @Override
        protected Element render() {
            List<Element> rows = new ArrayList<>();
            rows.add(row(
                text("◆ ").fg(CYAN).fit(),
                text(message).bold().fit()
            ).flex(Flex.START));

            for (int i = 0; i < options.size(); i++) {
                var opt = options.get(i);
                boolean sel = i == cursor;
                String bullet = sel ? "●" : "○";
                var label = text((sel ? " " : " ") + bullet + " " + opt.label);
                if (sel) label = label.bold();
                else label = label.fg(DIM);

                if (opt.hint != null && !opt.hint.isEmpty()) {
                    rows.add(row(
                        label.fit(),
                        text("  " + opt.hint).fg(DIM).fit()
                    ).flex(Flex.START));
                } else {
                    rows.add(label);
                }
            }

            rows.add(text(""));
            rows.add(text("↑/↓ navigate · enter select · esc cancel").fg(DIM));

            return column(rows.toArray(new Element[0]))
                    .focusable()
                    .onKeyEvent(this::handleKey);
        }

        private EventResult handleKey(KeyEvent event) {
            if (event.code() == KeyCode.ESCAPE || event.isCtrlC()) {
                cancelled = true;
                quit();
                return EventResult.HANDLED;
            }
            if (event.code() == KeyCode.UP) {
                cursor = (cursor - 1 + options.size()) % options.size();
                return EventResult.HANDLED;
            }
            if (event.code() == KeyCode.DOWN) {
                cursor = (cursor + 1) % options.size();
                return EventResult.HANDLED;
            }
            if (event.isConfirm()) {
                result = options.get(cursor).value;
                quit();
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        }

        T getResult() { return cancelled ? null : result; }
    }

    // ─── Multiselect App ───

    private static class MultiselectApp<T> extends InlineApp {
        private final String message;
        private final List<SelectOption<T>> options;
        private final boolean[] checked;
        private int cursor = 0;
        private List<T> result;
        private boolean cancelled;

        MultiselectApp(String message, List<SelectOption<T>> options, Set<Integer> preSelected) {
            this.message = message;
            this.options = options;
            this.checked = new boolean[options.size()];
            for (int i : preSelected) {
                if (i >= 0 && i < checked.length) checked[i] = true;
            }
        }

        @Override protected int height() { return Math.min(options.size() + 4, 20); }

        @Override
        protected InlineTuiConfig configure(int height) {
            return InlineTuiConfig.builder(height)
                    .tickRate(Duration.ofMillis(100))
                    .clearOnClose(true)
                    .build();
        }

        @Override
        protected Element render() {
            List<Element> rows = new ArrayList<>();
            rows.add(row(
                text("◆ ").fg(CYAN).fit(),
                text(message).bold().fit()
            ).flex(Flex.START));

            int maxVisible = Math.min(options.size(), height() - 4);
            int scrollOffset = Math.max(0, cursor - maxVisible + 1);

            for (int i = scrollOffset; i < Math.min(scrollOffset + maxVisible, options.size()); i++) {
                var opt = options.get(i);
                boolean sel = i == cursor;
                String box = checked[i] ? "◼" : "◻";
                Color boxColor = checked[i] ? GREEN : DIM;

                var labelEl = text(opt.label);
                if (sel) labelEl = labelEl.bold();
                var line = row(
                    text(sel ? " " : " ").fit(),
                    text(box + " ").fg(boxColor).fit(),
                    labelEl.fit(),
                    text(opt.hint != null ? "  " + opt.hint : "").fg(DIM).fit()
                ).flex(Flex.START);

                rows.add(line);
            }

            rows.add(text(""));
            rows.add(text("↑/↓ navigate · space toggle · a all · enter confirm · esc cancel").fg(DIM));

            return column(rows.toArray(new Element[0]))
                    .focusable()
                    .onKeyEvent(this::handleKey);
        }

        private EventResult handleKey(KeyEvent event) {
            if (event.code() == KeyCode.ESCAPE || event.isCtrlC()) {
                cancelled = true;
                quit();
                return EventResult.HANDLED;
            }
            if (event.code() == KeyCode.UP) {
                cursor = (cursor - 1 + options.size()) % options.size();
                return EventResult.HANDLED;
            }
            if (event.code() == KeyCode.DOWN) {
                cursor = (cursor + 1) % options.size();
                return EventResult.HANDLED;
            }
            if (event.character() == ' ') {
                checked[cursor] = !checked[cursor];
                return EventResult.HANDLED;
            }
            if (event.character() == 'a' || event.character() == 'A') {
                boolean allChecked = true;
                for (boolean c : checked) if (!c) { allChecked = false; break; }
                Arrays.fill(checked, !allChecked);
                return EventResult.HANDLED;
            }
            if (event.isConfirm()) {
                result = new ArrayList<>();
                for (int i = 0; i < options.size(); i++) {
                    if (checked[i]) result.add(options.get(i).value);
                }
                if (result.isEmpty()) {
                    // Must select at least one
                    return EventResult.HANDLED;
                }
                quit();
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        }

        List<T> getResult() { return cancelled ? null : result; }
    }

    // ─── Spinner App ───

    private static class SpinnerApp<T> extends InlineApp {
        private static final String[] FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
        private final String message;
        private final java.util.concurrent.Callable<T> action;
        private int frame = 0;
        private T result;
        private String doneMessage;
        private boolean done;

        SpinnerApp(String message, java.util.concurrent.Callable<T> action) {
            this.message = message;
            this.action = action;
        }

        @Override protected int height() { return 1; }

        @Override
        protected InlineTuiConfig configure(int height) {
            return InlineTuiConfig.builder(height)
                    .tickRate(Duration.ofMillis(80))
                    .clearOnClose(true)
                    .build();
        }

        @Override
        protected void onStart() {
            // Tick animation
            runner().scheduleRepeating(() -> {
                runner().runOnRenderThread(() -> frame = (frame + 1) % FRAMES.length);
            }, Duration.ofMillis(80));

            // Run action in background
            Thread.startVirtualThread(() -> {
                try {
                    result = action.call();
                } catch (Exception e) {
                    result = null;
                }
                if (runner() != null) {
                    runner().runOnRenderThread(() -> {
                        done = true;
                        quit();
                    });
                }
            });
        }

        @Override
        protected Element render() {
            if (done) {
                return row(
                    text("◇ ").fg(GREEN).fit(),
                    text(doneMessage != null ? doneMessage : message).fit()
                ).flex(Flex.START);
            }
            return row(
                text(FRAMES[frame] + " ").fg(CYAN).fit(),
                text(message).fit()
            ).flex(Flex.START);
        }

        T getResult() { return result; }
    }

    // ─── Text Prompt App ───

    private static class TextPromptApp extends InlineApp {
        private final String message;
        private final TextInputState inputState = new TextInputState();
        private String result;
        private boolean cancelled;

        TextPromptApp(String message, String placeholder) {
            this.message = message;
        }

        @Override protected int height() { return 3; }

        @Override
        protected InlineTuiConfig configure(int height) {
            return InlineTuiConfig.builder(height)
                    .tickRate(Duration.ofMillis(100))
                    .clearOnClose(true)
                    .build();
        }

        @Override
        protected Element render() {
            return column(
                row(
                    text("◆ ").fg(CYAN).fit(),
                    text(message).bold().fit()
                ).flex(Flex.START),
                row(
                    text("  ").fit(),
                    textInput(inputState).constraint(Constraint.fill(1))
                ).flex(Flex.START),
                text("enter confirm · esc cancel").fg(DIM)
            ).focusable().onKeyEvent(this::handleKey);
        }

        private EventResult handleKey(KeyEvent event) {
            if (event.code() == KeyCode.ESCAPE || event.isCtrlC()) {
                cancelled = true;
                quit();
                return EventResult.HANDLED;
            }
            if (event.isConfirm() && !inputState.text().isEmpty()) {
                result = inputState.text();
                quit();
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        }

        String getResult() { return cancelled ? null : result; }
    }
}
