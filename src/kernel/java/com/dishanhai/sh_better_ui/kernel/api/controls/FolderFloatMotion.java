// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import java.util.ArrayList;
import java.util.List;

/** Folder Float 的标签排布、展开和无重力运动，坐标相对于标签区。 */
public final class FolderFloatMotion {
    public static final int PILL_HEIGHT = 20;
    private static final int GAP = 6;
    private static final double DRAG_THRESHOLD = 4;
    private static final double MAX_SPEED = 420;

    public record Pill(float x, float y, int width, float progress) {
        public boolean contains(double px, double py) {
            return progress > 0 && px >= x && px < x + width && py >= y && py < y + PILL_HEIGHT;
        }
    }

    private final List<Body> bodies = new ArrayList<>();
    private final int width, height;
    private boolean open;
    private double elapsed, flap, simulationTime;
    private long lastFrame = -1;
    private int pressed = -1;
    private boolean dragged;
    private double pressX, pressY, originX, originY;
    private long dragTime;

    public FolderFloatMotion(int width, int height, int[] labelWidths, boolean open) {
        if (width < 1 || height < PILL_HEIGHT) throw new IllegalArgumentException("Folder Float area");
        this.width = width;
        this.height = height;
        this.open = open;
        var rows = new ArrayList<List<Body>>();
        var row = new ArrayList<Body>();
        int used = 0;
        for (int labelWidth : labelWidths) {
            int pillWidth = Math.max(1, Math.min(width, labelWidth));
            if (used + pillWidth > width && !row.isEmpty()) {
                rows.add(row);
                row = new ArrayList<>();
                used = 0;
            }
            var body = new Body(pillWidth, open ? 1 : 0);
            row.add(body);
            bodies.add(body);
            used += pillWidth + GAP;
        }
        if (!row.isEmpty()) rows.add(row);
        double pitch = Math.min(PILL_HEIGHT + GAP, (height - PILL_HEIGHT) / (double) Math.max(1, rows.size() - 1));
        for (int index = 0; index < rows.size(); index++) {
            var entries = rows.get(index);
            int rowWidth = entries.stream().mapToInt(body -> body.width).sum() + (entries.size() - 1) * GAP;
            double x = (width - rowWidth) / 2.0;
            for (var body : entries) {
                body.x = body.restX = x;
                body.y = body.restY = height - PILL_HEIGHT - index * pitch;
                x += body.width + GAP;
            }
        }
        flap = open ? 1 : 0;
    }

    public boolean isOpen() { return open; }
    public boolean isPressed() { return pressed >= 0; }
    public int pressedIndex() { return pressed; }
    public float flap() { return (float) flap; }

    public void setOpen(boolean open) {
        if (this.open == open) return;
        cancel();
        this.open = open;
        elapsed = 0;
    }

    /** 更新当前帧。暂停时保留画面并丢弃间隔，恢复后从当前姿态继续。 */
    public void advance(long now, boolean physics, double drift, boolean reducedMotion) {
        double step = lastFrame < 0 ? 0 : clamp((now - lastFrame) / 1000.0, 0, 0.05);
        lastFrame = now;
        elapsed += step;
        flap = approach(flap, open ? 1 : 0, reducedMotion ? 1 : step / 0.24);
        boolean settled = open;
        for (int index = 0; index < bodies.size(); index++) {
            var body = bodies.get(index);
            int order = open ? index : bodies.size() - 1 - index;
            double delay = Math.min(0.24, order * 0.038);
            double available = Math.min(step, Math.max(0, elapsed - delay));
            body.progress = approach(body.progress, open ? 1 : 0,
                    reducedMotion ? 1 : available / (open ? 0.44 : 0.25));
            settled &= body.progress == 1;
            if (body.progress == 0 || reducedMotion || !physics) body.reset();
        }
        if (!settled || !physics || reducedMotion) return;
        while (step > 0.00001) {
            double delta = Math.min(1.0 / 120, step);
            simulate(delta, drift);
            step -= delta;
        }
    }

    public void pause() {
        cancel();
        lastFrame = -1;
    }

    public Pill pill(int index) {
        var body = bodies.get(index);
        double t = body.progress - 1;
        double eased = 1 + 2.1 * t * t * t + 1.1 * t * t;
        double x = (width - body.width) / 2.0 + (body.x - (width - body.width) / 2.0) * eased;
        double y = height + (body.y - height) * eased;
        return new Pill((float) clamp(x, 0, width - body.width),
                (float) Math.max(0, y), body.width, (float) body.progress);
    }

    public int hit(double x, double y) {
        if (!open || x < 0 || x >= width || y < 0 || y >= height) return -1;
        for (int i = 0; i < bodies.size(); i++) {
            if (pill(i).contains(x, y)) return i;
        }
        return -1;
    }

