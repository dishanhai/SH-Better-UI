// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Themed UUK radio group with elastic, independently animated chips. */
public final class UiJellyRadio extends UiJellyRadioPlatform {
    private static final String KEY = "sh_better_ui.jelly_radio.";

    /** Stable value, narrated label, optional icon, and per-option disabled state. */
    public record Option(String value, Component label, GraphicIcon icon, boolean disabled) {
        public Option {
            Objects.requireNonNull(value);
            Objects.requireNonNull(label);
        }

        public Option(String value, Component label) {
            this(value, label, null, false);
        }
    }

    private final Component name;
    private final List<Option> options;
    private final List<Tooltip> tooltips;
    private final JellyRadioState state;
    private final BiConsumer<String, Integer> onChange;
    private UiPreferences preferences;

    /** A missing, unknown or disabled initial value selects the first enabled option. */
    public UiJellyRadio(Bounds area, Component name, List<Option> options, String initialValue,
                        UiPreferences preferences, BiConsumer<String, Integer> onChange) {
        super(area, name);
        this.name = name;
        this.options = List.copyOf(options);
        this.onChange = Objects.requireNonNull(onChange);
        if (this.options.stream().map(Option::value).distinct().count() != this.options.size()) {
            throw new IllegalArgumentException("Duplicate JellyRadio option value");
        }
        boolean[] enabled = new boolean[this.options.size()];
        for (int i = 0; i < enabled.length; i++) enabled[i] = !this.options.get(i).disabled();
        state = new JellyRadioState(enabled, indexOf(initialValue));
        active = state.selected() >= 0;
        tooltips = this.options.stream().map(option -> tooltip(option.disabled()
                ? Component.translatable(KEY + "disabled", option.label()) : option.label())).toList();
        setPreferences(preferences);
    }

    /** Returns null if the group has no enabled options. */
    public String selectedValue() {
        return state.selected() < 0 ? null : options.get(state.selected()).value();
    }

    public int selectedIndex() { return state.selected(); }

    /** Applies an external value immediately, without calling the listener. Unknown and disabled values are ignored. */
    public void select(String value) {
        int index = indexOf(value);
        if (!state.enabled(index)) return;
        cancelPress();
        state.select(index, now(), true);
    }

    public void setPreferences(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
        tooltipDelay(preferences.feedback().tooltipDelayMs());
    }

