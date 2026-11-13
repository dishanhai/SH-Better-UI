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
import java.util.Optional;

import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.MAX_TRANSITION_STEP_NANOS;
import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.TRANSITION_NANOS;

/**
 * Adapts opaque base and inline text colors for a panel drawn by the caller. Use one palette
 * per panel on the render thread. Glyph order, metrics and other style properties are preserved.
 */
public final class TextReadabilityPalette {
    private static final int MAX_COLORS = 512;
    private static final long IDLE_NANOS = 5_000_000_000L;

    private static final double EFFECT_BACKDROP_TOLERANCE = 0.02;

    private final Map<Integer, Fill> colors = new LinkedHashMap<>(16, .75f, true);
    private final Fill base = new Fill();
    private Backdrop backdrop = new Backdrop(0, 0);
    private Backdrop effectFrom;
    private Backdrop effectTarget;
    private Backdrop effectPending;
    private int effectElapsed = TRANSITION_NANOS;
    private long effectPendingSince;
    private BackgroundSample observation;
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
        double fallbackLow = TextReadabilityReference.srgbLuminance(darkestArgb);
        double fallbackHigh = TextReadabilityReference.srgbLuminance(lightestArgb);
        if (fallbackLow > fallbackHigh) throw new IllegalArgumentException("Backdrop colors must be ordered by luminance");
        observation = sample != null && sample.stage() == Stage.POST_MATERIAL_PRE_TEXT ? sample : null;
        double low = fallbackLow;
        double high = fallbackHigh;
        if (observation != null && observation.validity() == Validity.VALID) {
            low = observation.statistics().minimum();
            high = observation.statistics().maximum();
        }
        if (low != backdrop.low || high != backdrop.high) backdrop = new Backdrop(low, high);
        updateEffectBackdrop(nowNanos);
        this.nowNanos = nowNanos;
    }

    /** The last post-material observation, including pending or unavailable results when provided by the backend. */
    public Optional<BackgroundSample> backgroundSample() {
        return Optional.ofNullable(observation);
    }

    /** The panel base keeps its animation when the theme changes the authored color. */
    public int baseColor(int argb) {
        return base.sample(backdrop.target(argb), nowNanos);
    }

    public int color(int argb) {
        int original = argb | 0xff000000;
        var fill = colors.get(original);
        if (fill == null) {
            if (colors.size() >= MAX_COLORS) colors.remove(colors.keySet().iterator().next());
            fill = new Fill();
            colors.put(original, fill);
        }
        return fill.sample(backdrop.target(original), nowNanos);
    }

    /** Applies the current background transition to a fresh color from a caller-authored effect. */
    public int animatedColor(int argb) {
        if (effectTarget == null) return backdrop.target(argb);
        int target = effectTarget.target(argb);
        return effectElapsed == TRANSITION_NANOS ? target
                : TextColorTransition.fill(effectFrom.target(argb), target, effectElapsed);
    }

    private void updateEffectBackdrop(long now) {
        long delta = now - nowNanos;
        if (effectTarget == null || delta < 0 || delta >= IDLE_NANOS) {
            effectFrom = effectTarget = backdrop;
            effectElapsed = TRANSITION_NANOS;
            effectPending = null;
            return;
        }
        effectElapsed = Math.min(TRANSITION_NANOS, effectElapsed + (int) Math.min(MAX_TRANSITION_STEP_NANOS, delta));
        if (backdrop.low == effectTarget.low && backdrop.high == effectTarget.high) {
            effectPending = null;
        } else if (effectPending == null || !backdrop.closeTo(effectPending)) {
            effectPending = backdrop;
            effectPendingSince = now;
        } else if (effectElapsed == TRANSITION_NANOS
                && now - effectPendingSince >= TextReadabilityPolicy.DEFAULT_STABILITY_NANOS) {
            effectFrom = effectTarget;
            effectTarget = backdrop;
            effectPending = null;
            effectElapsed = 0;
        }
    }

    /** Returns a contrasting outline for a fill whose current contrast is below the panel target. */
    public int outlineColor(int fill) {
        double ink = TextReadabilityReference.srgbLuminance(fill);
        double nearest = Math.max(backdrop.low, Math.min(backdrop.high, ink));
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

    private static final class Backdrop {
        private final double low;
        private final double high;
        private final Map<Integer, Integer> targets = new HashMap<>();

        private Backdrop(double low, double high) {
            this.low = low;
            this.high = high;
        }

        boolean closeTo(Backdrop other) {
            return Math.abs(low - other.low) < EFFECT_BACKDROP_TOLERANCE
                    && Math.abs(high - other.high) < EFFECT_BACKDROP_TOLERANCE;
        }

        int target(int argb) {
            int original = argb | 0xff000000;
            var cached = targets.get(original);
            if (cached != null) return cached;
            if (targets.size() >= MAX_COLORS) targets.clear();
            int adapted = TextReadabilityReference.readableFill(original, low, high, 4.5);
            targets.put(original, adapted);
            return adapted;
        }
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
