// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.kernel.api.geometry.RoundedFill;
import java.util.LinkedHashMap;

/** Shared rounded gradients, circular fill front and clipped contrast-ink layer. */
final class UiPillNavPaint {
    private record Shape(int width, int height, float radius) {}
    private final LinkedHashMap<Shape, RoundedFill> shapes = new LinkedHashMap<>(32, .75f, true);

    private RoundedFill shape(int w, int h, float radius) {
        var key = new Shape(w, h, radius);
        var shape = shapes.computeIfAbsent(key, k -> new RoundedFill(w, h, radius));
        if (shapes.size() > 128) shapes.remove(shapes.keySet().iterator().next());
        return shape;
    }

    void gradient(PillNavCanvas c, float x, float y, int w, int h, float radius, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        c.mesh(x, y, shape(w, h, radius).interior(), top, bottom, h);
    }

    void fill(PillNavCanvas c, float x, float y, int w, int h, float amount, int top, int bottom) {
        if (amount <= .001f || w <= 0 || h <= 0) return;
        float reach = (float) Math.ceil(2 * Math.hypot(w / 2f, h)) + 2;
        double r = reach * amount / 2;
        var mesh = shape(w, h, h / 2f).filled(RoundedFill.Direction.UP, 2, px -> {
            double dx = px - w / 2.0;
            return Math.abs(dx) >= r ? h + 1 : h - Math.sqrt(Math.max(0, r * r - dx * dx));
        });
        c.mesh(x, y, mesh, top, bottom, h);
    }

    void shadow(PillNavCanvas c, float x, float y, float w, float h, float radius, boolean light) {
        for (int i = 5; i >= 1; i--) {
            c.box(x - i, y - i + 4, w + 2 * i, h + 2 * i, radius + i, 0, 0,
                    (light ? 3 : 6) << 24);
        }
    }

    /** Clips the ink copy to the animated pill, including its curved ends. */
    static void clipPill(PillNavCanvas c, float x, float y, float w, float h, Runnable draw) {
        if (w <= 0 || h <= 0) return;
        float radius = Math.min(w, h) / 2;
        int first = (int) Math.ceil(y), last = (int) Math.floor(y + h);
        for (int row = first; row < last; row += 2) {
            float cy = Math.max(radius - (row - y), (row + 2) - (y + h - radius));
            float inset = cy <= 0 ? 0 : radius - (float) Math.sqrt(Math.max(0, radius * radius - cy * cy));
            int left = (int) Math.ceil(x + inset), right = (int) Math.floor(x + w - inset);
            if (right > left) c.clip(left, row, right, Math.min(last, row + 2), draw);
        }
    }

    static int alpha(int color, float amount) {
        return color & 0xFFFFFF | Math.round((color >>> 24) * Math.max(0, Math.min(1, amount))) << 24;
    }
    static int mix(int from, int to, float amount) {
        float t = Math.max(0, Math.min(1, amount));
        int color = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = from >>> shift & 255, b = to >>> shift & 255;
            color |= Math.round(a + (b - a) * t) << shift;
        }
        return color;
    }
}
