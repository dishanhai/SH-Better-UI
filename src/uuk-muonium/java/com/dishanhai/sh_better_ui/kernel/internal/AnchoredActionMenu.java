// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.muoniumplayer.compat.lwjgl2.Keyboard;
import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.rendering.FoliaLucideIcons;
import com.muoniumplayer.core.rendering.GlassLook;
import com.muoniumplayer.core.rendering.RGBA;
import com.muoniumplayer.core.screens.ncm.NCMScreen.ColorType;
import com.muoniumplayer.core.screens.ncm.effects.EffectsLayout.Box;
import com.muoniumplayer.core.screens.ncm.effects.EffectsPaint;
import com.muoniumplayer.core.utils.cursor.CursorUtils;
import com.muoniumplayer.render.skija.SkijaGfx;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.muoniumplayer.core.screens.ncm.NCMScreen.getColor;

/** Anchored action list for an embedded Skija screen. The host owns its lifetime and target. */
public final class AnchoredActionMenu implements AutoCloseable {
    public record Entry(Component label, String icon, Runnable action) {}

    private EffectsPaint paint;
    private List<Entry> entries = List.of();
    private Box bounds;
    private int focused;
    private int firstRow;
    private boolean keyboardFocus;
    private int pressedRow = -1;
    private boolean pressedOutside;

    public void open(List<Entry> actions) {
        entries = List.copyOf(actions);
        bounds = null;
        focused = firstRow = 0;
        keyboardFocus = true;
        if (isOpen()) MusicTypography.narrate(entries.get(focused).label());
    }

    public boolean isOpen() { return !entries.isEmpty(); }

    public void dismiss() {
        entries = List.of();
        bounds = null;
        cancelPress();
    }

    public void cancelPress() {
        pressedRow = -1;
        pressedOutside = false;
    }

    public void render(Box frame, Box anchor, double mouseX, double mouseY, boolean pressed, float alpha) {
        if (!isOpen()) return;
        var canvas = SkijaGfx.canvas();
        if (canvas == null) return;
        if (paint == null) paint = new EffectsPaint();
        bounds = position(frame.inset(8), anchor, entries.size());
        firstRow = Math.min(firstRow, entries.size() - visibleRows());
        boolean highContrast = UiPreferences.configured().highContrast();
        double radius = 10;
        boolean glass = !highContrast && GlassLook.paintFrost(GlassLook.Surface.PLAYER,
                bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius, alpha);
        int ink = RGBA.color(getColor(ColorType.PRIMARY_TEXT), alpha);
        int accent = RGBA.color(getColor(ColorType.ACCENT), alpha);
        paint.round(canvas, bounds, radius, RGBA.color(getColor(ColorType.GENERIC_BACKGROUND),
                alpha * (glass ? .74f : 1f)));
        paint.outline(canvas, bounds, radius, 1, highContrast ? ink : RGBA.color(ink, alpha * .28f));

        for (int i = firstRow; i < firstRow + visibleRows(); i++) {
            Box row = row(i);
            boolean hover = row.contains(mouseX, mouseY);
            if (hover) {
                CursorUtils.setOverride(CursorUtils.HAND);
                paint.round(canvas, row, 5, RGBA.color(ink, alpha * (pressed && i == pressedRow ? .24f : .12f)));
            }
            if (keyboardFocus && i == focused)
                paint.line(canvas, row.x() + 2, row.centerY() - 4, row.x() + 2, row.centerY() + 4,
                        1.5, highContrast ? ink : accent);
            Entry entry = entries.get(i);
            FoliaLucideIcons.draw(entry.icon(), row.x() + 12, row.centerY(), 13, ink);
            paint.text(canvas, FontManager.pf14, entry.label().getString(),
                    row.x() + 25, row.centerY(), row.width() - 29, ink);
        }
    }

    static Box position(Box frame, Box anchor, int count) {
        double width = Math.min(196, frame.width());
        double height = Math.min(12 + 25.0 * count, frame.height());
        double x = clamp(anchor.right() - width, frame.x(), frame.right() - width);
        double idealTop = anchor.y() - height - 5;
        if (idealTop < frame.y() && anchor.bottom() + 5 + height <= frame.bottom()) {
            idealTop = anchor.bottom() + 5;
        }
        return new Box(x, clamp(idealTop, frame.y(), frame.bottom() - height), width, height);
    }

    private Box row(int index) {
        Box content = bounds.inset(6);
        double height = content.height() / visibleRows();
        return new Box(content.x(), content.y() + (index - firstRow) * height, content.width(), height);
    }

    private int visibleRows() {
        return bounds == null ? entries.size() : Math.min(entries.size(), Math.max(1, (int) ((bounds.height() - 12) / 25)));
    }

    public void wheel(double x, double y, int delta) {
        if (delta == 0 || bounds == null || !bounds.contains(x, y)) return;
        int visible = visibleRows();
        firstRow = (int) clamp(firstRow + (delta < 0 ? 1 : -1), 0, entries.size() - visible);
        focused = (int) clamp(focused, firstRow, firstRow + visible - 1);
        keyboardFocus = false;
        cancelPress();
    }

    public void press(double x, double y) {
        keyboardFocus = false;
        pressedRow = rowAt(x, y);
        pressedOutside = bounds != null && !bounds.contains(x, y);
    }

    public void release(double x, double y) {
        int row = rowAt(x, y);
        if (row >= 0 && row == pressedRow) choose(row);
        else if (pressedOutside && bounds != null && !bounds.contains(x, y)) dismiss();
        cancelPress();
    }

    private int rowAt(double x, double y) {
        if (bounds != null && bounds.contains(x, y)) {
            for (int i = firstRow; i < firstRow + visibleRows(); i++) if (row(i).contains(x, y)) return i;
        }
        return -1;
    }

    public boolean key(int key, boolean backwards) {
        if (!isOpen()) return false;
        switch (key) {
            case Keyboard.KEY_ESCAPE -> dismiss();
            case Keyboard.KEY_RETURN, Keyboard.KEY_SPACE -> choose(focused);
            case Keyboard.KEY_UP -> move(-1);
            case Keyboard.KEY_DOWN -> move(1);
            case Keyboard.KEY_TAB -> move(backwards ? -1 : 1);
            default -> { }
        }
        return true;
    }

    private void move(int step) {
        keyboardFocus = true;
        focused = Math.floorMod(focused + step, entries.size());
        int visible = visibleRows();
        if (focused < firstRow) firstRow = focused;
        if (focused >= firstRow + visible) firstRow = focused - visible + 1;
        MusicTypography.narrate(entries.get(focused).label());
    }

    private void choose(int index) {
        Entry entry = entries.get(index);
        dismiss();
        entry.action().run();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override public void close() {
        dismiss();
        if (paint != null) {
            paint.close();
            paint = null;
        }
    }
}
