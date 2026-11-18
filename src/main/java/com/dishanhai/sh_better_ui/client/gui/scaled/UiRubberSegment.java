// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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
import java.util.function.IntConsumer;

/** Single-choice control with a draggable thumb and elastic transitions. */
public final class UiRubberSegment extends AbstractWidget {
    private static final int INSET = 2;
    private static final String KEY = "sh_better_ui.rubber_segment.";

    private final List<UiSectionBar.Section> options;
    private final IntConsumer onChange;
    private final RubberSegmentState state;
    private final Tooltip[] tooltips;
    private UiPreferences preferences;
    private boolean liquidGlass;
    private final GlassSegmentStyle.Scrim trackScrim = new GlassSegmentStyle.Scrim();
    private final GlassSegmentStyle.Scrim thumbScrim = new GlassSegmentStyle.Scrim();

    public UiRubberSegment(Bounds area, Component name, List<UiSectionBar.Section> options, int selected,
                           UiPreferences preferences, IntConsumer onChange) {
        super(area.x(), area.y(), area.width(), area.height(), name);
        this.options = List.copyOf(options);
        this.onChange = Objects.requireNonNull(onChange);
        state = new RubberSegmentState(this.options.size(), selected);
        tooltips = this.options.stream().map(option -> Tooltip.create(option.label())).toArray(Tooltip[]::new);
        setPreferences(preferences);
        syncBounds();
    }

    /** Opt-in optical track and thumb. Disabled preferences retain an opaque presentation. */
    public void setLiquidGlass(boolean enabled) { liquidGlass = enabled; }
    public boolean isLiquidGlass() { return liquidGlass; }

    public int selectedIndex() { return state.selected(); }

    /** Synchronizes an external selection immediately without calling the change listener. */
    public void select(int index) {
        if (index != state.selected()) state.select(index);
    }

    public void setPreferences(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
        PlatformUiApi.setTooltipDelay(this, preferences.feedback().tooltipDelayMs());
    }

