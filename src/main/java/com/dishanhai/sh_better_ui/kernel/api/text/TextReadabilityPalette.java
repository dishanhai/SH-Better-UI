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
 * Adapts opaque base and neutral inline text colors for a panel drawn by the caller. Authored
 * chromatic fills keep their RGB and use outline protection. Use one palette per panel on the
 * render thread. Glyph order, metrics and other style properties are preserved.
 */
public final class TextReadabilityPalette {
    private static final int MAX_COLORS = 512;
    private static final long IDLE_NANOS = 5_000_000_000L;
    private static final double TARGET_CONTRAST = 4.5;
    private static final double CONTRAST_HYSTERESIS = 0.3;
    private static final int LIGHT_OUTLINE_MAX_ALPHA = 128;

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
        return base.sample(argb, backdrop, false, nowNanos);
    }

    /** Preserves authored chromatic colors and protects insufficient contrast with an outline. */
    public int color(int argb) {
        int original = argb | 0xff000000;
        var fill = colors.get(original);
        if (fill == null) {
            if (colors.size() >= MAX_COLORS) colors.remove(colors.keySet().iterator().next());
            fill = new Fill();
            colors.put(original, fill);
        }
        return fill.sample(original, backdrop, true, nowNanos);
    }

    /** Preserves chromatic effect frames and applies the background transition to neutral frames. */
    public int animatedColor(int argb) {
        if (effectTarget == null) return backdrop.target(argb, true);
        int target = effectTarget.target(argb, true);
        return effectElapsed == TRANSITION_NANOS ? target
                : TextColorTransition.fill(effectFrom.target(argb, true), target, effectElapsed);
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

    /**
     * Returns an ARGB outline with continuous opacity during adaptation.
     * Renderers must preserve its alpha. Protection fades out above the panel's contrast target.
     */
    public int outlineColor(int fill) {
        double ink = TextReadabilityReference.srgbLuminance(fill);
        boolean darkOutline = TextReadabilityReference.contrastRatio(ink, 0)
                >= TextReadabilityReference.contrastRatio(ink, 1);
        // The light edge should support dark chromatic glyphs without dominating thin strokes.
        int maxAlpha = darkOutline ? 255 : LIGHT_OUTLINE_MAX_ALPHA;
        int alpha = (int) Math.round(maxAlpha * smoothstep(TARGET_CONTRAST + 1 - backdrop.contrast(fill)));
        // Native Font treats alpha values below four as unspecified and makes them opaque.
        if (alpha < 4) return 0;
        return alpha << 24 | (darkOutline ? 0 : 0xffffff);
    }

    private static double smoothstep(double value) {
        double progress = Math.max(0, Math.min(1, value));
        return progress * progress * (3 - 2 * progress);
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

        double contrast(int argb) {
            double ink = TextReadabilityReference.srgbLuminance(argb);
            double nearest = Math.max(low, Math.min(high, ink));
            return TextReadabilityReference.contrastRatio(ink, nearest);
        }

        int target(int argb, boolean preserveColor) {
            int original = argb | 0xff000000;
            int red = original >>> 16 & 255;
            int green = original >>> 8 & 255;
            int blue = original & 255;
            if (preserveColor && (red != green || green != blue)) return original;
            var cached = targets.get(original);
            if (cached != null) return cached;
            if (targets.size() >= MAX_COLORS) targets.clear();
            int adapted = TextReadabilityReference.readableFill(original, low, high, TARGET_CONTRAST);
            targets.put(original, adapted);
            return adapted;
        }
    }

    private static final class Fill {
        private final TextColorTransition transition = new TextColorTransition();
        private long lastNanos;
        private long pendingSince;
        private int pending;
        private int original;
        private boolean initialized;

        int sample(int argb, Backdrop backdrop, boolean preserveColor, long nowNanos) {
            argb |= 0xff000000;
            int candidate = backdrop.target(argb, preserveColor);
            long delta = nowNanos - lastNanos;
            if (!initialized || delta < 0 || delta >= IDLE_NANOS) {
                transition.start(candidate, candidate);
                pending = 0;
                initialized = true;
            } else {
                // Keep the committed target near the boundary. Restore the authored color
                // once it has a contrast margin, while protection covers the intermediate fill.
                if (original == argb && candidate != transition.target()
                        && backdrop.contrast(transition.target()) >= TARGET_CONTRAST - CONTRAST_HYSTERESIS
                        && (candidate != argb || backdrop.contrast(argb) < TARGET_CONTRAST + CONTRAST_HYSTERESIS)) {
                    candidate = transition.target();
                }
                transition.advance(delta);
                if (candidate == transition.target()) {
                    pending = 0;
                } else if (pending != candidate) {
                    pending = candidate;
                    pendingSince = nowNanos;
                } else if (nowNanos - pendingSince >= TextReadabilityPolicy.DEFAULT_STABILITY_NANOS) {
                    transition.start(transition.fill(), candidate);
                    pending = 0;
                }
            }
            original = argb;
            lastNanos = nowNanos;
            return transition.fill();
        }
    }
}
