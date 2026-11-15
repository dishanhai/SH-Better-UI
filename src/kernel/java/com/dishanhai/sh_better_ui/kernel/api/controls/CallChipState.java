// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Client-thread state for a call indicator. The caller owns the operation and publishes its outcome. */
public final class CallChipState {
    public enum Status { IDLE, RUNNING, DONE, ERROR }

    public record Snapshot(Status status, long elapsedMs, float fill, float wash,
                           float shake, Status previousStatus, float glyphRoll) {}

    private static final float RUNNING_LIMIT = 0.9f;
    private static final int COMPLETION_MS = 200;
    private static final int WASH_MS = 400;
    private static final int ROLL_MS = 240;
    private static final int SHAKE_MS = 450;
    private static final float[] SHAKE = {0, -1, 1, -.66f, .66f, -.33f, 0};

    private final long expectedMs;
    private final LongSupplier clock;
    private Status status = Status.IDLE;
    private Status previousStatus = Status.IDLE;
    private long changedAt;
    private long startedAt;
    private long elapsedMs;
    private float resolvedFill;

    public CallChipState(long expectedMs) {
        this(expectedMs, () -> System.nanoTime() / 1_000_000);
    }

    CallChipState(long expectedMs, LongSupplier clock) {
        if (expectedMs <= 0) throw new IllegalArgumentException("expectedMs must be positive");
        this.expectedMs = expectedMs;
        this.clock = clock;
        changedAt = clock.getAsLong();
    }

    public Status status() {
        return status;
    }

    /** Publish a state change. Repeating the current status preserves its timer and animation. */
    public void setStatus(Status next) {
        Objects.requireNonNull(next);
        if (status == next) return;
        long now = clock.getAsLong();
        resolvedFill = fill(now);
        if (status == Status.RUNNING) elapsedMs = now - startedAt;
        if (next == Status.RUNNING) startedAt = now;
        if (next == Status.IDLE || next == Status.RUNNING) elapsedMs = 0;
        previousStatus = status;
        status = next;
        changedAt = now;
    }

    /** Running progress is an estimate. Reduced motion keeps the timer and uses static state colors. */
    public Snapshot sample(boolean reducedMotion) {
        long now = clock.getAsLong();
        long age = now - changedAt;
        float wash = status == Status.DONE
                ? (reducedMotion ? 1 : 1 - ease((age - COMPLETION_MS) / (float) WASH_MS)) : 1;
        float shake = status == Status.ERROR && !reducedMotion ? shakeAt(age) : 0;
        float roll = reducedMotion ? 1 : ease(age / (float) ROLL_MS);
        float fraction = reducedMotion ? switch (status) {
            case IDLE -> 0;
            case RUNNING, DONE, ERROR -> 1;
        } : fill(now);
        return new Snapshot(status, status == Status.RUNNING ? now - startedAt : elapsedMs,
                fraction, wash, shake, previousStatus, roll);
    }

    private float fill(long now) {
        return switch (status) {
            case IDLE -> 0;
            case RUNNING -> RUNNING_LIMIT * unit((now - startedAt) / (float) expectedMs);
            case DONE -> resolvedFill + (1 - resolvedFill) * ease((now - changedAt) / (float) COMPLETION_MS);
            case ERROR -> resolvedFill;
        };
    }

    private static float shakeAt(long age) {
        float position = unit(age / (float) SHAKE_MS) * (SHAKE.length - 1);
        int segment = Math.min((int) position, SHAKE.length - 2);
        return SHAKE[segment] + (SHAKE[segment + 1] - SHAKE[segment]) * ease(position - segment);
    }

    private static float ease(float fraction) {
        float rest = 1 - unit(fraction);
        return 1 - rest * rest * rest;
    }

    private static float unit(float fraction) {
        return Math.max(0, Math.min(1, fraction));
    }
}
