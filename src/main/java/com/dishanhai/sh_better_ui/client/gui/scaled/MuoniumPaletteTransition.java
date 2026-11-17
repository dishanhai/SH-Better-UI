// Author: dishanhai. Original native transition, inspired by MP cover-driven backgrounds.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.theme.token.ColorValue;

/** Screen-owned, interruption-safe color transition. Numeric material edits take effect immediately. */
final class MuoniumPaletteTransition {
    private AmbientBackdropProfile from, target, shown;
    private long last;
    private double elapsed;
    private static final double DURATION = 650;

    void reset() { from = target = shown = null; elapsed = 0; }

    AmbientBackdropProfile sample(AmbientBackdropProfile next, long now, boolean reducedMotion) {
        if (shown == null || reducedMotion) {
            from = target = shown = next; last = now; elapsed = DURATION; return next;
        }
        long delta = now > last ? now - last : 0;
        last = now;
        if (!sameColors(target, next)) {
            from = shown; target = next; elapsed = 0;
        } else elapsed = Math.min(DURATION, elapsed + Math.min(50, delta < 0 ? 50 : delta));
        double t = elapsed / DURATION;
        t = t * t * (3 - 2 * t);
        shown = new AmbientBackdropProfile(next.speed(), next.scale(), next.intensity(), next.opacity(),
                color(from.primaryColor(), next.primaryColor(), t),
                color(from.secondaryColor(), next.secondaryColor(), t),
                color(from.backgroundColor(), next.backgroundColor(), t));
        return shown;
    }

    private static boolean sameColors(AmbientBackdropProfile a, AmbientBackdropProfile b) {
        return a.primaryColor() == b.primaryColor() && a.secondaryColor() == b.secondaryColor()
                && a.backgroundColor() == b.backgroundColor();
    }
    private static int color(int a, int b, double t) {
        return ColorValue.interpolateOklab(ColorValue.argb(a), ColorValue.argb(b), t).argb();
    }
}
