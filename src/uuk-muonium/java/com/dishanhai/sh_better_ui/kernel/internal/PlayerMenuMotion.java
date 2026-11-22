// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.muoniumplayer.core.screens.ncm.effects.EffectsLayout.Box;

/** Real-time, interruptible popover and row transitions shared by the music controls. */
public final class PlayerMenuMotion {
    private final InterruptibleAnimation reveal = new InterruptibleAnimation(Spring.FLUID_MORPH);

    public float sample(boolean open, long now, boolean still) {
        return sample(open, now, still, 1);
    }

    public float sample(boolean open, long now, boolean still, double speed) {
        float target = open ? 1f : 0f;
        if (reveal.target() != target) {
            long duration = Math.max(1L, Math.round((open ? 205 : 145) / speed));
            reveal.blendTo(target, now, 0, duration, InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
        }
        return reveal.sample(now, still);
    }

    public void reset() { reveal.snapTo(0); }

    public static float rowReveal(float progress, int index) {
        float delay = .12f + Math.min(index, 5) * .055f;
        float t = Math.max(0f, Math.min(1f, (progress - delay) / (1f - delay)));
        return t * t * (3f - 2f * t);
    }

    public static Box frame(Box bounds, double anchorX, double anchorY, float progress) {
        double rest = 1 - progress;
        double pivotX = Math.max(bounds.x(), Math.min(bounds.right(), anchorX));
        boolean fromBelow = anchorY >= bounds.centerY();
        double pivotY = fromBelow ? bounds.bottom() : bounds.y();
        double scale = 1 - .06 * rest;
        return new Box(bounds.x() + (pivotX - bounds.x()) * (1 - scale),
                bounds.y() + (pivotY - bounds.y()) * (1 - scale) + (fromBelow ? 6 : -6) * rest,
                bounds.width() * scale, bounds.height() * scale);
    }

    public static Box mapRow(Box row, Box bounds, Box frame) {
        double scale = frame.width() / bounds.width();
        return new Box(frame.x() + (row.x() - bounds.x()) * scale, frame.y() + (row.y() - bounds.y()) * scale,
                row.width() * scale, row.height() * scale);
    }

    public static final class Row {
        private final InterruptibleAnimation fill = new InterruptibleAnimation(Spring.FLUID_MORPH,
                PlayerMenuStyle.rowOpacity(false, false));

        public float sample(boolean hover, boolean pressed, long now, boolean still) {
            float target = PlayerMenuStyle.rowOpacity(hover, pressed);
            if (fill.target() != target) {
                fill.blendTo(target, now, 0, pressed ? 70 : 125, InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
            }
            return fill.sample(now, still);
        }
    }
}
