// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.text;

import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.BackgroundSample;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Stage;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Validity;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapts opaque base and inline text colors for a panel drawn by the caller. Use one palette
 * per panel on the render thread. Glyph order, metrics and other style properties are preserved.
 */
public final class TextReadabilityPalette {
    private static final int MAX_COLORS = 512;
    private static final long IDLE_NANOS = 5_000_000_000L;

    private final Map<Integer, Integer> targets = new HashMap<>();
    private final Map<Integer, Fill> colors = new LinkedHashMap<>(16, .75f, true);
    private final Fill base = new Fill();
    private double darkest;
    private double lightest;
    private long nowNanos;

    /**
     * Selects the measured post-material range when available. The two fallback colors
     * bound the composited surface when a capture is pending or unsupported. Call before painting.
     */
    public void update(BackgroundSample sample, int darkestArgb, int lightestArgb) {
        update(sample, darkestArgb, lightestArgb, System.nanoTime());
    }

    /** Update with the same monotonic timestamp for all text drawn in this frame. */
    public void update(BackgroundSample sample, int darkestArgb, int lightestArgb, long nowNanos) {
        this.nowNanos = nowNanos;
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
        targets.clear();
    }

    /** The panel base keeps its animation when the theme changes the authored color. */
    public int baseColor(int argb) {
        return base.sample(target(argb), nowNanos);
    }

    public int color(int argb) {
        int original = argb | 0xff000000;
        var fill = colors.get(original);
        if (fill == null) {
            if (colors.size() >= MAX_COLORS) colors.remove(colors.keySet().iterator().next());
            fill = new Fill();
            colors.put(original, fill);
        }
        return fill.sample(target(original), nowNanos);
    }

    /** Adapts each frame of a caller-authored color effect using the bounded target cache. */
    public int animatedColor(int argb) {
        return target(argb);
    }

    private int target(int argb) {
        int original = argb | 0xff000000;
        var cached = targets.get(original);
        if (cached != null) return cached;
        if (targets.size() >= MAX_COLORS) targets.clear();
        int adapted = TextReadabilityReference.readableFill(original, darkest, lightest, 4.5);
        targets.put(original, adapted);
        return adapted;
    }

    /** Returns a contrasting outline for a fill whose current contrast is below the panel target. */
    public int outlineColor(int fill) {
        double ink = TextReadabilityReference.srgbLuminance(fill);
        double nearest = Math.max(darkest, Math.min(lightest, ink));
        if (TextReadabilityReference.contrastRatio(ink, nearest)
                + TextReadabilityReference.CONTRAST_TOLERANCE >= 4.5) return 0;
        return TextReadabilityReference.contrastRatio(ink, 0) >= TextReadabilityReference.contrastRatio(ink, 1)
                ? 0xff000000 : 0xffffffff;
    }

    public FormattedCharSequence glyphs(FormattedCharSequence text) {
        return sink -> text.accept((index, style, glyph) -> sink.accept(index, adapt(style), glyph));
    }

    private Style adapt(Style style) {
        return style.getColor() == null ? style : style.withColor(color(style.getColor().getValue()) & 0xffffff);
    }

    private static final class Fill {
        private final TextColorTransition transition = new TextColorTransition();
        private long lastNanos;
        private long pendingSince;
        private int pending;
        private boolean initialized;

        int sample(int candidate, long nowNanos) {
            long delta = nowNanos - lastNanos;
            if (!initialized || delta < 0 || delta >= IDLE_NANOS) {
                transition.start(candidate, candidate);
                pending = 0;
                initialized = true;
            } else if (candidate == transition.target()) {
                if (pending == 0) transition.advance(delta);
                pending = 0;
            } else if (pending != candidate) {
                pending = candidate;
                pendingSince = nowNanos;
            } else if (nowNanos - pendingSince >= TextReadabilityPolicy.DEFAULT_STABILITY_NANOS) {
                transition.start(transition.fill(), candidate);
                pending = 0;
            }
            lastNanos = nowNanos;
            return transition.fill();
        }
    }
}
