// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

/** Small staggered list entrance in GUI units, using the shared 12 ms row rhythm. */
public final class ListEntrance {
    private long startedAt;
    private boolean started;

    public void start(long nowMs) {
        startedAt = nowMs;
        started = true;
    }

    /** Returns the downward translation for a row; reduced motion always returns zero. */
    public double offset(int row, long nowMs, boolean reducedMotion) {
        if (!started || reducedMotion) return 0;
        long delay = Math.min(240L, Math.max(0L, (long) row) * 12L);
        double remaining = Math.max(0, Math.min(1, 1 - (nowMs - (double) startedAt - delay) / 180));
        return 5 * remaining * remaining * remaining;
    }
}
