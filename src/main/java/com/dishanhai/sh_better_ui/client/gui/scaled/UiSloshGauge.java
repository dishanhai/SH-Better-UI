// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.controls.SloshGaugeState;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.IntConsumer;

/** Vertical liquid meter. A change listener enables pointer and keyboard input. */
public final class UiSloshGauge extends AbstractWidget {
    private static final int INSET = 2;
    private static final String KEY = "sh_better_ui.slosh_gauge.";

    public record Style(int radius, int ticks, int liquidColor, int glassColor,
                        SloshGaugeState.Motion motion, boolean showValue, Component unit) {
        public static final Style DEFAULT = new Style(20, 4, 0xFFF5F5F5, 0xFF27272A,
                SloshGaugeState.Motion.DEFAULT, true, Component.translatable(KEY + "unit"));

        public Style {
            radius = Math.max(0, radius);
            ticks = Math.max(0, ticks);
            Objects.requireNonNull(motion);
            Objects.requireNonNull(unit);
        }
    }

    private final SloshGaugeState state;
    private final Style style;
    private final IntConsumer onChange;
    private UiPreferences preferences;

    public UiSloshGauge(Bounds area, Component name, int value, Style style,
                        UiPreferences preferences, IntConsumer onChange) {
        super(area.x(), area.y(), area.width(), area.height(), name);
        this.style = Objects.requireNonNull(style);
        this.onChange = onChange;
        state = new SloshGaugeState(value, style.motion());
        setPreferences(preferences);
        active = onChange != null;
    }

    public int value() { return state.value(); }

    /** Updates the level without calling the listener. */
    public void setValue(int value) {
        state.setValue(value);
    }

    public void setPreferences(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    /** Restores the pre-drag value and reports it if it changed. */
    public boolean cancelDrag() {
        int previous = value();
        if (!state.cancel()) return false;
        report(previous);
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) cancelDrag();
    }

