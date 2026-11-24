// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;

/** Delayed, interruptible paint cue for actual in-flight artwork, not an absence or failure. */
final class MusicCoverWait {
    static final long DELAY_MS = 140, ENTER_MS = 105, EXIT_MS = 110;
    enum Mode { NONE, SKELETON, BADGE }
    record Cue(Mode mode, float opacity, long phaseMs) {}

    private final InterruptibleAnimation opacity = new InterruptibleAnimation(Spring.INDICATOR);
    private Mode mode = Mode.NONE;
    private boolean loading, visible;
    private long since, phase;

    Cue sample(boolean pending, boolean hasCover, long now, boolean still) {
        if (pending && !loading) since = now;
        loading = pending;
        boolean show = pending && now - since >= DELAY_MS;
        if (show) {
            mode = hasCover ? Mode.BADGE : Mode.SKELETON;
            phase = Math.max(0, now - since - DELAY_MS);
        }
        if (show != visible) {
            visible = show;
            opacity.blendTo(show ? 1f : 0f, now, 0, show ? ENTER_MS : EXIT_MS,
                    InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
        }
        float alpha = opacity.sample(now, still);
        if (alpha <= 0f && !show) mode = Mode.NONE;
        // Stop moving immediately on completion. Only the frozen cue fades out.
        return new Cue(mode, alpha, still ? 0 : phase);
    }

    void reset() {
        mode = Mode.NONE;
        loading = visible = false;
        phase = 0;
        opacity.snapTo(0f);
    }
}
