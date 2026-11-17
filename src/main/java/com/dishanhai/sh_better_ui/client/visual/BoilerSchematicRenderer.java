// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Original, reusable water/heat/steam schematic drawn entirely with bounded local primitives.
 * This is a diagram, not a texture for a real machine or a simulation. No payload or resource
 * lookup is performed. Hosts must supply an explicit descriptive alt text and register it in
 * their own RichTextExtensions scope. Static drawing also serves reduced-motion users.
 */
public final class BoilerSchematicRenderer {
    private BoilerSchematicRenderer() {}
    private static final int SHELL = 0xff9aaec0, DARK = 0xff263746;
    private static final int WATER = 0xff55bce4, STEAM = 0xffd6eaf0, HEAT = 0xffefad64;

    public static void render(RichTextExtensions.Context context) {
        var b = context.bounds();
        for (var r : shapes(b.width(), b.height())) {
            context.graphics().fill(b.x() + r.x0(), b.y() + r.y0(), b.x() + r.x1(), b.y() + r.y1(), r.color());
        }
        // Small fragments keep the readable textual description outside the diagram instead.
        if (b.width() >= 240 && b.height() >= 80) {
            label(context, "水 / Water", 12, 26, WATER);
            label(context, "蒸汽 / Steam", 236, 26, STEAM);
            label(context, "热量 / Heat", 126, 86, HEAT);
        }
    }

    private static void label(RichTextExtensions.Context c, String text, int x, int y, int color) {
        var b = c.bounds();
        int offset = x * b.width() / 320;
        String visible = c.font().plainSubstrByWidth(text, Math.max(0, b.width() - offset - 2));
        c.graphics().drawText(Component.literal(visible), b.x() + offset,
                b.y() + y * b.height() / 104, color, false);
    }

    record Shape(int x0, int y0, int x1, int y1, int color) {}

    /** Coordinates stay inside the fragment; fewer than 32 commands even at full size. */
    static List<Shape> shapes(int width, int height) {
        if (width <= 0 || height <= 0) return List.of();
        var out = new ArrayList<Shape>();
        if (width < 160 || height < 64) {
            // Compact symbolic vessel: liquid chamber, heat base and separate steam outlet.
            add(out, width, height, 32, 32, 8, 6, 25, 27, SHELL);
            add(out, width, height, 32, 32, 10, 8, 23, 25, DARK);
            add(out, width, height, 32, 32, 10, 17, 23, 25, WATER);
            add(out, width, height, 32, 32, 12, 28, 21, 31, HEAT);
            add(out, width, height, 32, 32, 17, 2, 20, 8, STEAM);
        } else {
            // Inlet and outlet are spatially distinct as well as differently colored.
            add(out, width, height, 320, 104, 14, 51, 119, 55, WATER);
            add(out, width, height, 320, 104, 107, 47, 111, 59, WATER);
            add(out, width, height, 320, 104, 111, 49, 115, 57, WATER);
            add(out, width, height, 320, 104, 201, 35, 302, 39, STEAM);
            add(out, width, height, 320, 104, 294, 31, 298, 43, STEAM);
            add(out, width, height, 320, 104, 298, 33, 302, 41, STEAM);
            add(out, width, height, 320, 104, 119, 20, 201, 80, SHELL);
            add(out, width, height, 320, 104, 124, 25, 196, 75, DARK);
            add(out, width, height, 320, 104, 124, 49, 196, 75, WATER);
            add(out, width, height, 320, 104, 124, 49, 196, 52, 0xffa2e4f2);
            add(out, width, height, 320, 104, 137, 81, 184, 84, HEAT);
            // Heat fins and steam risers describe the flow without continuous animation.
            for (int x : new int[]{140, 159, 178}) {
                add(out, width, height, 320, 104, x, 63, x + 3, 74, HEAT);
                add(out, width, height, 320, 104, x, 29, x + 3, 42, STEAM);
            }
            add(out, width, height, 320, 104, 127, 78, 132, 84, SHELL);
            add(out, width, height, 320, 104, 188, 78, 193, 84, SHELL);
        }
        return List.copyOf(out);
    }

    private static void add(List<Shape> out, int w, int h, int baseW, int baseH,
                            int x0, int y0, int x1, int y1, int color) {
        int left = (int) ((long) x0 * w / baseW), top = (int) ((long) y0 * h / baseH);
        int right = (int) ((long) x1 * w / baseW), bottom = (int) ((long) y1 * h / baseH);
        if (right > left && bottom > top) out.add(new Shape(left, top, right, bottom, color));
    }
}
