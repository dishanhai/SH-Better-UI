// Author: dishanhai. Original native implementation inspired by MuoniumPlayer ThemePaletteOverlay's preview/commit interaction.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.lyrics.common.style.HsvColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Four HSV/alpha ramps and an explicit reset action. Mount as one widget; forward captured mouse releases. */
public final class UiColorEditor extends AbstractWidget implements AutoCloseable, UiCancelableInteraction {
    public static final int MIN_WIDTH = 180, MIN_HEIGHT = 132;
    private static final String KEY = "sh_better_ui.color_editor.";
    private static final String[] CHANNELS = {"hue", "saturation", "value", "alpha"};
    private final ColorEditorState state;
    private UiPreferences preferences;
    private int selected, dragging = -1;
    private boolean resetHeld, closed, pointerFocusPending;
    public UiColorEditor(Bounds area, Component name, int initialArgb, int defaultArgb,
                         UiPreferences preferences, IntConsumer onPreview, IntConsumer onCommit) {
        super(area.x(), area.y(), area.width(), area.height(), Objects.requireNonNull(name));
        if (area.width() < MIN_WIDTH || area.height() < MIN_HEIGHT) throw new IllegalArgumentException("Color editor requires at least 180 x 132 GUI units");
        this.preferences = Objects.requireNonNull(preferences);
        state = new ColorEditorState(initialArgb, defaultArgb, onPreview, onCommit);
        setTooltip(Tooltip.create(Component.translatable(KEY + "usage")));
    }
    public ColorEditorState state() { return state; }
    public void setValue(int argb) { pointerFocusPending = false; dragging = -1; resetHeld = false; state.setValue(argb); }
    public void setPreferences(UiPreferences preferences) { this.preferences = Objects.requireNonNull(preferences); }
    public int selectedChannel() { return selected; }
    public boolean isEditing() { return state.editing() || dragging >= 0 || resetHeld; }
    public boolean hasPendingChange() { return state.editing(); }
    /** Hosts should call before moving focus to an explicit Done action. */
    public void commitInteraction() { pointerFocusPending = false; dragging = -1; resetHeld = false; if (enabled()) state.commit(); else state.cancel(); }
    public boolean dragging() { return dragging >= 0; }
    @Override public boolean cancelInteraction() {
        boolean pending = isEditing(); pointerFocusPending = false;
        dragging = -1; resetHeld = false; state.cancel(); return pending;
    }
    @Override public boolean releasePointer(int button) {
        if (button != 0 || dragging < 0) return false;
        commitInteraction(); return true;
    }
    @Override public void close() { cancelInteraction(); closed = true; }
    private boolean enabled() { return !closed && active && visible && width >= MIN_WIDTH && height >= MIN_HEIGHT; }
    @Override public void setFocused(boolean focused) { super.setFocused(focused); if (focused) pointerFocusPending = false;
        else if (!pointerFocusPending && state != null) cancelInteraction(); }
    private int rampX() { return getX() + 62; }
    private int rampWidth() { return width - 72; }
    private int rampY(int channel) { return getY() + 30 + channel * 20; }
    private int hit(double x, double y) {
        if (!enabled()) return -1;
        for (int i = 0; i < 4; i++) if (x >= rampX() && x < rampX() + rampWidth() && y >= rampY(i) && y < rampY(i) + 14) return i;
        return resetHit(x, y) ? 4 : -1;
    }
    private boolean resetHit(double x, double y) { return x >= getX() + 8 && x < getX() + width - 8 && y >= getY() + 112 && y < getY() + 128; }
    private void previewPointer(double x) { state.preview(dragging, (float)((x - rampX()) / (rampWidth() - 1))); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        int target = hit(x, y); if (target < 0) return false;
        cancelInteraction(); selected = target; pointerFocusPending = true;
        if (target == 4) resetHeld = true;
        else { dragging = target; previewPointer(x); }
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != 0 || (dragging < 0 && !resetHeld)) return false;
        pointerFocusPending = false;
        if (!enabled()) { cancelInteraction(); return true; }
        if (dragging >= 0) previewPointer(x);
        else if (!resetHit(x, y)) resetHeld = false;
        return true;
    }
    @Override public boolean releasePointer(double x, double y, int button) { return mouseReleased(x, y, button); }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button != 0 || (dragging < 0 && !resetHeld)) return false;
        pointerFocusPending = false;
        if (!enabled()) { cancelInteraction(); return true; }
        if (dragging >= 0) { previewPointer(x); dragging = -1; state.commit(); }
        else { resetHeld = false; if (resetHit(x, y)) state.reset(); }
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && (state.editing() || dragging >= 0 || resetHeld)) { cancelInteraction(); return true; }
        if (!enabled() || !isFocused()) { if (!enabled()) cancelInteraction(); return false; }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            selected = Math.floorMod(selected + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 5); return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            dragging = -1; resetHeld = false;
            if (selected == 4) state.reset(); else state.commit(); return true;
        }
        if (selected == 4) return false;
        float step = selected == 0 ? 1f / 360 : 1f / 100;
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) step *= 10;
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            state.preview(selected, state.channel(selected) + (key == GLFW.GLFW_KEY_LEFT ? -step : step)); return true;
        }
        if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) { state.preview(selected, key == GLFW.GLFW_KEY_HOME ? 0 : 1); return true; }
        return false;
    }
    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        pointerFocusPending = false;
        if (resetHeld && !resetHit(mx, my)) resetHeld = false;
        if (!enabled() || !Minecraft.getInstance().isWindowActive()) cancelInteraction();
        if (width < MIN_WIDTH || height < MIN_HEIGHT || closed) return;
        int ink = active ? DarkModeTheme.textColor() : DarkModeTheme.mutedTextColor();
        DarkModeTheme.rounded(g, getX(), getY(), width, height, 5, 1,
                isFocused() ? DarkModeTheme.accentColor() : DarkModeTheme.borderColor(), DarkModeTheme.backgroundColor() | 0xff000000);
        var font = Minecraft.getInstance().font;
        checker(g, getX() + 8, getY() + 7, 22, 16);
        g.fill(getX() + 8, getY() + 7, getX() + 30, getY() + 23, state.color());
        g.drawString(font, HsvColor.format(state.color()), getX() + 36, getY() + 10, ink, false);
        for (int row = 0; row < 4; row++) {
            int x = rampX(), y = rampY(row), w = rampWidth();
            var label = Component.translatable(KEY + CHANNELS[row]);
            g.drawString(font, font.plainSubstrByWidth(label.getString(), 50), getX() + 8, y + 3, ink, false);
            if (row == 3) checker(g, x, y, w, 14);
            int steps = Math.min(w, 128);
            for (int i = 0; i < steps; i++) {
                int left = x + i * w / steps, right = x + (i + 1) * w / steps;
                g.fill(left, y, right, y + 14, state.rampColor(row, i / (float)(steps - 1)));
            }
            int thumb = x + Math.round(state.channel(row) * (w - 1));
            g.fill(Math.max(x, thumb - 2), y, Math.min(x + w, thumb + 3), y + 14, 0xff000000);
            g.fill(Math.max(x, thumb - 1), y + 1, Math.min(x + w, thumb + 2), y + 13, 0xffffffff);
            if (isFocused() && selected == row) g.fill(x, y + 16, x + w, y + 18, ink);
        }
        int resetY = getY() + 112;
        DarkModeTheme.rounded(g, getX() + 8, resetY, width - 16, 16, 3, 1,
                isFocused() && selected == 4 ? ink : DarkModeTheme.borderColor(), resetHeld ? DarkModeTheme.accentColor() : DarkModeTheme.surfaceColor());
        g.drawString(font, font.plainSubstrByWidth(Component.translatable(KEY + "reset").getString(), width - 24), getX() + 12, resetY + 4, ink, false);
    }
    private static void checker(GuiGraphics g, int x, int y, int width, int height) {
        // Fixed budget even for unusually wide hosts.
        int columns = Math.min(64, Math.max(1, width / 6));
        for (int row = 0; row < 3; row++) for (int col = 0; col < columns; col++)
            g.fill(x + col * width / columns, y + row * height / 3, x + (col + 1) * width / columns,
                    y + (row + 1) * height / 3, ((row + col) & 1) == 0 ? 0xff555555 : 0xff999999);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        Component channel = Component.translatable(KEY + (selected == 4 ? "reset" : CHANNELS[selected]));
        String value = selected == 4 ? HsvColor.format(state.defaultColor()) : Math.round(state.channel(selected) * (selected == 0 ? 360 : 100)) + (selected == 0 ? "°" : "%");
        output.add(NarratedElementType.TITLE, Component.empty().append(getMessage()).append(". ").append(channel).append(" " + value + ". " + HsvColor.format(state.color())));
        output.add(NarratedElementType.USAGE, Component.translatable(KEY + "usage"));
    }
}
