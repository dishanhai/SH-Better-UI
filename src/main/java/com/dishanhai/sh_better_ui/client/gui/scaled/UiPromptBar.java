// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

/** Reusable prompt composer. The host owns files, transcription and request execution. */
public final class UiPromptBar extends UiPromptBarPlatform {
    public static final int MIN_WIDTH = 152;
    public static final int MIN_HEIGHT = 124;
    private static final int MAX_DRAFT = 8192;
    private static final int TOOL = 20;
    private static final int GAP = 3;
    private static final int ROWS = 3;
    private static final int ROW = 18;
    private static final Pattern TOKEN = Pattern.compile("(?:^|\\s)([@/])([\\p{L}\\p{N}_-]*)\\z");
    private static final int[] ARROW = {8, 2, 14, 8, 10, 8, 10, 14, 6, 14, 6, 8, 2, 8};
    private static final int[] STOP = {8, 4, 12, 4, 12, 8, 12, 12, 4, 12, 4, 8, 4, 4};

    /** A stable key and localized presentation for a source, command or model. */
    public record Option(String key, Component label, Component detail, GraphicIcon icon) {
        public Option {
            Objects.requireNonNull(key);
            Objects.requireNonNull(label);
        }
    }

    public record Source(Option option, boolean attachment) {
        public Source { Objects.requireNonNull(option); }
    }

    /** The model is null and the effort is -1 when their option lists are empty. */
    public record Submission(String text, List<String> attachments, String model, int effort) {
        public Submission {
            attachments = List.copyOf(attachments);
        }
    }

    private enum Menu { NONE, SOURCES, COMMANDS, MODELS, EFFORT }
    record Token(char prefix, String query, int start) {}

    private final Font font;
    private final UiPreferences preferences;
    private final List<Source> sources;
    private final List<Option> commands;
    private final List<Option> models;
    private final List<Component> efforts;
    private final Consumer<Submission> onSend;
    private final Runnable onStop;
    private final Runnable onAttach;
    private final Runnable onDictate;
    private final MultiLineEditBox editor;
    private final UiButton add, modelButton, effortButton, mic, send, chip, previousChip;
    private final List<UiButton> rows = new ArrayList<>();
    private final UiSlider slider;
    private final List<String> attachments = new ArrayList<>();
    private List<Option> choices = List.of();
    private Menu menu = Menu.NONE;
    private Token token;
    private int model, effort, cursor, firstRow, chipIndex;
    private boolean busy, listening, programmaticEdit;
    private long lastEdit;
    private long morphStart;
    private float morphFrom;
    private int layoutX, layoutY, layoutHeight;
    private IntConsumer onEffortChange = ignored -> {};

    public UiPromptBar(Font font, Bounds area, Component placeholder, List<Source> sources, List<Option> commands,
                       List<Option> models, List<Component> efforts, UiPreferences preferences,
                       Consumer<Submission> onSend, Runnable onStop, Runnable onAttach, Runnable onDictate) {
        super(area, label("label"));
        if (area.width() < MIN_WIDTH || area.height() < MIN_HEIGHT) {
            throw new IllegalArgumentException("Prompt bar requires at least " + MIN_WIDTH + " x " + MIN_HEIGHT);
        }
        this.font = Objects.requireNonNull(font);
        this.preferences = Objects.requireNonNull(preferences);
        this.sources = List.copyOf(sources).stream().filter(source -> !source.attachment() || onAttach != null).toList();
        this.commands = List.copyOf(commands);
        this.models = List.copyOf(models);
        this.efforts = List.copyOf(efforts);
        this.onSend = Objects.requireNonNull(onSend);
        this.onStop = Objects.requireNonNull(onStop);
        this.onAttach = onAttach;
        this.onDictate = onDictate;
        effort = Math.max(0, (efforts.size() - 1) / 2);
        editor = editor(font, area.width() - 16, placeholder, getMessage());
        editor.setCharacterLimit(MAX_DRAFT);
        editor.setValueListener(this::draftChanged);
        add = button(label("sources"), GraphicIcon.ADD, () -> toggle(Menu.SOURCES));
        modelButton = button(Component.empty(), null, () -> toggle(Menu.MODELS));
        effortButton = button(Component.empty(), null, () -> toggle(Menu.EFFORT));
        mic = button(label("dictate"), GraphicIcon.MICROPHONE, onDictate == null ? () -> {} : onDictate);
        send = button(Component.empty(), null, this::activateFromKeyboard);
        chip = button(Component.empty(), null, this::removeChip);
        previousChip = button(label("next_attachment"), GraphicIcon.FILE, () -> {
            chipIndex = (chipIndex + 1) % attachments.size();
            sync();
        });
        for (int i = 0; i < ROWS; i++) {
            int row = i;
            var option = button(Component.empty(), null, () -> select(firstRow + row));
            option.setHeight(ROW);
            rows.add(option);
        }
        slider = efforts.size() < 2 ? null : new UiSlider(new Bounds(0, 0, area.width() - 16, TOOL),
                0, efforts.size() - 1, 1, effort, preferences, this.efforts::get, value -> {
                    effort = value;
                    sync();
                    onEffortChange.accept(value);
                });
        if (slider != null) slider.setOnCommit(ignored -> {}, previous -> {
            setEffort(previous);
            onEffortChange.accept(previous);
        });
        sync();
    }

