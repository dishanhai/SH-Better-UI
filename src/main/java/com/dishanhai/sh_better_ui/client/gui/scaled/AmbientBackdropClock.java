package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Screen-owned animation time: no catch-up after pauses, long stalls, or clock reversal. */
final class AmbientBackdropClock {
    private static final double TURN = Math.PI * 2;
    private long lastTime;
    private boolean initialized;
    private boolean frozen;
    private double phase;

    void suspend() { initialized = false; }

    float advance(long now, boolean reducedMotion, float speed) {
        if (initialized && !reducedMotion && !frozen && now > lastTime) {
            long difference = now - lastTime;
            // Subtraction can overflow only on an enormous positive jump. Preserve 1 ms deltas
            // near Long.MAX_VALUE instead of converting timestamps to imprecise doubles.
            double elapsedMs = difference < 0 ? 50d : Math.min(50L, difference);
            double rate = Float.isFinite(speed) ? Math.max(0f, Math.min(2f, speed)) : 0d;
            phase = (phase + elapsedMs * .001d * rate) % TURN;
        }
        initialized = true;
        frozen = reducedMotion;
        lastTime = now;
        // A double just below TURN can round up when converted to float.
        float result = (float) phase;
        return result < TURN ? result : 0f;
    }
}
