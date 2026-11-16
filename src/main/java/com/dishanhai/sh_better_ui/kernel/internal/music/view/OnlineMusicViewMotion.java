// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

/** UI-thread-only ornamental motion. It never schedules work or changes playback/layout. */
public final class OnlineMusicViewMotion implements AutoCloseable {
    public record Frame(float activity, float emphasis) {}
    private double activity, emphasis, pulseRemaining;
    private boolean playing, started, closed;
    private long lastMs;

    /** A short accent response. Repeated calls preserve the currently visible value. */
    public void pulse(long nowMs) {
        if (closed) return;
        advance(nowMs);
        pulseRemaining = 140;
    }

    /** Repeated/reversed timestamps do not advance animation. Paused playback settles to rest. */
    public Frame sample(long nowMs, boolean reducedMotion, boolean playing) {
        if (closed) return new Frame(0f, 0f);
        advance(nowMs);
        this.playing = playing;
        if (reducedMotion) {
            activity = playing ? 1 : 0;
            emphasis = 0;
            pulseRemaining = 0;
        }
        return new Frame((float)activity, (float)emphasis);
    }

    private void advance(long nowMs) {
        if (!started) { started = true; lastMs = nowMs; return; }
        if (nowMs <= lastMs) return;
        // Saturated subtraction prevents wrap; a stalled client never causes numerical instability.
        long delta;
        try { delta = Math.subtractExact(nowMs, lastMs); }
        catch (ArithmeticException overflow) { delta = Long.MAX_VALUE; }
        double elapsed = Math.min(1000L, delta);
        lastMs = nowMs;
        activity = approach(activity, playing ? 1 : 0, elapsed, 150);
        double attack = Math.min(elapsed, pulseRemaining);
        emphasis = approach(emphasis, 1, attack, 65);
        pulseRemaining -= attack;
        emphasis = approach(emphasis, 0, elapsed - attack, 190);
    }
    private static double approach(double value, double target, double elapsed, double timeConstant) {
        double next = target + (value - target) * Math.exp(-elapsed / timeConstant);
        return Math.abs(next - target) < .0001 ? target : Math.max(0, Math.min(1, next));
    }
    @Override public void close() {
        closed = true; playing = false; activity = emphasis = pulseRemaining = 0;
    }
}