    @Override
    public boolean isMouseOver(double x, double y) {
        return onChange != null && super.isMouseOver(x, y) && hasRoom()
                && SloshGaugePaint.contains(x - getX(), y - getY(), getWidth(), getHeight(), style.radius());
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || !isMouseOver(x, y)) return false;
        int previous = value();
        if (!state.press(localY(y), innerHeight())) return false;
        report(previous);
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != 0) return false;
        if (!canInteract()) return cancelDrag();
        int previous = value();
        if (!state.drag(localY(y), innerHeight())) return false;
        report(previous);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button != 0) return false;
        if (!canInteract() || !Minecraft.getInstance().isWindowActive()) return cancelDrag();
        int previous = value();
        if (!state.release(localY(y), innerHeight())) return false;
        report(previous);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return cancelDrag();
        if (!canInteract() || !isFocused() || state.dragging()) return false;
        int previous = value();
        int step = (modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 2 : 10;
        int next = switch (key) {
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_RIGHT -> previous + step;
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_LEFT -> previous - step;
            case GLFW.GLFW_KEY_PAGE_UP -> previous + 10;
            case GLFW.GLFW_KEY_PAGE_DOWN -> previous - 10;
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> 100;
            default -> Integer.MIN_VALUE;
        };
        if (next == Integer.MIN_VALUE) return false;
        setValue(next);
        state.snap();
        report(previous);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable(KEY + "narration", getMessage(), readout()));
        if (canInteract()) output.add(NarratedElementType.USAGE, Component.translatable(KEY + "usage"));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!canInteract() || !Minecraft.getInstance().isWindowActive()) cancelDrag();
        if (!hasRoom()) return;

        var theme = UiTheme.current();
        boolean enabled = canInteract();
        boolean focused = enabled && isFocused();
        var surface = state.sample(System.nanoTime(), preferences.reducedMotion());
        int glass = style.glassColor() | 0xFF000000;
        int liquid = style.liquidColor() | 0xFF000000;
        if (onChange != null && !enabled) liquid = SloshGaugePaint.blend(liquid, glass);
        double contrast = preferences.highContrast() ? ContrastPolicy.HIGH_CONTRAST_RATIO : ContrastPolicy.TEXT_RATIO;
        int glassInk = ContrastPolicy.readable(0xFFFFFFFF, glass, glass, contrast);
        int liquidInk = ContrastPolicy.readable(0xFF111111, liquid, liquid, contrast);
        int edge = focused || state.dragging() || isMouseOver(mouseX, mouseY)
                ? ContrastPolicy.readable(theme.accent(), glass, glass, 3)
                : preferences.highContrast() ? glassInk : theme.border();
        RoundedCorners.draw(graphics, getX(), getY(), getWidth(), getHeight(), style.radius(),
                focused || state.dragging() ? INSET : 1, edge, glass);

        int left = getX() + INSET, top = getY() + INSET;
        int width = getWidth() - 2 * INSET, height = innerHeight();
        float radius = RoundedCorners.insetRadius(getWidth(), getHeight(), style.radius(), INSET);
        var font = Minecraft.getInstance().font;
        var label = readout();
        int labelWidth = font.width(label);
        boolean showText = style.showValue() && labelWidth <= width && font.lineHeight <= height;
        int textX = left + (width - labelWidth) / 2, textY = top + (height - font.lineHeight) / 2;
        int markerY = top + Math.min(height - 2, Math.round((100 - value()) * height / 100f));
        int markerInset = Math.max(width / 10, Math.max(
                SloshGaugePaint.inset(markerY - top + 0.5, height, radius),
                SloshGaugePaint.inset(markerY - top + 1.5, height, radius)));

        if (showText) graphics.drawString(font, label, textX, textY, glassInk, false);
        if (state.dragging()) {
            graphics.fill(left + markerInset, markerY, left + width - markerInset, markerY + 2, glassInk);
        }
        final int fill = liquid;
        SloshGaugePaint.liquidRuns(width, height, radius, surface, (from, start, to, bottom) -> {
            int x0 = left + from, y0 = top + start, x1 = left + to, y1 = top + bottom;
            graphics.fill(x0, y0, x1, y1, fill);
            if (showText && x0 < textX + labelWidth && x1 > textX
                    && y0 < textY + font.lineHeight && y1 > textY) {
                graphics.enableScissor(x0, y0, x1, y1);
                try {
                    graphics.drawString(font, label, textX, textY, liquidInk, false);
                } finally {
                    graphics.disableScissor();
                }
            }
            if (state.dragging()) {
                int a = Math.max(x0, left + markerInset), b = Math.min(x1, left + width - markerInset);
                int c = Math.max(y0, markerY), d = Math.min(y1, markerY + 2);
                if (a < b && c < d) graphics.fill(a, c, b, d, liquidInk);
            }
        });
        int ticks = Math.min(style.ticks(), height / 3);
        int tickStart = Math.max(0, width - Math.max(2, width / 5));
        int tickEnd = width - 1;
        for (int i = 1; i <= ticks; i++) {
            int row = i * height / (ticks + 1);
            int inset = SloshGaugePaint.inset(row + 0.5, height, radius);
            for (int x = Math.max(tickStart, inset); x < Math.min(tickEnd, width - inset); x++) {
                boolean wet = row + 0.5 >= surface.topAt(x + 0.5, width, height);
                int ink = wet ? liquidInk : glassInk;
                int color = preferences.highContrast() ? ink : SloshGaugePaint.blend(ink, wet ? fill : glass);
                graphics.fill(left + x, top + row, left + x + 1, top + row + 1, color);
            }
        }
    }

    private Component readout() {
        return Component.translatable(KEY + "readout", value(), style.unit());
    }

    private boolean hasRoom() { return getWidth() >= 8 && innerHeight() >= 4; }
    private boolean canInteract() { return onChange != null && isActive() && hasRoom(); }
    private int innerHeight() { return getHeight() - 2 * INSET; }
    private double localY(double y) { return y - getY() - INSET; }

    private void report(int previous) {
        if (onChange != null && value() != previous) onChange.accept(value());
    }
}
