// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.kernel.api.config.ConfigPage;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextField;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.gui.text.UiHudRichText;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Native keyboard and narration with independently animated option meters. */
public final class ConfigPageScreen<T> extends ConfigPagePlatform {
    private static final int GAP = 8;
    private static final int INSET = 10;
    private static final int BUTTON = 22;
    private static final int ROW = 58;
    private static final Component EMPTY = Component.empty();
    private final ConfigPage<T> page;
    private final Screen parent;
    private final Predicate<T> commit;
    private final Supplier<? extends GuiEventListener> returnFocus;
    private final ConfigPageDraft<T> draft;
    private final List<UiTextField> inputs = new ArrayList<>();
    private final List<UiButton> presets = new ArrayList<>();
    private final List<InterruptibleAnimation> meters;
    private final TextReadabilityPalette palette = new TextReadabilityPalette();
    private final TextReadabilityPalette cardPalette = new TextReadabilityPalette();
    private final InterruptibleAnimation entrance = new InterruptibleAnimation(Spring.INDICATOR);
    private UiPreferences preferences;
    private UiButton previous;
    private UiButton next;
    private UiButton done;
    private UiButton cancel;
    private int left, span, top, body, footer, columns, visibleRows, firstRow;
    private List<FormattedCharSequence> description = List.of();
    private boolean layoutReady;
    private boolean failed;
    private boolean finished;

    public ConfigPageScreen(ConfigPage<T> page, Screen parent, T current, Predicate<T> commit,
                            Supplier<? extends GuiEventListener> returnFocus) {
        super(page.title());
        this.page = page;
        this.parent = parent;
        this.commit = Objects.requireNonNull(commit);
        this.returnFocus = Objects.requireNonNull(returnFocus);
        draft = new ConfigPageDraft<>(page, current);
        meters = page.options().stream().map(option -> new InterruptibleAnimation(Spring.INDICATOR)).toList();
    }

    @Override
    protected void init() {
        int focus = inputs.indexOf(getFocused());
        inputs.clear();
        presets.clear();
        previous = null;
        next = null;
        done = null;
        preferences = UiPreferences.configured();
        span = Math.max(1, Math.min(560, width - 16));
        left = (width - span) / 2;
        columns = span >= 360 ? 2 : 1;
        int presetCount = page.presets().size();
        int presetColumns = Math.min(presetCount, Math.max(3, (span - 2 * INSET) / 90));
        int presetHeight = presetCount == 0 ? 0 : ((presetCount + presetColumns - 1) / presetColumns) * (BUTTON + GAP);
        boolean compact = width < 150 || height < 146 + presetHeight;
        description = compact ? List.of() : font.split(UiHudRichText.prepare(page.description()), span - 2 * INSET);
        int baseHeader = INSET + font.lineHeight + GAP + presetHeight;
        int descriptionHeight = description.isEmpty() ? 0 : Math.min(2, description.size()) * font.lineHeight + 4;
        if (height < baseHeader + descriptionHeight + ROW + 65) {
            description = List.of();
            descriptionHeight = 0;
        }
        int header = baseHeader + descriptionHeight;
        int panelHeight = Math.min(height - 12, header + rowCount() * ROW + 58);
        top = Math.max(2, (height - panelHeight) / 2);
        body = top + header;
        footer = Math.max(0, height - 25);
        if (!compact && panelHeight < height - 12) footer = top + panelHeight - BUTTON - 5;
        visibleRows = compact ? 0 : Math.max(1, (footer - body - 28) / ROW);
        if (compact) {
            cancel = button(left, footer, span, CommonComponents.GUI_CANCEL, GraphicIcon.CLOSE, false, this::onClose);
            return;
        }
        int presetY = body - presetHeight;
        for (var preset : page.presets()) {
            int index = presets.size();
            int column = index % presetColumns;
            int x = left + INSET + (span - 2 * INSET) * column / presetColumns;
            int right = left + INSET + (span - 2 * INSET) * (column + 1) / presetColumns;
            int y = presetY + index / presetColumns * (BUTTON + GAP);
            var control = button(x, y, right - x - 3, preset.label(), null, false, () -> choose(preset));
            control.setPreview(preset.description());
            presets.add(control);
        }
        for (int i = 0; i < page.options().size(); i++) addInput(i);
        if (maxFirstRow() > 0) {
            previous = button(left + span - INSET - 46, footer - 25, 22, label("previous"), null, false, () -> page(-1));
            next = button(left + span - INSET - 22, footer - 25, 22, label("next"), null, false, () -> page(1));
            previous.setPreview(label("previous.tooltip"));
            next.setPreview(label("next.tooltip"));
        }
        int half = (span - 2 * INSET - GAP) / 2;
        done = button(left + INSET, footer, half, CommonComponents.GUI_DONE, GraphicIcon.SUCCESS, true, this::accept);
        cancel = button(left + INSET + half + GAP, footer, span - 2 * INSET - half - GAP,
                CommonComponents.GUI_CANCEL, GraphicIcon.CLOSE, false, this::onClose);
        if (focus >= 0) firstRow = Math.max(focus / columns - visibleRows + 1, Math.min(firstRow, focus / columns));
        positionRows();
        if (focus >= 0 && inputs.get(focus).visible) setFocused(inputs.get(focus));
        refresh(!layoutReady);
        if (!layoutReady) {
            if (preferences.reducedMotion()) entrance.snapTo(1);
            else entrance.animateTo(1, now());
        }
        layoutReady = true;
    }