    public String draft() { return editor.getValue(); }

    public void setDraft(String draft) {
        writeDraft(draft);
        closeMenus();
    }

    /** Apply asynchronous host results on the client thread. */
    public void appendDictation(String text) {
        if (text == null || text.isBlank()) return;
        setDraft(draft().isEmpty() || Character.isWhitespace(draft().charAt(draft().length() - 1))
                ? draft() + text : draft() + " " + text);
    }

    public void addAttachments(List<String> names) {
        for (String name : List.copyOf(names)) if (!name.isBlank()) attachments.add(name);
        chipIndex = Math.max(0, attachments.size() - 1);
        sync();
    }

    public void setBusy(boolean busy) {
        if (this.busy == busy) return;
        long now = now();
        morphFrom = glyphProgress(now);
        morphStart = now;
        this.busy = busy;
        sync();
    }

    public void setListening(boolean listening) {
        this.listening = listening;
        sync();
    }

    public void setModel(String key) {
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).key().equals(key)) {
                model = i;
                sync();
                return;
            }
        }
    }

    public void setEffort(int index) {
        effort = Objects.checkIndex(index, efforts.size());
        if (slider != null) slider.setValue(index);
        sync();
    }

    public void setOnEffortChange(IntConsumer listener) {
        onEffortChange = Objects.requireNonNull(listener);
    }

    /** Allows a containing screen to dismiss the popup before handling Escape itself. */
    public boolean dismissPopup() {
        if (menu == Menu.NONE) return false;
        closeMenus();
        return true;
    }

    public void focusEditor() { setFocused(editor); }

    /** Release the focused child and cancel an in-progress effort drag before removing this widget. */
    public void cancelInteraction() {
        closeMenus();
        setFocused((GuiEventListener) null);
    }

    @Override
    public List<AbstractWidget> children() {
        var result = new ArrayList<AbstractWidget>();
        for (var widget : List.of(editor, previousChip, chip, add, modelButton, effortButton, mic, send)) {
            if (widget.visible) result.add(widget);
        }
        for (var row : rows) if (row.visible) result.add(row);
        if (slider != null && slider.visible) result.add(slider);
        return result;
    }

    @Override
    boolean handleKey(int key, boolean shift) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return dismissPopup();
        if (menu != Menu.NONE && menu != Menu.EFFORT && (getFocused() == editor || rows.contains(getFocused())
                || getFocused() == add || getFocused() == modelButton)) {
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                moveCursor(key == GLFW.GLFW_KEY_UP ? -1 : 1);
                return true;
            }
            if (!shift && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
                    || (key == GLFW.GLFW_KEY_TAB && getFocused() == editor && !choices.isEmpty()))) {
                if (!choices.isEmpty()) select(cursor);
                return true;
            }
        }
        if (getFocused() == editor && !shift && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            // While a response is pending, only the stop tile stops it.
            if (!busy && send.active) activateSend();
            return true;
        }
        return false;
    }

    @Override
    boolean handleScroll(double x, double y, double amount) {
        int menuBottom = editor.getY() - GAP - (attachments.isEmpty() ? 0 : ROW + GAP);
        if (amount == 0 || menu == Menu.NONE || menu == Menu.EFFORT || !inside(x, y) || y >= menuBottom
                || y < menuBottom - rows.size() * ROW) {
            return false;
        }
        moveCursor(amount > 0 ? -1 : 1);
        return true;
    }

    @Override
    void pointerAt(double x, double y) {
        if (!inside(x, y) || (menu != Menu.NONE && editor.isMouseOver(x, y))) dismissPopup();
    }

    private boolean inside(double x, double y) {
        return x >= getX() && x < getX() + getWidth() && y >= getY() && y < getY() + getHeight();
    }

    @Override
    void afterRelease(GuiEventListener target, double x, double y) {
        if (target == send && send.isMouseOver(x, y) && isActive()) activateSend();
    }

    private void activateFromKeyboard() {
        if (!pressed(send)) activateSend();
    }

    private void activateSend() {
        if (busy) {
            onStop.run();
            return;
        }
        String value = draft().strip();
        if (value.isEmpty() && attachments.isEmpty()) return;
        var payload = new Submission(value, attachments,
                models.isEmpty() ? null : models.get(model).key(), efforts.isEmpty() ? -1 : effort);
        onSend.accept(payload);
        attachments.clear();
        chipIndex = 0;
        focusEditor();
        setDraft("");
    }

    private void writeDraft(String value) {
        Objects.requireNonNull(value);
        programmaticEdit = true;
        try { editor.setValue(value); }
        finally { programmaticEdit = false; }
    }

    static Token trailingToken(String value) {
        var matcher = TOKEN.matcher(value);
        return matcher.find() ? new Token(matcher.group(1).charAt(0), matcher.group(2).toLowerCase(Locale.ROOT),
                matcher.start(1)) : null;
    }

    private void draftChanged(String value) {
        if (programmaticEdit) return;
        lastEdit = now();
        token = trailingToken(value);
        menu = token == null ? Menu.NONE : token.prefix() == '@' ? Menu.SOURCES : Menu.COMMANDS;
        if ((menu == Menu.SOURCES && sources.isEmpty()) || (menu == Menu.COMMANDS && commands.isEmpty())) menu = Menu.NONE;
        rebuildChoices();
    }

    private void toggle(Menu target) {
        if (menu == target && token == null) {
            closeMenus();
            return;
        }
        token = null;
        menu = target;
        rebuildChoices();
        if (target == Menu.EFFORT && slider != null && getFocused() != null) setFocused(slider);
    }

    private void closeMenus() {
        if (rows.contains(getFocused()) || (slider != null && getFocused() == slider)) focusEditor();
        if (slider != null) slider.cancelEdit();
        setDragging(false);
        menu = Menu.NONE;
        token = null;
        rebuildChoices();
    }

    private void rebuildChoices() {
        String query = token == null ? "" : token.query();
        choices = switch (menu) {
            case SOURCES -> sources.stream().map(Source::option).filter(o -> matches(o, query)).toList();
            case COMMANDS -> commands.stream().filter(o -> matches(o, query)).toList();
            case MODELS -> models;
            default -> List.of();
        };
        cursor = menu == Menu.MODELS ? model : 0;
        firstRow = Math.max(0, cursor - ROWS + 1);
        sync();
    }

    private static boolean matches(Option option, String query) {
        return option.key().toLowerCase(Locale.ROOT).startsWith(query)
                || option.label().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private void moveCursor(int direction) {
        if (choices.isEmpty()) return;
        cursor = UiMath.clamp(cursor + direction, 0, choices.size() - 1);
        firstRow = UiMath.clamp(firstRow, Math.max(0, cursor - ROWS + 1), cursor);
        sync();
    }

    private void select(int index) {
        if (index >= choices.size()) return;
        var selected = choices.get(index);
        Token replace = token;
        Menu selectionMenu = menu;
        closeMenus();
        focusEditor();
        if (selectionMenu == Menu.MODELS) {
            setModel(selected.key());
            return;
        }
        String base = replace == null ? draft() : draft().substring(0, replace.start());
        if (selectionMenu == Menu.COMMANDS) {
            writeDraft(base + selected.label().getString() + " ");
        } else {
            var source = sources.stream().filter(item -> item.option().equals(selected)).findFirst().orElseThrow();
            if (source.attachment()) {
                writeDraft(base);
                onAttach.run();
            } else {
                writeDraft(base + "@" + selected.label().getString() + " ");
            }
        }
        sync();
    }

    private void removeChip() {
        attachments.remove(chipIndex);
        if (attachments.isEmpty()) focusEditor();
        sync();
    }

    private UiButton button(Component text, GraphicIcon icon, Runnable action) {
        var button = new UiButton(0, 0, TOOL, TOOL, text, ignored -> action.run(), preferences, false, UiStyle.DEFAULT);
        button.setIcon(icon);
        button.setSampleBackground(false);
        return button;
    }

    private void sync() {
        layoutX = getX();
        layoutY = getY();
        layoutHeight = getHeight();
        int left = getX() + 8;
        int width = getWidth() - 16;
        int toolbar = getY() + getHeight() - TOOL - 6;
        int editorBottom = toolbar - GAP;
        int lines = font.getSplitter().splitLines(draft(), Math.max(1, width - 8), Style.EMPTY).size();
        int editorHeight = Math.min(53, 8 + Math.max(1, lines) * 9);
        int minEditorTop = getY() + ROWS * ROW + 5 + (attachments.isEmpty() ? 0 : ROW + GAP);
        editorHeight = Math.max(17, Math.min(editorHeight, editorBottom - minEditorTop));
        editor.setHeight(editorHeight);
        editor.setX(left);
        editor.setY(editorBottom - editorHeight);
        int right = left + width;
        place(send, right - TOOL, toolbar, TOOL, true);
        send.setPreview(sendLabel());
        send.active = busy || !draft().isBlank() || !attachments.isEmpty();
        if (!send.active && getFocused() == send) focusEditor();
        send.setSelected(send.active);
        place(mic, right - 2 * TOOL - GAP, toolbar, TOOL, onDictate != null);
        updateLabel(mic, label(listening ? "listening" : "dictate"));
        mic.setSelected(listening);
        place(add, left, toolbar, TOOL, !sources.isEmpty());
        add.setSelected(menu == Menu.SOURCES);
        int pickX = add.visible ? left + TOOL + GAP : left;
        int room = right - (mic.visible ? 2 : 1) * (TOOL + GAP) - pickX;
        int modelWidth = slider == null ? room : Math.max(20, (room - GAP) / 2);
        place(modelButton, pickX, toolbar, modelWidth, !models.isEmpty());
        if (modelButton.visible) updateLabel(modelButton, models.get(model).label());
        modelButton.setSelected(menu == Menu.MODELS);
        int effortX = modelButton.visible ? pickX + modelWidth + GAP : pickX;
        place(effortButton, effortX, toolbar, room - (effortX - pickX), slider != null);
        if (effortButton.visible) updateLabel(effortButton, efforts.get(effort));
        effortButton.setSelected(menu == Menu.EFFORT || maxed());
        syncPopup(left, width);
    }

    private void syncPopup(int left, int width) {
        int count = attachments.size();
        int top = editor.getY();
        if (count > 0) {
            chipIndex = Math.min(chipIndex, count - 1);
            var name = Component.literal(attachments.get(chipIndex));
            updateLabel(chip, label("remove_attachment", name));
            updateLabel(previousChip, label("next_attachment_count", chipIndex + 1, count));
            top -= ROW + GAP;
        }
        place(previousChip, left, top, TOOL, count > 1);
        int chipInset = count > 1 ? TOOL + GAP : 0;
        place(chip, left + chipInset, top, width - chipInset, count > 0);
        int countRows = Math.min(ROWS, choices.size());
        for (int i = 0; i < ROWS; i++) {
            var row = rows.get(i);
            int index = firstRow + i;
            place(row, left, top - GAP - (countRows - i) * ROW, width, i < countRows && index < choices.size());
            if (row.visible) {
                var option = choices.get(index);
                updateLabel(row, option.label());
                row.setLeadingIcon(option.icon());
                row.setPreview(option.detail());
                row.setSelected(index == cursor);
            }
        }
        if (slider != null) {
            slider.setX(left);
            slider.setY(top - TOOL - GAP);
            slider.visible = menu == Menu.EFFORT;
        }
    }

    private static void place(UiButton button, int x, int y, int width, boolean visible) {
        button.setX(x);
        button.setY(y);
        button.setWidth(Math.max(1, width));
        button.visible = visible;
    }

    private static void updateLabel(UiButton button, Component label) {
        if (!button.getMessage().equals(label)) button.setMessage(label);
    }

    private boolean maxed() { return slider != null && effort == efforts.size() - 1; }

    @Override
    void paint(Canvas canvas, int mouseX, int mouseY) {
        if (getX() != layoutX || getY() != layoutY || getHeight() != layoutHeight) sync();
        UiTheme theme = UiTheme.current();
        int top = editor.getY() - 4 - (attachments.isEmpty() ? 0 : ROW + GAP);
        int height = getY() + getHeight() - top;
        int edge = (isFocused() ? theme.accent() : preferences.highContrast() ? theme.text() : theme.border()) | 0xFF000000;
        int surface = theme.surface() | 0xFF000000;
        int fill = maxed() ? GuiMotion.lerpColor(surface, theme.accent() | 0xFF000000, .23f) : surface;
        canvas.box(getX(), top, getWidth(), height, 9, edge, fill);
        if (maxed() && !preferences.reducedMotion()) {
            double time = now() / 1000.0;
            double energy = Math.exp(-(now() - lastEdit) / 180.0);
            for (int i = 0; i < 18; i++) {
                int x = getX() + 7 + (i * 37 % (getWidth() - 14));
                double progress = (time * (.14 + i % 5 * .025) + i * .137) % 1;
                int y = top + 6 + (int) ((1 - progress) * (height - 13));
                int size = 1 + (energy > .4 && i % 3 == 0 ? 1 : 0);
                canvas.dot(x, y, size, GuiMotion.lerpColor(fill, theme.accent() | 0xFF000000,
                        (float) Math.sin(Math.PI * progress) * (.28f + (float) energy * .32f)));
            }
        }
        int menuTop = top - 2;
        if (menu != Menu.NONE && menu != Menu.EFFORT && choices.isEmpty()) {
            canvas.box(getX() + 5, menuTop - ROW, getWidth() - 10, ROW, 5, edge, surface);
            canvas.text(font, label("empty"), getX() + 8, menuTop - font.lineHeight - 4, theme.text() | 0xFF000000);
        }
    }

    @Override
    void paintForeground(Canvas canvas) {
        int x = send.getX() + (TOOL - 16) / 2;
        int y = send.getY() + (TOOL - 16) / 2;
        var theme = UiTheme.current();
        int text = theme.text() | 0xFF000000;
        int ink = send.active || preferences.highContrast() ? text
                : GuiMotion.lerpColor(text, theme.surface() | 0xFF000000, .35f);
        float progress = glyphProgress(now());
        float scale = pressed(send) && !preferences.reducedMotion() ? .88f : 1f;
        for (int row = 0; row < 16; row++) {
            int start = -1;
            for (int column = 0; column <= 16; column++) {
                boolean inside = column < 16 && glyphContains(8 + (column - 7.5f) / scale,
                        8 + (row - 7.5f) / scale, progress);
                if (inside && start < 0) start = column;
                else if (!inside && start >= 0) {
                    canvas.fill(x + start, y + row, x + column, y + row + 1, ink);
                    start = -1;
                }
            }
        }
    }

    private float glyphProgress(long time) {
        float end = busy ? 1f : 0f;
        if (preferences.reducedMotion()) return end;
        float t = GuiMotion.clamp01((time - morphStart) / 240f);
        return morphFrom + (end - morphFrom) * t * t * (3f - 2f * t);
    }

    private static boolean glyphContains(float x, float y, float progress) {
        boolean inside = false;
        for (int i = 0, j = ARROW.length - 2; i < ARROW.length; j = i, i += 2) {
            float ay = coordinate(i + 1, progress), by = coordinate(j + 1, progress);
            if ((y < ay) == (y < by)) continue;
            float ax = coordinate(i, progress), bx = coordinate(j, progress);
            if (x < ax + (bx - ax) * (y - ay) / (by - ay)) inside = !inside;
        }
        return inside;
    }

    private static float coordinate(int index, float progress) {
        return ARROW[index] + (STOP[index] - ARROW[index]) * progress;
    }

    private Component sendLabel() { return label(busy ? "stop" : "send"); }

    @Override
    Component focusedLabel() {
        return getFocused() == send ? sendLabel() : null;
    }

    private static Component label(String suffix, Object... args) {
        return Component.translatable("sh_better_ui.prompt_bar." + suffix, args);
    }

    private static long now() { return System.nanoTime() / 1_000_000L; }
}
