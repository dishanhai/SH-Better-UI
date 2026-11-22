// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.muoniumplayer.compat.lwjgl2.Keyboard;
import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.rendering.FoliaLucideIcons;
import com.muoniumplayer.core.screens.ncm.effects.EffectsLayout.Box;
import com.muoniumplayer.core.screens.ncm.effects.EffectsPaint;
import com.muoniumplayer.core.utils.cursor.CursorUtils;
import com.muoniumplayer.render.skija.SkijaGfx;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static com.dishanhai.sh_better_ui.kernel.internal.PlayerMenuStyle.*;

/** Anchored action list for an embedded Skija screen. The host owns its lifetime and target. */
public final class AnchoredActionMenu implements AutoCloseable {
    public record Entry(Component label, String icon, Runnable action) {}

    private EffectsPaint paint;
    private final PlayerMenuMotion motion = new PlayerMenuMotion();
    private List<Entry> entries = List.of();
    private final List<PlayerMenuMotion.Row> rowMotion = new ArrayList<>();
    private boolean open;
    private Box bounds;
    private Box visibleBounds;
    private float progress;
    private int focused;
    private int firstRow;
    private boolean keyboardFocus;
    private int pressedRow = -1;
    private boolean pressedOutside;

    public void open(List<Entry> actions) {
        entries = List.copyOf(actions);
        open = !entries.isEmpty();
        rowMotion.clear();
        for (Entry entry : entries) rowMotion.add(new PlayerMenuMotion.Row());
        visibleBounds = null;
        focused = firstRow = 0;
        keyboardFocus = true;
        cancelPress();
        if (isOpen()) MusicTypography.narrate(entries.get(focused).label());
    }

    public boolean isOpen() { return open; }
    public boolean isVisible() { return !entries.isEmpty(); }

    public void dismiss() {
        open = false;
        visibleBounds = null;
        cancelPress();
    }

    public void hide() {
        dismiss();
        motion.reset();
        progress = 0;
        entries = List.of();
        rowMotion.clear();
        bounds = null;
    }

    public void cancelPress() {
        pressedRow = -1;
        pressedOutside = false;
    }

