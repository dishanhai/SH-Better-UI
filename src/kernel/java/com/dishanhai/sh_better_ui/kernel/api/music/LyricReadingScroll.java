// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

/** Document-space reading position. Reflow above the reading anchor moves both ends of a wheel animation. */
public final class LyricReadingScroll {
    private static final long IDLE_NANOS = 3_000_000_000L;

    private boolean browsing;
    private double position, target;
    private long lastFrame, lastInteraction;

    public boolean browsing() { return browsing; }
    public void follow() { browsing = false; }

    /** Resolve the browsing mode before measuring the frame's anchor. Held gestures renew it without movement. */
    public void resumeIfIdle(long nowNanos, boolean interacting) {
        if (!browsing) return;
        if (interacting) lastInteraction = nowNanos;
        else if (nowNanos - lastInteraction >= IDLE_NANOS) follow();
    }

    public double sample(double playback, double first, double last, double layoutShift,
                         double delta, boolean immediate, long nowNanos) {
        resumeIfIdle(nowNanos, delta != 0);
        last = Math.max(first, last);
        if (!browsing) {
            position = target = clamp(playback, first, last);
            if (delta == 0 || clamp(target + delta, first, last) == target) return playback;
            browsing = true;
            lastInteraction = nowNanos;
            lastFrame = nowNanos - 16_000_000L;
        } else {
            position = clamp(position + layoutShift, first, last);
            target = clamp(target + layoutShift, first, last);
        }
        if (immediate && delta != 0) target = position;
        target = clamp(target + delta, first, last);
        double dt = Math.max(0, Math.min(.1, (nowNanos - lastFrame) * 1e-9));
        lastFrame = nowNanos;
        position = immediate || Math.abs(target - position) < .05 ? target
                : position + (target - position) * -Math.expm1(-dt / .075);
        return position;
    }

    private static double clamp(double value, double first, double last) {
        return Math.max(first, Math.min(last, value));
    }
}
