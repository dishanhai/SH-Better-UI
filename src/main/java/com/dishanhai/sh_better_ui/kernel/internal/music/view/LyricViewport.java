// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

/** Bounded lyric follow state independent of playback. Manual browsing never seeks audio. */
final class LyricViewport {
    private double position;
    private int manual = -1;
    private long last, resume;
    private boolean started;
    void reset() { started = false; manual = -1; resume = 0; }
    void scroll(int direction, int active, int size, long now) {
        browseTo((manual < 0 ? Math.max(0, active) : manual) + direction, size, now);
    }
    void browseTo(int index, int size, long now) {
        if (size == 0) return;
        manual = Math.max(0, Math.min(size - 1, index));
        resume = now + 5000;
    }
    static boolean canAnimate(int height) { return height >= 72; }
    int scrollPosition(int active) { return Math.max(0, manual < 0 ? active : manual); }
    boolean browsing(long now) { return manual >= 0 && now < resume; }
    void follow() { manual = -1; resume = 0; }
    double sample(int active, int size, long now, boolean reducedMotion, boolean focused) {
        if (size == 0) { reset(); return 0; }
        if (focused && started) { last = now; return position; }
        if (now >= resume) follow();
        int target = Math.max(0, Math.min(size - 1, manual < 0 ? active : manual));
        if (!started || reducedMotion || Math.abs(target - position) > 6) position = target;
        else if (!focused) position += (target - position) * (1 - Math.exp(-Math.max(0, Math.min(100, now - last)) / 100d));
        if (Math.abs(position - target) < .01) position = target;
        started = true; last = now;
        return position;
    }
}
