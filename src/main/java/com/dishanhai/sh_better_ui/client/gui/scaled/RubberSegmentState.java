// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Pointer and animation state shared by the native RubberSegment widgets. */
final class RubberSegmentState {
    private static final double DEAD_ZONE = 4;
    private final int count;
    private int selected;
    private int width;
    private int pressed = -1;
    private boolean grabbed;
    private boolean dragging;
    private double downX;
    private double grabOffset;
    private double grabWidth;
    private double lastX;
    private long lastTime;
    private double velocity;
    private Edges edges = new Edges(0, 0);
    private Edges origin = edges;
    private double animationStart;
    private int animationDuration;
    private boolean stretch;

    RubberSegmentState(int count, int selected) {
        this.count = count;
        this.selected = java.util.Objects.checkIndex(selected, count);
    }

    record Edges(double left, double right) {
        double center() { return (left + right) / 2; }
    }

    int selected() { return selected; }
    int preview(long now, boolean reduced) { return dragging ? landing(now, reduced) : selected; }
    int pressed() { return pressed; }

    int edge(int index) { return (int) ((long) width * index / count); }

    void resize(int width) {
        if (this.width == width) return;
        this.width = Math.max(0, width);
        select(selected);
    }

    void select(int index) {
        selected = java.util.Objects.checkIndex(index, count);
        pressed = -1;
        dragging = false;
        snap();
    }

    private Edges slot(int index) {
        return new Edges(edge(index), edge(index + 1));
    }

    boolean isDragging() { return dragging; }

    int indexAt(double x) {
        for (int i = 0; i < count - 1; i++) {
            if (x < edge(i + 1)) return i;
        }
        return count - 1;
    }

    void press(double x, long now, boolean reduced) {
        edges = sample(now, reduced);
        pressed = indexAt(x);
        grabbed = x >= edges.left && x < edges.right;
        dragging = false;
        downX = lastX = x;
        lastTime = now;
        velocity = 0;
        grabOffset = x - edges.left;
        grabWidth = edges.right - edges.left;
        if (grabbed) animationDuration = 0;
    }

    boolean drag(double x, long now, boolean reduced) {
        if (pressed < 0) return false;
        if (!grabbed || !dragging && Math.abs(x - downX) < DEAD_ZONE) return true;
        dragging = true;
        long elapsed = now - lastTime;
        if (elapsed >= 8) {
            velocity = elapsed > 100 ? 0 : UiMath.clamp((x - lastX) / elapsed, -2, 2);
            lastX = x;
            lastTime = now;
        }
        double left = x - grabOffset;
        double limit = Math.max(0, width - grabWidth);
        double clamped = UiMath.clamp(left, 0, limit);
        double excess = left - clamped;
        double compression = reduced ? 0 : Math.abs(excess) * grabWidth * 0.55
                / (grabWidth + 0.55 * Math.abs(excess));
        edges = new Edges(clamped + (excess > 0 ? compression : 0),
                clamped + grabWidth - (excess < 0 ? compression : 0));
        return true;
    }

    private int landing(long now, boolean reduced) {
        double speed = now - lastTime <= 100 ? velocity : 0;
        double projection = !reduced && Math.abs(speed) > 0.11 ? speed * 90 : 0;
        return indexAt(edges.center() + projection);
    }

    int release(double x, boolean inside, long now, boolean reduced) {
        if (pressed < 0) return selected;
        if (x != lastX) drag(x, now, reduced);
        int next = dragging ? landing(now, reduced)
                : inside && Math.abs(x - downX) <= 10 && indexAt(x) == pressed ? pressed : selected;
        edges = sample(now, reduced);
        boolean tap = !dragging && next != selected;
        pressed = -1;
        dragging = false;
        selected = next;
        if (reduced) {
            snap();
        } else {
            origin = edges;
            stretch = tap;
            animationStart = now;
            animationDuration = tap ? 440 : 260;
        }
        return next;
    }

    boolean cancel() {
        if (pressed < 0) return false;
        select(selected);
        return true;
    }

    private void snap() {
        edges = slot(selected);
        animationDuration = 0;
    }

    Edges sample(long now, boolean reduced) {
        if (dragging) return edges;
        if (reduced) {
            snap();
            return edges;
        }
        if (animationDuration == 0) return edges;
        double elapsed = now - animationStart;
        if (elapsed >= animationDuration) {
            snap();
            return edges;
        }
        Edges target = slot(selected);
        boolean toRight = target.center() > origin.center();
        double squash = Math.min(3, (target.right - target.left) / 4);
        if (!stretch) return edges = blend(origin, target, ease(elapsed / animationDuration));
        Edges wide = new Edges(Math.min(origin.left, target.left), Math.max(origin.right, target.right));
        Edges compressed = toRight ? new Edges(target.left + squash, target.right)
                : new Edges(target.left, target.right - squash);
        if (elapsed < 150) return edges = blend(origin, wide, ease(elapsed / 150));
        if (elapsed < 320) return edges = blend(wide, compressed, ease((elapsed - 150) / 170));
        return edges = blend(compressed, target, ease((elapsed - 320) / 120));
    }

    private static double ease(double t) {
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private static Edges blend(Edges from, Edges to, double t) {
        return new Edges(from.left + (to.left - from.left) * t, from.right + (to.right - from.right) * t);
    }
}
