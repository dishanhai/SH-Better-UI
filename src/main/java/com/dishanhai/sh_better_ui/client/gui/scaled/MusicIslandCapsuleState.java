// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Pure playback visibility policy. No fake audio or synthetic transport state. */
public final class MusicIslandCapsuleState {
    public static final long PAUSE_TIMEOUT_MS = 30_000;
    private String track;
    private long pausedAt = Long.MIN_VALUE, lastTime = Long.MIN_VALUE;
    private boolean active;

    public void observe(String identity, boolean playing, long nowMs) {
        boolean changed = !java.util.Objects.equals(track, identity);
        boolean rewound = lastTime != Long.MIN_VALUE && nowMs < lastTime;
        lastTime = nowMs;
        track = identity;
        if (identity == null) { reset(); return; }
        if (playing) { pausedAt = Long.MIN_VALUE; active = true; return; }
        if (changed || rewound || pausedAt == Long.MIN_VALUE) pausedAt = nowMs;
        active = nowMs >= pausedAt && nowMs - pausedAt < PAUSE_TIMEOUT_MS;
    }
    public boolean active() { return active; }
    public void reset() { track = null; pausedAt = lastTime = Long.MIN_VALUE; active = false; }
    public static double progress(long positionMs, long durationMs) {
        if (positionMs < 0 || durationMs <= 0) return 0;
        return Math.min(1, positionMs / (double) durationMs);
    }
}
