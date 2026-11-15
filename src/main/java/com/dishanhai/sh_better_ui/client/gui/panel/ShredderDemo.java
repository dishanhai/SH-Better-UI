// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMath;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Shared paper stack, drop targets, and strip animation for the native gallery previews. */
final class ShredderDemo {
    static final String TITLE = "sh_better_ui.panel.developer.gallery.sample.shredder";
    static final int MIN_HEIGHT = 86;
    private static final int GAP = 4;
    private static final int STATUS_HEIGHT = 12;
    private static final int SLIT_HEIGHT = 4;
    private static final int BITE = 10;
    private static final int FALL_MS = 460;
    private static final List<Integer> SAMPLE = List.of(1, 2, 3);

    private final List<Integer> rows = new ArrayList<>(SAMPLE);
    private List<Integer> undo;
    private Bounds area;
    private int selected;
    private int held = -1;
    private int target = -1;
    private int dragX;
    private int dragY;
    private double grabX;
    private double grabY;
    private boolean armed;
    private Feed feed;
    private Component status = text("hint");

    static Component text(String key, Object... args) {
        return Component.translatable("sh_better_ui.panel.developer.gallery.shredder." + key, args);
    }

    void layout(Bounds area) {
        cancel();
        this.area = area;
    }

    private int fallHeight() { return Math.min(48, Math.min(area.height() / 3, area.height() - 68)); }
    private int slitY() { return area.bottom() - STATUS_HEIGHT - fallHeight() - SLIT_HEIGHT; }
    private int rowHeight() { return Math.min(22, (slitY() - area.y()) / SAMPLE.size() - GAP); }
    private Bounds slot(int index) {
        int y = slitY() - (rows.size() - index) * (rowHeight() + GAP);
        return new Bounds(area.x() + 8, y, area.width() - 16, rowHeight());
    }

    boolean press(double x, double y) {
        if (held >= 0 || feed != null) return false;
        for (int index = 0; index < rows.size(); index++) {
            var row = slot(index);
            if (!row.contains(x, y)) continue;
            held = target = selected = index;
            grabX = x - row.x();
            grabY = y - row.y();
            dragX = row.x();
            dragY = row.y();
            return true;
        }
        return false;
    }

    boolean dragging() { return held >= 0; }

    void drag(double x, double y) {
        if (!dragging()) return;
        var row = slot(held);
        dragX = (int) Math.round(UiMath.clamp(x - grabX, area.x() + 2, area.right() - row.width() - 2));
        dragY = (int) Math.round(UiMath.clamp(y - grabY, area.y(), slitY() + BITE - row.height()));
        armed = area.contains(x, y) && x >= area.x() + 3 && x < area.right() - 3
                && y < slitY() + SLIT_HEIGHT + fallHeight()
                && y - grabY + row.height() >= slitY() + BITE;
        target = -1;
        if (area.contains(x, y) && y < slitY()) {
            double center = dragY + rowHeight() / 2.0;
            target = UiMath.clamp(held + (int) Math.round((center - row.y() - row.height() / 2.0)
                    / (rowHeight() + GAP)), 0, rows.size() - 1);
        }
    }

    boolean release(double x, double y, long now, boolean reducedMotion) {
        if (!dragging()) return false;
        drag(x, y);
        int from = held;
        int to = target;
        boolean shred = armed;
        held = target = -1;
        armed = false;
        if (shred) {
            beginFeed(now, reducedMotion, dragX, dragY);
            return true;
        }
        return reorder(from, to);
    }

    void cancelDrag() {
        held = target = -1;
        armed = false;
    }

    void cancel() {
        cancelDrag();
        if (feed != null && !feed.consumed) status = text("hint");
        feed = null;
    }

    boolean select(int direction) {
        if (dragging() || feed != null || rows.isEmpty()) return false;
        selected = UiMath.clamp(selected + direction, 0, rows.size() - 1);
        return true;
    }

    boolean move(int direction) {
        return !dragging() && feed == null && reorder(selected, selected + direction);
    }

    private boolean reorder(int from, int to) {
        if (from < 0 || from >= rows.size() || to < 0 || to >= rows.size() || from == to) return false;
        undo = List.copyOf(rows);
        int file = rows.remove(from);
        rows.add(to, file);
        selected = to;
        status = text("moved", name(file), to + 1);
        return true;
    }

    boolean shred(long now, boolean reducedMotion) {
        if (dragging() || feed != null || rows.isEmpty()) return false;
        var row = slot(selected);
        beginFeed(now, reducedMotion, row.x(), row.y());
        return true;
    }

    private void beginFeed(long now, boolean reducedMotion, int x, int y) {
        int file = rows.get(selected);
        if (reducedMotion) {
            consume(file);
            return;
        }
        int duration = Math.max(65, (slitY() - y) * 1000 / 180);
        feed = new Feed(file, x, y, now, duration);
        status = text("feeding", name(file));
    }

    private void consume(int file) {
        undo = List.copyOf(rows);
        rows.remove(selected);
        selected = Math.min(selected, Math.max(0, rows.size() - 1));
        status = text("shredded", name(file));
    }

    boolean advance(long now, boolean reducedMotion) {
        if (feed == null) return false;
        long elapsed = now - feed.start;
        boolean changed = !feed.consumed && (elapsed >= feed.duration || reducedMotion);
        if (changed) {
            consume(feed.file);
            feed.consumed = true;
        }
        if (elapsed >= feed.duration + FALL_MS || reducedMotion) feed = null;
        return changed;
    }

    boolean canUndo() { return undo != null && !dragging() && (feed == null || feed.consumed); }