    public boolean cancelPress() { return state.cancel(); }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) cancelPress();
    }

    @Override
    boolean pointerDown(double x, double y) {
        return isActive() && state.press(hit(x, y, now()));
    }

    @Override
    boolean pointerUp(double x, double y) {
        if (state.pressed() < 0) return false;
        if (!isActive() || !Minecraft.getInstance().isWindowActive()) return cancelPress();
        long now = now();
        if (state.release(hit(x, y, now), now, preferences.reducedMotion())) notifySelection();
        return true;
    }

    @Override
    boolean pointerDragged(double x, double y) {
        if (state.pressed() < 0) return false;
        if (!isActive() || !isMouseOver(x, y)) cancelPress();
        return true;
    }

    @Override
    boolean key(int key) {
        if (!isActive() || !isFocused()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) return cancelPress();
        if (state.pressed() >= 0 || state.selected() < 0) return false;
        int next = switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> state.step(state.selected(), -1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN -> state.step(state.selected(), 1);
            case GLFW.GLFW_KEY_HOME -> state.step(-1, 1);
            case GLFW.GLFW_KEY_END -> state.step(options.size(), -1);
            case GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> state.selected();
            default -> -1;
        };
        if (next < 0) return false;
        if (next != state.selected() && state.select(next, now(), true)) notifySelection();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        int index = state.selected();
        output.add(NarratedElementType.TITLE, index < 0
                ? Component.translatable(KEY + "unavailable", name)
                : Component.translatable(KEY + "narration", name, options.get(index).label(), index + 1, options.size()));
        if (isActive() && index >= 0) {
            output.add(NarratedElementType.USAGE, Component.translatable(KEY + "usage"));
        }
    }

    @Override
    void paint(Canvas canvas, int mouseX, int mouseY) {
        if (!isActive() || !Minecraft.getInstance().isWindowActive() || !isMouseOver(mouseX, mouseY)) cancelPress();
        long time = now();
        int hover = hit(mouseX, mouseY, time);
        int selected = state.selected();
        int tooltipIndex = hover >= 0 ? hover : isFocused() ? selected : -1;
        showTooltip(tooltipIndex < 0 ? null : tooltips.get(tooltipIndex));
        var theme = UiTheme.current();
        for (int i = 0; i < options.size(); i++) {
            if (i != selected) paintChip(canvas, theme, i, false, i == hover, time);
        }
        if (selected >= 0) paintChip(canvas, theme, selected, true, selected == hover, time);
    }

    private void paintChip(Canvas canvas, UiTheme theme, int index, boolean selected, boolean hovered, long time) {
        boolean enabled = isActive() && state.enabled(index);
        boolean down = enabled && index == state.pressed() && hovered;
        var chip = state.chip(index, getWidth(), getHeight(), time, preferences.reducedMotion(), down);
        paintChip(canvas, theme, options.get(index).label(), options.get(index).icon(), preferences,
                chip, getX(), getY(), enabled, selected, hovered, enabled && selected && isFocused(), down);
    }

    static void paintChip(Canvas canvas, UiTheme theme, Component label, GraphicIcon icon, UiPreferences preferences,
                          JellyRadioState.Chip chip, int left, int top, boolean enabled, boolean selected,
                          boolean hovered, boolean focused, boolean down) {
        float x = left + chip.x();
        float y = top + chip.y();
        int fill = (selected ? enabled ? theme.accent() : theme.border()
                : enabled ? theme.surface() : theme.background()) | 0xff000000;
        int ink = ContrastPolicy.readable(selected ? theme.background() : theme.text(), fill, fill,
                preferences.highContrast() ? ContrastPolicy.HIGH_CONTRAST_RATIO : ContrastPolicy.TEXT_RATIO);
        int edge = focused || down || (enabled && hovered && !selected) || preferences.highContrast() ? ink : theme.border();
        canvas.box(x, y, chip.width(), chip.height(), Math.min(chip.width(), chip.height()) / 2,
                focused || down ? 2 : 1, edge, fill);

        var font = Minecraft.getInstance().font;
        int available = (int) chip.width() - 8;
        if (available <= 0 || chip.height() < font.lineHeight + 2) return;
        int iconSize = GraphicIcon.PIXELS;
        int labelWidth = font.width(label);
        boolean iconFits = icon != null && chip.width() >= iconSize + 2 && chip.height() >= iconSize + 2;
        boolean drawIcon = iconFits && available >= labelWidth + iconSize + 3;
        if (iconFits && !drawIcon && labelWidth > available) {
            canvas.icon(icon, Math.round(x + (chip.width() - iconSize) / 2),
                    Math.round(y + (chip.height() - iconSize) / 2), ink, preferences);
            return;
        }
        int iconSpace = drawIcon ? iconSize + 3 : 0;
        int textSpace = available - iconSpace;
        FormattedText shown = label;
        if (labelWidth > textSpace) {
            int ellipsisWidth = font.width(CommonComponents.ELLIPSIS);
            if (textSpace < ellipsisWidth) return;
            shown = FormattedText.composite(font.substrByWidth(label, Math.max(0, textSpace - ellipsisWidth)),
                    CommonComponents.ELLIPSIS);
        }
        var text = Language.getInstance().getVisualOrder(shown);
        int contentWidth = iconSpace + font.width(text);
        int textLeft = Math.round(x + (chip.width() - contentWidth) / 2);
        if (drawIcon) canvas.icon(icon, textLeft, Math.round(y + (chip.height() - iconSize) / 2), ink, preferences);
        canvas.text(font, text, textLeft + iconSpace, Math.round(y + (chip.height() - font.lineHeight) / 2), ink);
    }

    private int indexOf(String value) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).value().equals(value)) return i;
        }
        return -1;
    }

    private int hit(double x, double y, long time) {
        return state.hit(x - getX(), y - getY(), getWidth(), getHeight(), time, preferences.reducedMotion());
    }

    private void notifySelection() { onChange.accept(selectedValue(), state.selected()); }

    static long now() { return System.nanoTime() / 1_000_000; }
}
