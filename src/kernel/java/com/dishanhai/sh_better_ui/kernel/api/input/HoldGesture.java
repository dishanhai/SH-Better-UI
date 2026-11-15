// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.input;

import java.util.Objects;

/** Time-based confirmation state. The host validates input capture before advancing the gesture. */
public final class HoldGesture {
    public record Timing(int holdMs, int releaseMs, int resetAfterMs) {
        public static final Timing DEFAULT = new Timing(2000, 200, 1200);

        public Timing {
            if (holdMs <= 0 || releaseMs < 0 || resetAfterMs < 0) {
                throw new IllegalArgumentException("Invalid hold timing");
            }
        }
    }

    public enum Input { POINTER, KEYBOARD }
    public enum Result { NONE, TAP, CONFIRMED }

    private static final int TAP_MS = 250;
    private final Timing timing;
    private Input input;
    private long startedAt;
    private long completedAt;
    private boolean completed;
    private long releasedAt;
    private float releasedFill;

    public HoldGesture(Timing timing) {
        this.timing = Objects.requireNonNull(timing);
    }

    public Input input() { return input; }

    public boolean completed() { return completed; }

    public boolean begin(Input source, long now) {
        expire(now);
        if (input != null || completed) return false;
        input = Objects.requireNonNull(source);
        startedAt = now;
        releasedFill = 0;
        return true;
    }

    /** Returns CONFIRMED exactly once per uninterrupted hold. */
    public Result advance(long now) {
        expire(now);
        if (input == null || now - startedAt < timing.holdMs()) return Result.NONE;
        input = null;
        completed = true;
        completedAt = now;
        return Result.CONFIRMED;
    }

    public Result release(Input source, long now) {
        if (input == null || input != source) return Result.NONE;
        Result result = advance(now);
        if (result == Result.CONFIRMED) return result;
        boolean tap = now - startedAt < TAP_MS;
        cancel(now);
        return tap ? Result.TAP : Result.NONE;
    }

    public boolean cancel(long now) {
        if (input == null) return false;
        releasedFill = progress(now);
        releasedAt = now;
        input = null;
        return true;
    }

    /** Clears both a hold and a confirmed state. */
    public void reset() {
        input = null;
        completed = false;
        releasedFill = 0;
    }

    public float progress(long now) {
        if (completed) return 1;
        if (input != null) return fraction(now - startedAt, timing.holdMs());
        if (releasedFill == 0) return 0;
        float remaining = 1 - fraction(now - releasedAt, timing.releaseMs());
        return releasedFill * remaining * remaining * remaining;
    }

    public float completionPulse(long now) {
        if (!completed) return 0;
        float remaining = 1 - fraction(now - completedAt, 600);
        return remaining * remaining * remaining;
    }

    private void expire(long now) {
        if (!completed || timing.resetAfterMs() == 0 || now - completedAt < timing.resetAfterMs()) return;
        completed = false;
        releasedFill = 1;
        releasedAt = completedAt + timing.resetAfterMs();
    }

    private static float fraction(long elapsed, int duration) {
        return duration == 0 ? 1 : Math.max(0, Math.min(1, (float) elapsed / duration));
    }
}
