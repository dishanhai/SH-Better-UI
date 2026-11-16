package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Screen-owned radians. Tracks ordinary 4+ FPS frames in real time without pause catch-up. */
final class BlackHoleAnimationClock {
    private static final double TURN = 2 * Math.PI;
    private long lastTime;
    private boolean initialized, frozen;
    private double phase;

    void suspend() { initialized = false; }

    float advance(long now, boolean reducedMotion, float speed) {
        double rate = Float.isFinite(speed) ? Math.max(0, Math.min(2, speed)) : 0;
        boolean freeze = reducedMotion || rate == 0;
        if (initialized && !freeze && !frozen && now > lastTime) {
            long elapsed = now-lastTime;
            // Overflow/reversal and >250 ms gaps represent stalls or hidden screens,
            // not animation time. Normal 10 FPS and 60 FPS therefore advance equally.
            if (elapsed > 0 && elapsed <= 250) phase = (phase+elapsed*.001*rate)%TURN;
        }
        initialized = true;
        frozen = freeze;
        lastTime = now;
        float result = (float) phase;
        return result < TURN ? result : 0;
    }
}