    public void render(Box frame, Box anchor, double mouseX, double mouseY, boolean pressed, float alpha) {
        if (!isVisible()) return;
        var canvas = SkijaGfx.canvas();
        if (canvas == null) return;
        var preferences = UiPreferences.configured();
        boolean highContrast = preferences.highContrast();
        boolean still = preferences.reducedMotion() || highContrast;
        long now = System.nanoTime() / 1_000_000L;
        progress = motion.sample(open, now, still);
        if (!open && progress <= .002f) {
            hide();
            return;
        }
        if (paint == null) paint = new EffectsPaint();
        bounds = position(frame.inset(8), anchor, entries.size());
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            visibleBounds = null;
            return;
        }
        int visibleRows = visibleRows();
        firstRow = Math.min(firstRow, entries.size() - visibleRows);
        if (keyboardFocus) {
            firstRow = Math.max(focused - visibleRows + 1, Math.min(focused, firstRow));
        }
        visibleBounds = PlayerMenuMotion.frame(bounds, anchor.centerX(), anchor.centerY(), progress);
        int hovered = rowAt(mouseX, mouseY);
        if (hovered >= 0) CursorUtils.setOverride(CursorUtils.HAND);
        float scale = (float) (visibleBounds.width() / bounds.width());
        int saved = canvas.save();
        canvas.translate((float) visibleBounds.x(), (float) visibleBounds.y());
        canvas.scale(scale, scale);
        canvas.translate((float) -bounds.x(), (float) -bounds.y());
        try {
            panel(bounds, 1, progress * alpha, highContrast);
            for (int i = firstRow; i < firstRow + visibleRows; i++) {
                float reveal = PlayerMenuMotion.rowReveal(progress, i - firstRow);
                if (reveal <= .002f) continue;
                float rowAlpha = alpha * reveal;
                Box row = drawRow(i, reveal);
                boolean hover = i == hovered;
                float fill = rowMotion.get(i).sample(hover, hover && pressed && i == pressedRow, now, still);
                PlayerMenuStyle.row(row, rowAlpha * fill);
                if (keyboardFocus && i == focused) focus(row, rowAlpha, highContrast);
                double iconSize = Math.min(12, Math.min(row.height() - 4, row.width() - ROW_INSET * 2));
                if (iconSize <= 0) continue;
                Entry entry = entries.get(i);
                int ink = text(rowAlpha, highContrast);
                int iconInk = hover || keyboardFocus && i == focused ? accent(rowAlpha, highContrast) : ink;
                FoliaLucideIcons.draw(entry.icon(), row.x() + ROW_INSET + iconSize * .5, row.centerY(), iconSize, iconInk);
                double labelX = row.x() + ROW_INSET + iconSize + 6;
                paint.text(canvas, FontManager.pf12bold, entry.label().getString(), labelX, row.centerY(),
                        row.right() - ROW_INSET - labelX, ink);
            }
            if (visibleRows < entries.size()) {
                Box content = contentBounds();
                double thumb = content.height() * visibleRows / entries.size();
                double top = content.y() + content.height() * firstRow / entries.size();
                paint.round(canvas, new Box((bounds.right() + content.right()) * .5 - .75, top, 1.5, thumb),
                        .75, PlayerMenuStyle.text(alpha * progress * .48f, highContrast));
            }
        } finally {
            canvas.restoreToCount(saved);
        }
    }

    static Box position(Box frame, Box anchor, int count) {
        double width = Math.min(WIDTH, frame.width());
        double height = Math.min(PADDING * 2 + ROW_PITCH * (double) count - ROW_GAP, frame.height());
        double x = clamp(anchor.right() - width, frame.x(), frame.right() - width);
        double idealTop = anchor.y() - height - 5;
        if (idealTop < frame.y() && anchor.bottom() + 5 + height <= frame.bottom()) {
            idealTop = anchor.bottom() + 5;
        }
        return new Box(x, clamp(idealTop, frame.y(), frame.bottom() - height), width, height);
    }

    private Box row(int index) {
        Box content = contentBounds();
        return new Box(content.x(), content.y() + (index - firstRow) * ROW_PITCH,
                content.width(), Math.min(ROW_HEIGHT, content.height()));
    }

    private Box drawRow(int index, float reveal) {
        return row(index).offset(0, 3 * (1 - reveal));
    }

    private int visibleRows() {
        return bounds == null ? entries.size()
                : Math.min(entries.size(), Math.max(1, (int) ((contentBounds().height() + ROW_GAP) / ROW_PITCH)));
    }

    private Box contentBounds() {
        return bounds.inset(Math.min(PADDING, Math.min(bounds.width(), bounds.height()) * .25));
    }

    public void wheel(double x, double y, int delta) {
        if (delta == 0 || !open || visibleBounds == null || !visibleBounds.contains(x, y)) return;
        int visible = visibleRows();
        firstRow = (int) clamp(firstRow + (delta < 0 ? 1 : -1), 0, entries.size() - visible);
        focused = (int) clamp(focused, firstRow, firstRow + visible - 1);
        keyboardFocus = false;
        cancelPress();
    }

    public void press(double x, double y) {
        keyboardFocus = false;
        pressedRow = rowAt(x, y);
        pressedOutside = open && visibleBounds != null && !visibleBounds.contains(x, y);
    }

    public void release(double x, double y) {
        int row = rowAt(x, y);
        if (row >= 0 && row == pressedRow) choose(row);
        else if (pressedOutside && visibleBounds != null && !visibleBounds.contains(x, y)) dismiss();
        cancelPress();
    }

    private int rowAt(double x, double y) {
        if (open && visibleBounds != null && visibleBounds.contains(x, y)) {
            for (int i = firstRow; i < firstRow + visibleRows(); i++) {
                float reveal = PlayerMenuMotion.rowReveal(progress, i - firstRow);
                if (reveal >= .72f && PlayerMenuMotion.mapRow(drawRow(i, reveal), bounds, visibleBounds).contains(x, y)) {
                    return i;
                }
            }
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
        hide();
        entry.action().run();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override public void close() {
        hide();
        if (paint != null) {
            paint.close();
            paint = null;
        }
    }
}