    public boolean cancelDrag() { return state.cancel(); }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) cancelDrag();
    }

    private boolean syncBounds() {
        int innerWidth = getWidth() - 2 * INSET;
        state.resize(innerWidth);
        return innerWidth >= options.size() && getHeight() >= 14;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || !syncBounds() || !isMouseOver(x, y)) return false;
        state.press(localX(x), now(), reduced());
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != 0) return false;
        if (!active || !visible || !syncBounds()) return cancelDrag();
        return state.drag(localX(x), now(), reduced());
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button != 0 || state.pressed() < 0) return false;
        if (!active || !visible || !syncBounds() || !Minecraft.getInstance().isWindowActive()) return cancelDrag();
        int previous = state.selected();
        int next = state.release(localX(x), isMouseOver(x, y), now(), reduced());
        if (next != previous) onChange.accept(next);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return cancelDrag();
        if (!active || !visible || !isFocused() || !syncBounds() || state.pressed() >= 0) return false;
        int selected = state.selected();
        int last = options.size() - 1;
        int next = switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> Math.max(0, selected - 1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN -> Math.min(last, selected + 1);
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> last;
            default -> -1;
        };
        if (next < 0) return false;
        if (next != selected) {
            state.select(next);
            onChange.accept(next);
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        int index = state.selected();
        output.add(NarratedElementType.TITLE,
                Component.translatable(KEY + "narration", getMessage(), options.get(index).label(), index + 1, options.size()));
        if (active) output.add(NarratedElementType.USAGE, Component.translatable(KEY + "usage"));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!syncBounds()) return;
        if (!active || !Minecraft.getInstance().isWindowActive()) cancelDrag();
        int hover = isMouseOver(mouseX, mouseY) ? state.indexAt(localX(mouseX)) : -1;
        Tooltip tooltip = hover >= 0 ? tooltips[hover] : isFocused() ? tooltips[state.selected()] : null;
        if (getTooltip() != tooltip) setTooltip(tooltip);
        var theme = UiTheme.current();
        int track = (active ? theme.surface() : theme.background()) | 0xFF000000;
        int thumb = (active ? theme.accent() : theme.border()) | 0xFF000000;
        int ink = readable(theme.text(), track) | 0xff000000;
        int thumbInk = readable(theme.background(), thumb) | 0xff000000;
        int border = isFocused() ? ContrastPolicy.readable(theme.accent(), track, track, 3)
                : preferences.highContrast() ? ink : theme.border();
        int x = getX() + INSET;
        int y = getY() + INSET;
        int height = getHeight() - 2 * INSET;
        long frameTime = now();
        var edges = state.sample(frameTime, reduced());
        int left = x + (int) Math.round(edges.left());
        int right = x + (int) Math.round(edges.right());
        boolean optical = paintGlass(graphics, left, right, ink, thumbInk);
        RoundedCorners.draw(graphics, getX(), getY(), getWidth(), getHeight(), 8, isFocused() ? 2 : 1,
                border, trackScrim.tint(track, ink, optical));
        if (right > left) RoundedCorners.draw(graphics, left, y, right - left, height, 6, 0,
                thumb, thumbScrim.tint(thumb, thumbInk, optical));
        for (int i = 0; i < options.size(); i++) {
            int slotLeft = x + state.edge(i);
            int slotRight = x + state.edge(i + 1);
            if (i == state.pressed()) {
                graphics.fill(slotLeft + 2, y + 1, Math.max(slotLeft + 2, slotRight - 2), y + 2, ink);
            }
            if (slotLeft < left) drawClipped(graphics, i, slotLeft, slotRight, slotLeft, Math.min(slotRight, left), y, height, ink);
            drawClipped(graphics, i, slotLeft, slotRight, Math.max(slotLeft, left), Math.min(slotRight, right), y, height, thumbInk);
            if (slotRight > right) drawClipped(graphics, i, slotLeft, slotRight, Math.max(slotLeft, right), slotRight, y, height, ink);
        }
        if (state.isDragging()) {
            int preview = state.preview(frameTime, reduced());
            int center = x + (state.edge(preview) + state.edge(preview + 1)) / 2;
            int marker = center >= left && center < right ? thumbInk : ink;
            graphics.fill(center - 2, y + height - 2, center + 2, y + height - 1, marker);
        }
    }

    private boolean paintGlass(GuiGraphics graphics, int left, int right, int ink, int thumbInk) {
        if (!liquidGlass || !LiquidGlassTheme.useBackdrop(preferences)) return false;
        return com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.paintHudGlassBatch(graphics,
                GlassSegmentStyle.surfaces(getX(), getY(), getWidth(), getHeight(), left, right, ink, thumbInk));
    }

    private void drawClipped(GuiGraphics graphics, int index, int slotLeft, int slotRight, int left, int right,
                             int y, int height, int color) {
        if (left >= right) return;
        var font = Minecraft.getInstance().font;
        var option = options.get(index);
        int width = slotRight - slotLeft;
        int center = slotLeft + width / 2;
        graphics.enableScissor(left, y, right, y + height);
        try {
            if (font.width(option.label()) > width - 6 && width >= GraphicIcon.PIXELS + 2
                    && height >= GraphicIcon.PIXELS) {
                option.icon().draw(graphics, center - GraphicIcon.PIXELS / 2,
                        y + (height - GraphicIcon.PIXELS) / 2, 1, color, preferences);
            } else {
                var label = Language.getInstance().getVisualOrder(font.width(option.label()) <= width - 6
                        ? option.label() : FormattedText.composite(font.substrByWidth(option.label(),
                                Math.max(0, width - 6 - font.width(CommonComponents.ELLIPSIS))), CommonComponents.ELLIPSIS));
                graphics.drawString(font, label, center - font.width(label) / 2,
                        y + (height - font.lineHeight) / 2, color, false);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private int readable(int text, int background) {
        return ContrastPolicy.readable(text, background, background,
                preferences.highContrast() ? ContrastPolicy.HIGH_CONTRAST_RATIO : ContrastPolicy.TEXT_RATIO);
    }

    private double localX(double x) { return x - getX() - INSET; }
    private boolean reduced() { return preferences.reducedMotion(); }
    private static long now() { return System.nanoTime() / 1_000_000; }
}