    boolean undo() {
        if (!canUndo()) return false;
        int file = rows.isEmpty() ? 0 : rows.get(selected);
        rows.clear();
        rows.addAll(undo);
        undo = null;
        selected = Math.max(0, rows.indexOf(file));
        feed = null;
        status = text("restored");
        return true;
    }

    Component status() {
        if (armed) return text("armed");
        if (target >= 0 && target != held) return text("drop", target + 1);
        return status;
    }

    Component narration() {
        return rows.isEmpty() ? text("empty") : text("selection", name(rows.get(selected)), selected + 1, rows.size());
    }

    void paint(Painter painter, Palette colors, boolean focused, int mouseX, int mouseY, long now) {
        int slit = slitY();
        painter.box(area.x(), area.y(), area.width(), area.height(), 6, colors.border, colors.background);
        painter.clip(area.x() + 1, area.y() + 1, area.right() - 1, slit);
        for (int index = 0; index < rows.size(); index++) {
            if (index == held || (feed != null && !feed.consumed && index == selected)) continue;
            var row = slot(index);
            boolean highlighted = index == target || (held < 0 && (index == selected && focused || row.contains(mouseX, mouseY)));
            card(painter, colors, row.x(), row.y(), rows.get(index), highlighted);
        }
        if (dragging()) {
            if (!armed && target >= 0 && target != held) {
                var destination = slot(target);
                int marker = target < held ? destination.y() - 2 : destination.bottom() + 1;
                painter.fill(destination.x(), marker, destination.right(), marker + 1, colors.accent);
            }
            card(painter, colors, dragX, dragY, rows.get(held), true);
        } else if (feed != null && !feed.consumed) {
            card(painter, colors, feed.x, feedTop(now), feed.file, true);
        } else if (rows.isEmpty()) {
            painter.text(text("empty"), area.x() + 8, slit - 18, area.width() - 16, colors.text);
        }
        painter.unclip();

        if (feed != null) {
            painter.clip(area.x() + 1, slit + SLIT_HEIGHT, area.right() - 1, area.bottom() - STATUS_HEIGHT);
            strips(painter, colors, now);
            painter.unclip();
        }
        boolean feeding = armed || feed != null && !feed.consumed;
        int slitColor = feeding ? colors.accent : colors.border;
        painter.box(area.x() + 3, slit, area.width() - 6, SLIT_HEIGHT, 2, slitColor, slitColor);
        if (feeding) painter.fill(area.x() + 8, slit + 1, area.right() - 8, slit + 3, colors.background);
        painter.text(status(), area.x() + 6, area.bottom() - STATUS_HEIGHT,
                area.width() - 12, colors.text);
    }

    private void card(Painter painter, Palette colors, int x, int y, int file, boolean highlighted) {
        int width = area.width() - 16;
        int height = rowHeight();
        painter.box(x, y, width, height, 3, highlighted ? colors.accent : colors.border, colors.surface);
        if (highlighted) painter.fill(x + 1, y + 3, x + 3, y + height - 3, colors.accent);
        for (int dot = -1; dot <= 1; dot++) {
            int grip = y + height / 2 + dot * 3;
            painter.fill(x + 4, grip, x + 6, grip + 1, colors.text);
        }
        painter.text(name(file), x + 11, y + (height - 9) / 2, width - 16, colors.text);
    }

    private int feedTop(long now) {
        double remaining = 1 - UiMath.clamp((double) (now - feed.start) / feed.duration, 0, 1);
        return slitY() - (int) Math.round((slitY() - feed.y) * remaining * remaining);
    }

    private void strips(Painter painter, Palette colors, long now) {
        long elapsed = now - feed.start;
        double fall = UiMath.clamp((double) (elapsed - feed.duration) / FALL_MS, 0, 1);
        int length = Math.max(0, feedTop(now) + rowHeight() - slitY());
        int opacity = (int) Math.round(255 * (1 - fall * fall));
        int width = area.width() - 16;
        int count = Math.max(1, width / 9);
        for (int i = 0; i < count; i++) {
            int left = feed.x + i * width / count;
            int stripWidth = (i + 1) * width / count - i * width / count - 1;
            int fallY = (int) Math.round(fall * fall * (fallHeight() + (i % 4) * 8));
            for (int offset = 0; offset < length; offset += 2) {
                int sway = (int) Math.round(Math.sin(i * 1.7 + offset * 0.14 + elapsed * 0.012)
                        * (offset * 0.2 + fall * 6));
                int drift = (int) Math.round((i - (count - 1) / 2.0) * fall * 1.6);
                int x = left + sway + drift;
                int y = slitY() + SLIT_HEIGHT + fallY + offset;
                int color = offset == (length / 4) * 2 ? colors.text : colors.surface;
                painter.fill(x, y, x + stripWidth, y + Math.min(2, length - offset),
                        color & 0xFFFFFF | opacity << 24);
            }
        }
    }

    private static Component name(int file) { return text("file." + file); }

    private static final class Feed {
        private final int file, x, y, duration;
        private final long start;
        private boolean consumed;

        private Feed(int file, int x, int y, long start, int duration) {
            this.file = file;
            this.x = x;
            this.y = y;
            this.start = start;
            this.duration = duration;
        }
    }

    record Palette(int background, int surface, int border, int text, int accent) {}

    interface Painter {
        void fill(int left, int top, int right, int bottom, int color);
        void box(int x, int y, int width, int height, int radius, int edge, int fill);
        void text(Component text, int x, int y, int width, int color);
        void clip(int left, int top, int right, int bottom);
        void unclip();
    }
}
