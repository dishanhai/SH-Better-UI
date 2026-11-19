// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import java.util.Objects;

/** Decorative scroll response only. Never reads playback time or changes layout/hit testing. */
final class MusicScrollParallax {
    static final int LIMIT = 8;
    private Object context;
    private double target, shown;
    private long last;
    private boolean initialized, disabled;
    void reset() { context = null; target = shown = 0; initialized = false; }
    void scroll(double rows) {
        if (!initialized || disabled || !Double.isFinite(rows)) return;
        // Thumb jumps remain a small visual cue, not a displacement proportional to list size.
        target = Math.max(-LIMIT, Math.min(LIMIT, target - Math.max(-4, Math.min(4, rows)) * 2));
    }
    float sample(Object key, long now, boolean reducedMotion) {
        if (!initialized || !Objects.equals(context, key) || reducedMotion || now < last) {
            context = key; target = shown = 0; initialized = true; last = now;
            disabled = reducedMotion;
            return 0;
        }
        disabled = false;
        double dt = Math.min(100, Math.max(0, (double) now - last)); last = now;
        shown += (target - shown) * (1 - Math.exp(-dt / 85));
        target *= Math.exp(-dt / 240);
        if (Math.abs(shown) < .001 && Math.abs(target) < .001) shown = target = 0;
        return (float) shown;
    }
}
