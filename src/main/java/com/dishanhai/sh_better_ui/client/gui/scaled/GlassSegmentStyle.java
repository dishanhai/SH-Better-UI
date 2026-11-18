// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.kernel.api.glass.*;
import java.util.List;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;

/** Shared optical geometry. The animated thumb edges are also used for foreground clipping. */
final class GlassSegmentStyle {
    private GlassSegmentStyle() {}

    static List<GlassDraw> surfaces(int x, int y, int width, int height, int left, int right,
                                    int trackInk, int thumbInk) {
        var track = surface(x, y, width, height, 8, trackInk);
        return right > left ? List.of(track, surface(left, y + 2, right - left, height - 4, 6, thumbInk))
                : List.of(track);
    }

    private static GlassDraw surface(int x, int y, int width, int height, int radius, int ink) {
        int luma = ((ink >>> 16 & 255) * 54 + (ink >>> 8 & 255) * 183 + (ink & 255) * 19) >> 8;
        var preset = GlassMaterial.preset(GlassVariant.REGULAR);
        var material = new GlassMaterial(preset.variant(), preset.optics(), preset.quality(),
                luma >= 128 ? GlassMaterial.Foreground.LIGHT : GlassMaterial.Foreground.DARK);
        return new GlassDraw(GlassRegion.rounded(x, y, width, height, radius), material,
                GlassDraw.Profile.SNELL, new GlassDraw.ForegroundBounds(x, y, width, height));
    }

    /** Per-control cache: theme colors are normally stable across animation frames. */
    static final class Scrim {
        private boolean sampled;
        private int fill, ink, color;
        int tint(int fill, int ink, boolean optical) {
            if (!optical) return fill | 0xff000000;
            if (!sampled || this.fill != fill || this.ink != ink) {
                this.fill = fill;
                this.ink = ink;
                color = GlassSegmentStyle.tint(fill, ink, true);
                sampled = true;
            }
            return color;
        }
    }

    static int tint(int opaque, int ink, boolean optical) {
        if (!optical) return opaque | 0xff000000;
        // The caller chooses readable ink against the opaque fill first. Pick the least
        // absorbing scrim that also protects it against both extremes of an unknown scene.
        // A bounded scan (72 candidates), cached by each control, avoids GPU readback.
        for (int alpha = 184; alpha <= 255; alpha++) {
            int scrim = (opaque & 0x00ffffff) | alpha << 24;
            double inkLuma = luminance(ink);
            double low = luminance(ContrastPolicy.composite(scrim, 0xff000000));
            double high = luminance(ContrastPolicy.composite(scrim, 0xffffffff));
            // Composited scene channels lie between the black/white extremes. Keep the
            // entire monotonic luminance interval on one side of the opaque foreground;
            // passing two opposite sides would hide a same-luminance background in between.
            boolean sameSide = high <= inkLuma || low >= inkLuma;
            if (sameSide && ContrastPolicy.ratio(ink, scrim, 0xff000000) >= ContrastPolicy.TEXT_RATIO
                    && ContrastPolicy.ratio(ink, scrim, 0xffffffff) >= ContrastPolicy.TEXT_RATIO) {
                return scrim;
            }
        }
        return opaque | 0xff000000;
    }

    private static double luminance(int color) {
        return .2126 * ColorValue.linear((color >>> 16) & 255)
                + .7152 * ColorValue.linear((color >>> 8) & 255)
                + .0722 * ColorValue.linear(color & 255);
    }
}
