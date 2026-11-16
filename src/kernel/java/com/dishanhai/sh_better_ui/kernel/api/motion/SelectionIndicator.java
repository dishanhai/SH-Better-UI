// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.motion;

import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;

/** Retargetable spring for a selection overlay's top and height, in content coordinates. */
public final class SelectionIndicator {
    private static final Spring.Preset MOTION = new Spring.Preset(2 * Math.PI / 30, .78, .05f, .25f);
    private final Spring y = new Spring(MOTION), height = new Spring(MOTION);
    private boolean visible;

    public void target(double top, double size, long now, boolean snap) {
        if (!Double.isFinite(top) || !Double.isFinite(size) || Math.abs(top) > Float.MAX_VALUE
                || size <= 0 || size > Float.MAX_VALUE) throw new IllegalArgumentException("Invalid indicator bounds");
        if (!visible || snap) {
            y.snap((float) top);
            height.snap((float) size);
        } else {
            if (y.target() != (float) top) y.setTarget((float) top, now);
            if (height.target() != (float) size) height.setTarget((float) size, now);
        }
        visible = true;
    }

    public void advance(long now) { y.update(now); height.update(now); }
    public void hide() { visible = false; }
    public boolean visible() { return visible; }
    public double y() { return y.value(); }
    public double height() { return Math.max(0, height.value()); }
}
