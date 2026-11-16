// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

/**
 * One reversible clock for a panel and its staggered layers. Sample once per frame,
 * then read any number of windows. Inspired by MuoniumPlayer SheetClock's shared
 * timeline design; implemented independently for SHBUI with an injected clock,
 * reduced-motion support and explicit reopening. No rendering or input ownership.
 */
public final class UiLayeredMotion implements AutoCloseable {
    private final double openMs, closeMs;
    private double position;
    private long lastMs;
    private boolean clockStarted, shown, disposed;

    public UiLayeredMotion(long openMs, long closeMs) {
        if (openMs <= 0 || closeMs <= 0) throw new IllegalArgumentException("Durations must be positive");
        this.openMs = openMs; this.closeMs = closeMs;
    }

    /** Changes direction without resetting the current position. */
    public void setShown(boolean value, long nowMs, boolean reducedMotion) {
        if (disposed) return;
        if (clockStarted && shown == value) {
            if (reducedMotion) position = shown ? 1 : 0;
            return;
        }
        shown = value;
        lastMs = clockStarted ? Math.max(lastMs, nowMs) : nowMs; clockStarted = true;
        if (reducedMotion) position = shown ? 1 : 0;
    }

    /** Caller supplies a monotonic millisecond clock; repeated/backward samples do not advance. */
    public float tick(long nowMs, double speed, boolean reducedMotion) {
        if (disposed) return 0;
        if (!clockStarted) { lastMs = nowMs; clockStarted = true; }
        double elapsed = nowMs > lastMs ? Math.min(250d, (double) nowMs - (double) lastMs) : 0;
        if (nowMs > lastMs) lastMs = nowMs;
        if (reducedMotion) position = shown ? 1 : 0;
        else {
            double rate = Double.isFinite(speed) ? Math.max(.1d, Math.min(8d, speed)) : 1d;
            double step = elapsed * rate / (shown ? openMs : closeMs);
            position = Math.max(0, Math.min(1, position + (shown ? step : -step)));
        }
        return progress();
    }

    public float rawProgress() { return (float) position; }
    public float progress() { return ease(position); }
    public boolean shown() { return shown; }
    public boolean settled() { return disposed || position == (shown ? 1d : 0d); }

    /** A bounded stagger window; invalid, inverted and non-finite ranges are rejected. */
    public float layer(double from, double to) { return ease(window(position, from, to)); }
    public static double window(double value, double from, double to) {
        if (!Double.isFinite(from) || !Double.isFinite(to) || from < 0 || to > 1 || from >= to)
            throw new IllegalArgumentException("Layer window must satisfy 0 <= from < to <= 1");
        if (!Double.isFinite(value)) return 0;
        return Math.max(0, Math.min(1, (value - from) / (to - from)));
    }
    private static float ease(double value) { return (float) (value * value * (3 - 2 * value)); }

    /** Re-layout may settle immediately without a second entrance animation. */
    public void finish() { if (!disposed) position = shown ? 1 : 0; }
    @Override public void close() { disposed = true; shown = false; position = 0; clockStarted = false; }
}
