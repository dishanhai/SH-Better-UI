// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.text;

import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.BackgroundSample;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Stage;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Validity;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts opaque base and inline text colors for a panel drawn by the caller. Use one palette
 * per panel on the render thread. Glyph order, metrics and other style properties are preserved.
 */
public final class TextReadabilityPalette {
    private final Map<Integer, Integer> colors = new HashMap<>();
    private final Map<Style, Style> styles = new HashMap<>();
    private double darkest;
    private double lightest;

    /**
     * Selects the measured post-material range when available. The two fallback colors
     * bound the composited surface when a capture is pending or unsupported. Call before painting.
     */
    public void update(BackgroundSample sample, int darkestArgb, int lightestArgb) {
        double low;
        double high;
        if (sample != null && sample.validity() == Validity.VALID
                && sample.stage() == Stage.POST_MATERIAL_PRE_TEXT) {
            low = sample.statistics().minimum();
            high = sample.statistics().maximum();
        } else {
            low = TextReadabilityReference.srgbLuminance(darkestArgb);
            high = TextReadabilityReference.srgbLuminance(lightestArgb);
            if (low > high) throw new IllegalArgumentException("Backdrop colors must be ordered by luminance");
        }
        if (low == darkest && high == lightest) return;
        darkest = low;
        lightest = high;
        colors.clear();
        styles.clear();
    }

    public int color(int argb) {
        return colors.computeIfAbsent(argb | 0xff000000,
                original -> TextReadabilityReference.readableFill(original, darkest, lightest, 4.5));
    }

    public FormattedCharSequence glyphs(FormattedCharSequence text) {
        return sink -> text.accept((index, style, glyph) -> sink.accept(index, adapt(style), glyph));
    }

    private Style adapt(Style style) {
        if (style.getColor() == null) return style;
        return styles.computeIfAbsent(style, original ->
                original.withColor(color(original.getColor().getValue()) & 0xffffff));
    }
}
