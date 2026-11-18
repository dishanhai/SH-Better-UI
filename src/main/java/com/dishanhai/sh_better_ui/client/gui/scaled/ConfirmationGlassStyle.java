// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.kernel.api.glass.*;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;

/** Modal-local, cached readability over unknown scene colors. Owns no GPU resources. */
final class ConfirmationGlassStyle {
    record Palette(int fill, int title, int details) {}
    private int fill, title, details;
    private Palette solid, optical;

    Palette palette(int fill, int title, int details, boolean useOptics) {
        fill |= 0xff000000;
        title |= 0xff000000;
        details |= 0xff000000;
        if (solid == null || this.fill != fill || this.title != title || this.details != details) {
            this.fill = fill;
            this.title = title;
            this.details = details;
            int heading = ContrastPolicy.readable(title, fill, fill, ContrastPolicy.TEXT_RATIO) | 0xff000000;
            int body = ContrastPolicy.readable(details, fill, fill, ContrastPolicy.TEXT_RATIO) | 0xff000000;
            solid = new Palette(fill, heading, body);
            optical = null;
        }
        if (!useOptics) return solid;
        if (optical == null) {
            int alpha = Math.max(alpha(fill, solid.title()), alpha(fill, solid.details()));
            optical = new Palette((fill & 0xffffff) | alpha << 24, solid.title(), solid.details());
        }
        return optical;
    }

    private static int alpha(int fill, int ink) {
        double text = luminance(ink);
        for (int alpha = 184; alpha < 255; alpha++) {
            int scrim = (fill & 0xffffff) | alpha << 24;
            double low = luminance(ContrastPolicy.composite(scrim, 0xff000000));
            double high = luminance(ContrastPolicy.composite(scrim, 0xffffffff));
            // Every possible scene lies in this monotonic luminance interval.
            if ((high <= text || low >= text)
                    && ContrastPolicy.ratio(ink, scrim, 0xff000000) >= ContrastPolicy.TEXT_RATIO
                    && ContrastPolicy.ratio(ink, scrim, 0xffffffff) >= ContrastPolicy.TEXT_RATIO) return alpha;
        }
        return 255;
    }

    private static double luminance(int color) {
        return .2126 * ColorValue.linear((color >>> 16) & 255)
                + .7152 * ColorValue.linear((color >>> 8) & 255)
                + .0722 * ColorValue.linear(color & 255);
    }

    /** Enforce the final glyph color after both component styles and legacy format codes resolve. */
    static net.minecraft.util.FormattedCharSequence ink(net.minecraft.util.FormattedCharSequence line, int color) {
        return sink -> line.accept((index, style, codepoint) ->
                sink.accept(index, style.withColor(color), codepoint));
    }

    static GlassDraw surface(int x, int y, int width, int height, int ink) {
        var preset = GlassMaterial.preset(GlassVariant.REGULAR);
        var material = new GlassMaterial(preset.variant(), preset.optics(), preset.quality(),
                luminance(ink) >= .5 ? GlassMaterial.Foreground.LIGHT : GlassMaterial.Foreground.DARK);
        return new GlassDraw(GlassRegion.rounded(x, y, width, height, 9), material,
                GlassDraw.Profile.SNELL, new GlassDraw.ForegroundBounds(x, y, width, height));
    }
}