    public boolean press(double x, double y, long now) {
        if (isPressed()) return false;
        pressed = hit(x, y);
        if (pressed < 0) return false;
        var body = bodies.get(pressed);
        pressX = x;
        pressY = y;
        originX = body.x;
        originY = body.y;
        body.vx = body.vy = 0;
        dragTime = now;
        return true;
    }

    public void drag(double x, double y, long now, boolean enabled) {
        if (!isPressed()) return;
        double dx = x - pressX, dy = y - pressY;
        var body = bodies.get(pressed);
        dragged |= Math.hypot(dx, dy) >= DRAG_THRESHOLD;
        if (!dragged || !enabled || body.progress != 1) return;
        double nextX = clamp(originX + dx, 0, width - body.width);
        double nextY = clamp(originY + dy, 0, height - PILL_HEIGHT);
        double dt = Math.max(0.008, (now - dragTime) / 1000.0);
        body.vx = clamp((nextX - body.x) / dt, -MAX_SPEED, MAX_SPEED);
        body.vy = clamp((nextY - body.y) / dt, -MAX_SPEED, MAX_SPEED);
        body.x = nextX;
        body.y = nextY;
        dragTime = now;
    }

    /** 返回刚选中的索引，拖放或在标签外松开时返回 -1。 */
    public int release(double x, double y, long now, boolean dragEnabled) {
        if (!isPressed()) return -1;
        var body = bodies.get(pressed);
        double targetX = clamp(originX + x - pressX, 0, width - body.width);
        double targetY = clamp(originY + y - pressY, 0, height - PILL_HEIGHT);
        if (!dragged || targetX != body.x || targetY != body.y) drag(x, y, now, dragEnabled);
        if (now - dragTime > 90) body.vx = body.vy = 0;
        int selected = !dragged && hit(x, y) == pressed ? pressed : -1;
        pressed = -1;
        dragged = false;
        return selected;
    }

    public void cancel() {
        if (!isPressed()) return;
        var body = bodies.get(pressed);
        body.x = originX;
        body.y = originY;
        body.vx = body.vy = 0;
        pressed = -1;
        dragged = false;
    }

    private void simulate(double dt, double drift) {
        simulationTime += dt;
        double damping = Math.exp(-3.2 * dt);
        for (int i = 0; i < bodies.size(); i++) {
            if (i == pressed) continue;
            var body = bodies.get(i);
            double phase = i * 2.4;
            body.vx = (body.vx + Math.sin(simulationTime + phase) * drift * 34 * dt) * damping;
            body.vy = (body.vy + Math.cos(simulationTime * 1.4 + phase) * drift * 28 * dt) * damping;
            body.x += body.vx * dt;
            body.y += body.vy * dt;
            contain(body);
        }
        for (int i = 0; i < bodies.size(); i++) {
            for (int j = i + 1; j < bodies.size(); j++) {
                collide(bodies.get(i), bodies.get(j), i == pressed, j == pressed);
            }
        }
    }

    private void collide(Body a, Body b, boolean fixedA, boolean fixedB) {
        double dx = b.x + b.width / 2.0 - a.x - a.width / 2.0;
        double dy = b.y - a.y;
        double overlapX = (a.width + b.width) / 2.0 - Math.abs(dx);
        double overlapY = PILL_HEIGHT - Math.abs(dy);
        if (overlapX <= 0 || overlapY <= 0) return;
        boolean horizontal = overlapX < overlapY;
        double normal = (horizontal ? dx : dy) < 0 ? -1 : 1;
        double separation = (horizontal ? overlapX : overlapY) * normal;
        double relativeSpeed = horizontal ? b.vx - a.vx : b.vy - a.vy;
        double movable = fixedA || fixedB ? 1 : 2;
        double impulse = Math.min(0, relativeSpeed * normal) * 1.55 / movable * normal;
        if (!fixedA) separate(a, horizontal, -separation / movable, impulse);
        if (!fixedB) separate(b, horizontal, separation / movable, -impulse);
    }

    private void separate(Body body, boolean horizontal, double distance, double impulse) {
        if (horizontal) {
            body.x += distance;
            body.vx += impulse;
        } else {
            body.y += distance;
            body.vy += impulse;
        }
        contain(body);
    }

    private void contain(Body body) {
        double x = clamp(body.x, 0, width - body.width);
        double y = clamp(body.y, 0, height - PILL_HEIGHT);
        if (x != body.x) body.vx *= -0.55;
        if (y != body.y) body.vy *= -0.55;
        body.x = x;
        body.y = y;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double approach(double value, double target, double step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    private static final class Body {
        final int width;
        double progress, restX, restY, x, y, vx, vy;

        Body(int width, double progress) {
            this.width = width;
            this.progress = progress;
        }

        void reset() {
            x = restX;
            y = restY;
            vx = vy = 0;
        }
    }
}
