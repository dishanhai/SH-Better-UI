// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;

/** Animates only the image already owned by the loader. Never holds a released texture. */
final class OnlineCoverReveal implements AutoCloseable {
    static final long REVEAL_MS = 180L;
    private final InterruptibleAnimation opacity = new InterruptibleAnimation(Spring.PANEL);
    private long imageRevision; // Identity hash isn't enough: equal content can reuse a GPU location.
    private boolean present;

    float sample(long revision, boolean available, long nowMs, boolean still) {
        if (!available) {
            present = false;
            opacity.snapTo(0f);
            return 0f;
        }
        if (!present || revision != imageRevision) {
            present = true;
            imageRevision = revision;
            opacity.snapTo(0f);
            if (still) opacity.snapTo(1f);
            else opacity.blendTo(1f, nowMs, 0, REVEAL_MS, InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
        }
        return opacity.sample(nowMs, still);
    }

    @Override public void close() { opacity.close(); }
}
