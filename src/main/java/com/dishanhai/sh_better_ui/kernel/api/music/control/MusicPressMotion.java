// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;

/** Paint-only compression and return. A click between frames still leaves a visible release tail. */
public final class MusicPressMotion {
    private final InterruptibleAnimation track = new InterruptibleAnimation(Spring.PANEL);
    private boolean down;

    /** Returns the press amount from 0 to 1. Use a monotonic clock in milliseconds. */
    public float sample(boolean pressed, long now, boolean still) {
        if (still) {
            track.snapTo(0f);
            down = pressed;
            return 0f;
        }
        if (pressed != down) {
            down = pressed;
            if (pressed && track.sample(now) <= 0f) track.snapTo(.38f);
            track.blendTo(pressed ? 1f : 0f, now, 0, pressed ? 60L : 145L,
                    InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
        }
        return track.sample(now);
    }

    public void reset() {
        down = false;
        track.snapTo(0f);
    }

    /** A quick activation starts its return even if no frame rendered while pressed. */
    public void pulse(long now, boolean still) {
        if (still) {
            reset();
            return;
        }
        if (track.sample(now) < .38f) track.snapTo(.38f);
        down = false;
        track.blendTo(0f, now, 0, 145L, InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
    }
}
