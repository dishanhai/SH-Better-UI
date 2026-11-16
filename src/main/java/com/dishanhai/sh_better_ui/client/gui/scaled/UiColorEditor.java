// Author: dishanhai. Original native implementation inspired by MuoniumPlayer ThemePaletteOverlay's preview/commit interaction.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
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
    public static final int MIN_WIDTH = 180, MIN_HEIGHT = 132, RGB_MIN_HEIGHT = 112;
    private final boolean alphaEditable;
    private static final String KEY = "sh_better_ui.color_editor.";
    private static final String[] CHANNELS = {"hue", "saturation", "value", "alpha"};
    private final ColorEditorState state;
    private UiPreferences preferences;
    private int selected, dragging = -1;
    private boolean resetHeld, closed, pointerFocusPending;
    public UiColorEditor(Bounds area, Component name, int initialArgb, int defaultArgb,
                         UiPreferences preferences, IntConsumer onPreview, IntConsumer onCommit) {
        this(area, name, initialArgb, defaultArgb, true, preferences, onPreview, onCommit);
    }
    /** RGB-only mode hides alpha and normalizes all host values and callbacks to opaque ARGB. */
    public UiColorEditor(Bounds area, Component name, int initialArgb, int defaultArgb, boolean alphaEditable,
                         UiPreferences preferences, IntConsumer onPreview, IntConsumer onCommit) {
        super(area.x(), area.y(), area.width(), area.height(), Objects.requireNonNull(name));
        this.alphaEditable = alphaEditable;
        if (area.width() < MIN_WIDTH || area.height() < minimumHeight())
            throw new IllegalArgumentException("Color editor requires at least 180 x " + minimumHeight() + " GUI units");
        setPreferences(preferences);
        state = new ColorEditorState(initialArgb, defaultArgb, alphaEditable, onPreview, onCommit);
        setTooltip(Tooltip.create(Component.translatable(KEY + "usage")));
    }
    public boolean alphaEditable() { return alphaEditable; }
    public int channelCount() { return alphaEditable ? 4 : 3; }
    private int minimumHeight() { return alphaEditable ? MIN_HEIGHT : RGB_MIN_HEIGHT; }
    private int resetY() { return getY() + 32 + channelCount() * 20; }
    String formatColor(int color) { return alphaEditable ? HsvColor.format(color) : String.format(java.util.Locale.ROOT, "#%06X", color & 0xffffff); }
    public ColorEditorState state() { return state; }
    public void setValue(int argb) { pointerFocusPending = false; dragging = -1; resetHeld = false; state.setValue(argb); }
    public void setPreferences(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
        PlatformUiApi.setTooltipDelay(this, preferences.feedback().tooltipDelayMs());
    }
    record Paint(int background, int text, int border, int resetFill, int resetText, int stroke) {}
    /** High-contrast chrome stays opaque and readable without altering the edited color ramps. */
    Paint paint() {
        if (preferences.highContrast()) {
            int background = UiTheme.current().light() ? 0xffffffff : 0xff000000;
            int text = UiTheme.current().light() ? 0xff000000 : 0xffffffff;
            return new Paint(background, text, text, resetHeld ? text : background,
                    resetHeld ? background : text, isFocused() ? 2 : 1);
        }
        int text = active ? DarkModeTheme.textColor() : DarkModeTheme.mutedTextColor();
        return new Paint(DarkModeTheme.backgroundColor() | 0xff000000, text,
                isFocused() ? DarkModeTheme.accentColor() : DarkModeTheme.borderColor(),
                resetHeld ? DarkModeTheme.accentColor() : DarkModeTheme.surfaceColor(), text, 1);
    }
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
    private boolean enabled() { return !closed && active && visible && width >= MIN_WIDTH && height >= minimumHeight(); }
    @Override public void setFocused(boolean focused) { super.setFocused(focused); if (focused) pointerFocusPending = false;
        else if (!pointerFocusPending && state != null) cancelInteraction(); }
    private int rampX() { return getX() + 62; }
    private int rampWidth() { return width - 72; }
    private int rampY(int channel) { return getY() + 30 + channel * 20; }
    private int hit(double x, double y) {
        if (!enabled()) return -1;
        for (int i = 0; i < channelCount(); i++) if (x >= rampX() && x < rampX() + rampWidth() && y >= rampY(i) && y < rampY(i) + 14) return i;
        return resetHit(x, y) ? channelCount() : -1;
    }
    private boolean resetHit(double x, double y) { return x >= getX() + 8 && x < getX() + width - 8 && y >= resetY() && y < resetY() + 16; }
    private void previewPointer(double x) { state.preview(dragging, (float)((x - rampX()) / (rampWidth() - 1))); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        int target = hit(x, y); if (target < 0) return false;
        cancelInteraction(); selected = target; pointerFocusPending = true;
        if (target == channelCount()) resetHeld = true;
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
            selected = Math.floorMod(selected + (key == GLFW.GLFW_KEY_UP ? -1 : 1), channelCount() + 1); return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            dragging = -1; resetHeld = false;
            if (selected == channelCount()) state.reset(); else state.commit(); return true;
        }
        if (selected == channelCount()) return false;
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
        if (width < MIN_WIDTH || height < minimumHeight() || closed) return;
        var paint = paint();
        int ink = paint.text();
        DarkModeTheme.rounded(g, getX(), getY(), width, height, 5, paint.stroke(), paint.border(), paint.background());
        var font = Minecraft.getInstance().font;
        if (alphaEditable) checker(g, getX() + 8, getY() + 7, 22, 16);
        g.fill(getX() + 8, getY() + 7, getX() + 30, getY() + 23, state.color());
        g.drawString(font, formatColor(state.color()), getX() + 36, getY() + 10, ink, false);
        for (int row = 0; row < channelCount(); row++) {
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
        int resetY = resetY();
        DarkModeTheme.rounded(g, getX() + 8, resetY, width - 16, 16, 3,
                isFocused() && selected == channelCount() ? paint.stroke() : 1,
                preferences.highContrast() || isFocused() && selected == channelCount() ? ink : DarkModeTheme.borderColor(), paint.resetFill());
        g.drawString(font, font.plainSubstrByWidth(Component.translatable(KEY + "reset").getString(), width - 24), getX() + 12, resetY + 4, paint.resetText(), false);
    }
    private static void checker(GuiGraphics g, int x, int y, int width, int height) {
        // Fixed budget even for unusually wide hosts.
        int columns = Math.min(64, Math.max(1, width / 6));
        for (int row = 0; row < 3; row++) for (int col = 0; col < columns; col++)
            g.fill(x + col * width / columns, y + row * height / 3, x + (col + 1) * width / columns,
                    y + (row + 1) * height / 3, ((row + col) & 1) == 0 ? 0xff555555 : 0xff999999);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        Component channel = Component.translatable(KEY + (selected == channelCount() ? "reset" : CHANNELS[selected]));
        String value = selected == channelCount() ? formatColor(state.defaultColor()) : Math.round(state.channel(selected) * (selected == 0 ? 360 : 100)) + (selected == 0 ? "°" : "%");
        output.add(NarratedElementType.TITLE, Component.empty().append(getMessage()).append(". ").append(channel).append(" " + value + ". " + formatColor(state.color())));
        output.add(NarratedElementType.USAGE, Component.translatable(KEY + "usage"));
    }
}
