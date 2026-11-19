// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTransportControl.Glyph;

/** Paint-only weight of the requested icon; playback state and interaction never depend on it. */
final class TransportGlyphMotion {
    private static final long BLEND_MS = 140;
    private static final long MAX_FRAME_GAP_MS = 250;
    private final InterruptibleAnimation blend = new InterruptibleAnimation(Spring.HOVER);
    private Glyph previous;
    private long lastFrame;

    static Glyph pairedWith(Glyph glyph) {
        return switch (glyph) {
            case PLAY -> Glyph.PAUSE;
            case PAUSE -> Glyph.PLAY;
            case STOP -> Glyph.CANCEL;
            case CANCEL -> Glyph.STOP;
            default -> glyph;
        };
    }

    float sample(Glyph glyph, long nowMs, boolean immediate) {
        float target = glyph == Glyph.PAUSE || glyph == Glyph.CANCEL ? 1f : 0f;
        boolean firstOrStaleFrame = previous == null || nowMs < lastFrame || nowMs - lastFrame > MAX_FRAME_GAP_MS;
        boolean samePair = previous == glyph || previous == pairedWith(glyph);
        if (immediate || firstOrStaleFrame || !samePair) {
            blend.snapTo(target);
        } else if (previous != glyph) {
            blend.blendTo(target, nowMs, 0, BLEND_MS, InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
        }
        previous = glyph;
        lastFrame = nowMs;
        float sampled = blend.sample(nowMs, immediate);
        return target == 1f ? sampled : 1f - sampled;
    }
}