    private void addInput(int index) {
        var option = page.options().get(index);
        int inputWidth = Math.max(40, Math.min(94, cellWidth() / 2));
        var input = ShBetterUiApi.controls().textField(font, new Bounds(0, 0, inputWidth, 20),
                option.label(), 48, preferences);
        input.setValue(draft.text(index));
        input.setOnChange(value -> {
            draft.set(index, value);
            failed = false;
            refresh(false);
        });
        inputs.add(addRenderableWidget(input));
    }

    private UiButton button(int x, int y, int w, Component text, GraphicIcon icon, boolean primary, Runnable action) {
        var button = ShBetterUiApi.controls().button(new Bounds(x, y, w, BUTTON), text,
                ignored -> action.run(), preferences, primary, UiStyle.DEFAULT);
        if (icon != null && w >= 58) button.setLeadingIcon(icon);
        button.setSampleBackground(false);
        return addRenderableWidget(button);
    }

    private void choose(ConfigPage.Preset<T> preset) {
        draft.apply(preset);
        for (int i = 0; i < inputs.size(); i++) inputs.get(i).setValue(draft.text(i));
        failed = false;
        refresh(false);
    }

    private void refresh(boolean snap) {
        if (done == null) return;
        long time = now();
        for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            var option = page.options().get(i);
            double value = draft.value(i);
            boolean valid = Double.isFinite(value);
            input.setError(valid ? null : label("invalid"));
            input.setTooltip(Tooltip.create(UiHudRichText.prepare(option.description()).copy().append(CommonComponents.NEW_LINE)
                    .append(range(option)).append(valid ? EMPTY : CommonComponents.NEW_LINE.copy()
                            .append(label("invalid").withStyle(ChatFormatting.RED)))));
            float target = valid ? fraction(value, option.min(), option.max()) : 0f;
            var meter = meters.get(i);
            if (snap || preferences.reducedMotion()) meter.snapTo(target);
            else if (meter.target() != target) meter.animateTo(target, time);
        }
        for (int i = 0; i < presets.size(); i++) presets.get(i).setSelected(draft.selected(page.presets().get(i)));
        done.active = draft.firstInvalid() < 0;
        done.setPreview(status());
    }

    private static float fraction(double value, double min, double max) {
        if (Double.isFinite(max - min)) return (float) ((value - min) / (max - min));
        return (float) ((value / 2 - min / 2) / (max / 2 - min / 2));
    }

    private Component status() {
        int invalid = draft.firstInvalid();
        if (invalid >= 0) return label("invalid_field", page.options().get(invalid).label());
        if (failed) return label("save_failed");
        return draft.dirty() ? label("unsaved") : null;
    }

    private static Component range(ConfigPage.NumberOption<?> option) {
        return label("range", ConfigPageDraft.format(option.min()), ConfigPageDraft.format(option.max()));
    }

    private int rowCount() { return (page.options().size() + columns - 1) / columns; }
    private int maxFirstRow() { return Math.max(0, rowCount() - visibleRows); }
    private int cellWidth() { return (span - 2 * INSET - (columns - 1) * GAP) / columns; }

    private void positionRows() {
        firstRow = Math.max(0, Math.min(maxFirstRow(), firstRow));
        for (int i = 0; i < inputs.size(); i++) {
            int row = i / columns - firstRow;
            var input = inputs.get(i);
            input.visible = row >= 0 && row < visibleRows;
            if (!input.visible && getFocused() == input) setFocused(null);
            input.setX(cellX(i) + cellWidth() - 6 - input.getWidth());
            input.setY(body + row * ROW + ROW - 32);
        }
        if (previous != null) {
            previous.active = firstRow > 0;
            next.active = firstRow < maxFirstRow();
        }
    }

    private int cellX(int i) { return left + INSET + i % columns * (cellWidth() + GAP); }

    private boolean move(int direction) {
        int target = Math.max(0, Math.min(maxFirstRow(), firstRow + direction));
        if (firstRow == target || visibleRows == 0) return false;
        firstRow = target;
        positionRows();
        return true;
    }

    private boolean page(int direction) {
        if (!move(direction)) return false;
        setFocused(inputs.get(firstRow * columns));
        return true;
    }

    @Override
    boolean pageKey(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_PAGE_UP -> page(-1);
            case GLFW.GLFW_KEY_PAGE_DOWN -> page(1);
            default -> false;
        };
    }

    @Override
    boolean scroll(double x, double y, double delta) {
        return delta != 0 && x >= left && x < left + span && y >= body && y < footer && move(delta < 0 ? 1 : -1);
    }

    private void accept() {
        if (finished || !done.active) return;
        if (!draft.commit(commit)) {
            failed = true;
            refresh(false);
            return;
        }
        closeToParent();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (finished) return;
        if (!draft.dirty()) {
            closeToParent();
            return;
        }
        int focus = inputs.indexOf(getFocused());
        confirmDiscard(Component.translatable("sh_better_ui.panel.discard.title"),
                Component.translatable("sh_better_ui.panel.discard.settings"),
                Component.translatable("sh_better_ui.panel.discard.action"), CommonComponents.GUI_CANCEL,
                this::closeToParent, () -> {
                    if (focus >= 0 && focus < inputs.size() && inputs.get(focus).visible) setFocused(inputs.get(focus));
                    else setFocused(cancel);
                });
    }

    private void closeToParent() {
        if (finished) return;
        finished = true;
        show(parent);
        var focus = returnFocus.get();
        if (parent != null && focus != null && parent.children().contains(focus)) parent.setFocused(focus);
    }

    @Override
    public Component getNarrationMessage() {
        var status = status();
        return status == null ? getTitle() : CommonComponents.joinForNarration(getTitle(), status);
    }

    @Override
    void paint(Painter painter) {
        var theme = UiTheme.current();
        int text = theme.text() | 0xFF000000;
        int accent = theme.accent() | 0xFF000000;
        int frame = preferences.highContrast() ? text : theme.border() | 0xFF000000;
        int surface = theme.surface() | 0xFF000000;
        int card = GuiMotion.lerpColor(surface, text, .07f);
        palette.update(null, surface, surface);
        cardPalette.update(null, card, card);
        float glow = 1 - GuiMotion.clamp01(entrance.sample(now(), preferences));
        int panelBorder = GuiMotion.lerpColor(frame, accent, glow);
        painter.plate(left, top, span, Math.max(BUTTON, footer + BUTTON + 5 - top), 11, panelBorder, surface);
        int x = left + INSET;
        lines(painter, page.title().copy().withStyle(ChatFormatting.BOLD), x, top + INSET,
                span - 2 * INSET, 1, accent, palette);
        if (visibleRows == 0) {
            lines(painter, Component.translatable("sh_better_ui.picker.too_small"), x, top + 23,
                    span - 2 * INSET, Math.max(0, (footer - top - 23) / font.lineHeight), text, palette);
            return;
        }
        for (int i = 0; i < Math.min(2, description.size()); i++) {
            painter.text(description.get(i), x, top + INSET + font.lineHeight + 4 + i * font.lineHeight, text,
                    preferences, palette);
        }
        long time = now();
        for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            if (!input.visible) continue;
            int y = body + (i / columns - firstRow) * ROW;
            int cellX = cellX(i);
            var option = page.options().get(i);
            boolean valid = Double.isFinite(draft.value(i));
            int edge = input.isFocused() ? accent : !valid ? 0xFFFF8282 : frame;
            painter.plate(cellX, y, cellWidth(), ROW - 5, 7, edge, card);
            lines(painter, option.label().copy().withStyle(ChatFormatting.BOLD), cellX + 6, y + 6,
                    cellWidth() - 12, 2, text, cardPalette);
            lines(painter, range(option), cellX + 6, y + ROW - 27,
                    cellWidth() - input.getWidth() - 19, 1, text, cardPalette);
            int trackWidth = cellWidth() - 12;
            int bar = Math.min(trackWidth, Math.round(trackWidth * GuiMotion.clamp01(meters.get(i).sample(time))));
            painter.plate(cellX + 6, y + ROW - 10, trackWidth, 2, 1, 0, frame);
            if (bar > 0) painter.plate(cellX + 6, y + ROW - 10, bar, 2, 1, 0, accent);
        }
        var status = status();
        if (status != null) lines(painter, status, x, footer - 17, span - 2 * INSET - (next == null ? 0 : 55),
                1, text, palette);
    }

    private void lines(Painter painter, Component message, int x, int y, int w, int maxLines, int ink,
                       TextReadabilityPalette palette) {
        var lines = font.split(UiHudRichText.prepare(message), Math.max(1, w));
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            painter.text(lines.get(i), x, y + i * font.lineHeight, ink, preferences, palette);
        }
    }

    private static long now() { return System.nanoTime() / 1_000_000; }

    private static net.minecraft.network.chat.MutableComponent label(String key, Object... args) {
        return Component.translatable("sh_better_ui.config_page." + key, args);
    }
}
