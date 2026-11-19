// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import java.util.Objects;

/** Track identity, not playback state or asynchronously enriched metadata, triggers motion. */
final class MusicMetadataReveal {
    static final long DURATION_MS = 280;
    record Frame(float scale, float opacity) {}
    private static final Frame STILL = new Frame(1f, 1f);
    private String identity;
    private long started;
    private float strength;

    Frame sample(String key, long nowMs, boolean still) {
        float remaining = remaining(nowMs);
        if (!Objects.equals(identity, key)) {
            // On rapid skips continue from the currently visible transform, not a queued animation.
            strength = identity == null || key == null ? 0f : remaining > 0f ? remaining : 1f;
            identity = key;
            started = nowMs;
            remaining = strength;
        }
        if (still || key == null) { strength = 0f; return STILL; }
        return new Frame(1f - .025f * remaining, 1f - .18f * remaining);
    }

    private float remaining(long nowMs) {
        float t = Math.max(0f, Math.min(1f, (nowMs - started) / (float) DURATION_MS));
        return strength * (1f - t * t * (3f - 2f * t));
    }
}
