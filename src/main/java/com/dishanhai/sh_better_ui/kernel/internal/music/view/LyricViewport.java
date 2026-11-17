// Author: dishanhai. Original native motion inspired by MP's lyric focus scrolling.
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

/** Bounded lyric follow state independent of playback. Manual browsing never seeks audio. */
final class LyricViewport {
    private double position, velocity;
    private int manual = -1;
    private long last, resume;
    private boolean started;
    void reset() { started = false; manual = -1; resume = 0; velocity = 0; }
    void scroll(int direction, int active, int size, long now) {
        long next = (long) (manual < 0 ? Math.max(0, active) : manual) + direction;
        browseTo((int) Math.max(0, Math.min(Integer.MAX_VALUE, next)), size, now);
    }
    void browseTo(int index, int size, long now) {
        if (size <= 0) return;
        manual = Math.max(0, Math.min(size - 1, index));
        resume = now > Long.MAX_VALUE - 5000 ? Long.MAX_VALUE : now + 5000;
        // Direct manipulation follows the pointer immediately; playback following is animated.
        position = manual; velocity = 0; started = true; last = now;
    }
    static boolean canAnimate(int height) { return height >= 72; }
    int scrollPosition(int active) { return Math.max(0, manual < 0 ? active : manual); }
    boolean browsing(long now) { return manual >= 0 && now < resume; }
    void follow() { manual = -1; resume = 0; velocity = 0; }
    double sample(int active, int size, long now, boolean reducedMotion, boolean focused) {
        if (size <= 0) { reset(); return 0; }
        if (focused && started) {
            // Keep keyboard targets stationary, including when a track's lyric count shrinks.
            position = Math.max(0, Math.min(size - 1, position));
            velocity = 0; last = now; return position;
        }
        if (manual >= 0 && now >= resume) follow();
        int target = Math.max(0, Math.min(size - 1, manual < 0 ? active : manual));
        if (!started || reducedMotion || Math.abs(target - position) > 6) {
            position = target; velocity = 0;
        } else {
            // Exact critically damped spring: no Euler instability or overshoot at variable FPS.
            // Bound the frame budget; a resume/stall never races through seconds of hidden motion.
            long delta = now > last ? now - last : 0;
            double seconds = Math.min(50, delta < 0 ? 50 : delta) / 1000d;
            double omega = 18, displacement = position - target;
            double carry = velocity + omega * displacement;
            double decay = Math.exp(-omega * seconds);
            position = target + (displacement + carry * seconds) * decay;
            velocity = (velocity - omega * carry * seconds) * decay;
            position = Math.max(0, Math.min(size - 1, position));
        }
        if (Math.abs(position - target) < .005 && Math.abs(velocity) < .01) {
            position = target; velocity = 0;
        }
        started = true; last = now;
        return position;
    }
}
