// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import com.dishanhai.sh_better_ui.kernel.api.glass.*;

/** Readability policy shared by native media-card renderers. No scene readback or GPU ownership. */
final class GlassMediaStyle {
    private final GlassSegmentStyle.Scrim titleScrim = new GlassSegmentStyle.Scrim();
    private final GlassSegmentStyle.Scrim subtitleScrim = new GlassSegmentStyle.Scrim();
    record Palette(int fill, int title, int subtitle) {}
    Palette palette(int fill, int title, int subtitle, boolean optical) {
        fill |= 0xff000000;
        title = ContrastPolicy.readable(title | 0xff000000, fill, fill, ContrastPolicy.TEXT_RATIO) | 0xff000000;
        subtitle = ContrastPolicy.readable(subtitle | 0xff000000, fill, fill, ContrastPolicy.TEXT_RATIO) | 0xff000000;
        int alpha = Math.max(titleScrim.tint(fill, title, optical) >>> 24,
                subtitleScrim.tint(fill, subtitle, optical) >>> 24);
        return new Palette((fill & 0xffffff) | alpha << 24, title, subtitle);
    }
    static GlassDraw surface(int x, int y, int width, int height, int ink) {
        var preset = GlassMaterial.preset(GlassVariant.REGULAR);
        int luma = ((ink >>> 16 & 255) * 54 + (ink >>> 8 & 255) * 183 + (ink & 255) * 19) >> 8;
        var material = new GlassMaterial(preset.variant(), preset.optics(), preset.quality(),
                luma >= 128 ? GlassMaterial.Foreground.LIGHT : GlassMaterial.Foreground.DARK);
        return new GlassDraw(GlassRegion.rounded(x, y, width, height, 9), material,
                GlassDraw.Profile.SNELL, new GlassDraw.ForegroundBounds(x, y, width, height));
    }
}
