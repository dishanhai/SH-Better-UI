// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.kernel.api.controls.SloshGaugeState.Surface;

/** Shared hit test and raster bounds for the overlays clipped to the liquid. */
final class SloshGaugePaint {
    @FunctionalInterface
    interface Run {
        void draw(int left, int top, int right, int bottom);
    }

    private SloshGaugePaint() {}

    static void liquidRuns(int width, int height, float radius, Surface surface, Run run) {
        int start = 0, previousTop = 0, previousBottom = 0;
        for (int x = 0; x <= width; x++) {
            int top = 0, bottom = 0;
            if (x < width) {
                int inset = inset(x + 0.5, width, radius);
                top = Math.max(inset, (int) Math.ceil(surface.topAt(x + 0.5, width, height) - 0.5));
                bottom = height - inset;
                if (top >= bottom) top = bottom = 0;
            }
            if (top == previousTop && bottom == previousBottom) continue;
            if (previousTop < previousBottom) run.draw(start, previousTop, x, previousBottom);
            start = x;
            previousTop = top;
            previousBottom = bottom;
        }
    }

    static boolean contains(double x, double y, int width, int height, int radius) {
        double r = RoundedCorners.fit(width, height, radius);
        double dx = Math.max(0, r - Math.min(x, width - x));
        double dy = Math.max(0, r - Math.min(y, height - y));
        return dx * dx + dy * dy <= r * r;
    }

    static int inset(double position, int extent, double radius) {
        double distance = Math.max(0, radius - Math.min(position, extent - position));
        return (int) Math.max(0, Math.ceil(radius - Math.sqrt(radius * radius - distance * distance) - 0.5));
    }

    static int blend(int a, int b) {
        return 0xFF000000 | (((a & 0xFEFEFE) + (b & 0xFEFEFE)) >>> 1);
    }
}
