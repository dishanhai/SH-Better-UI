package com.muoniumplayer.core.screens.ncm;

/** Mouse/trackpad browsing owns the document camera temporarily, without changing playback. */
final class AppleLyricScroll {
    static final long FOLLOW_DELAY = 3_000_000_000L;
    private static final double WHEEL_STEP = 38.0;

    private double destination;
    private long lastInput;
    private boolean browsing;
    private boolean pressed;
    private boolean dragging;
    private boolean rejected;
    private double downX, downY, lastY, slop;

    void wheel(int units, double camera, long now) {
        if (units == 0) return;
        begin(camera, now);
        destination -= Math.max(-3.0, Math.min(3.0, units / 120.0)) * WHEEL_STEP;
    }

    void press(double x, double y, double slop) {
        this.slop = slop;
        pressed = true;
        rejected = dragging = false;
        downX = x;
        downY = lastY = y;
    }

    boolean move(double x, double y, double camera, long now) {
        if (!pressed || rejected) return false;
        if (!dragging) {
            double dx = Math.abs(x - downX);
            double dy = Math.abs(y - downY);
            if (Math.hypot(dx, dy) <= slop) return false;
            if (dx >= dy) {
                rejected = true;
                return false;
            }
            begin(camera, now);
            dragging = true;
        } else {
            lastInput = now;
        }
        destination -= y - lastY;
        lastY = y;
        return true;
    }

    void end(long now) {
        if (dragging) lastInput = now;
        pressed = dragging = rejected = false;
    }

    boolean ownsPress() {
        return pressed;
    }

    boolean isBrowsing(long now) {
        return browsing && (dragging || now - lastInput < FOLLOW_DELAY);
    }

    double target(double follow, double low, double high, long now) {
        if (!isBrowsing(now)) {
            browsing = false;
            return follow;
        }
        destination = Math.max(low, Math.min(high, destination));
        return destination;
    }

    void rebase(double shift) {
        destination += shift;
    }

    void reset() {
        browsing = pressed = dragging = rejected = false;
    }

    private void begin(double camera, long now) {
        if (!isBrowsing(now)) destination = camera;
        browsing = true;
        lastInput = now;
    }
}
