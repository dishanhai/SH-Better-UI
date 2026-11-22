// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;

/** Tracks the upward drag left over after a vertical list reaches its forward limit. */
public final class ScrollEndPull {
    private static final double TRIGGER_DISTANCE = 36;
    private final InterruptibleAnimation reveal = new InterruptibleAnimation(Spring.HOVER);
    private double distance;

    public double drag(double offset, double maximum, double forwardDelta) {
        if (!Double.isFinite(forwardDelta)) return offset;
        double target = offset + distance + forwardDelta;
        distance = Math.max(0, Math.min(target - maximum, TRIGGER_DISTANCE * 1.5));
        reveal.snapTo(progress());
        return Math.max(0, Math.min(target, maximum));
    }

    public float progress() { return (float) Math.min(1, distance / TRIGGER_DISTANCE); }

    public boolean release(boolean allowed, long now) {
        boolean trigger = allowed && distance >= TRIGGER_DISTANCE;
        distance = 0;
        reveal.blendTo(0f, now, 0, 160L, InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
        return trigger;
    }

    public float reveal(long now, boolean still) {
        return still ? progress() : reveal.sample(now);
    }

    public void reset() {
        distance = 0;
        reveal.snapTo(0f);
    }
}
