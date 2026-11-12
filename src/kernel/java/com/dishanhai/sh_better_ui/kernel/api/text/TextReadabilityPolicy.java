// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.text;

import java.util.Objects;

/** Field-level text readability overrides. A null field inherits from the enclosing configuration. */
public record TextReadabilityPolicy(Boolean adaptiveColor, Boolean foregroundProtection, Contrast contrast,
                                    Classification classification, Double largeTextFramebufferPixels,
                                    Long stabilityNanos, SamplingMode samplingMode, Boolean lockHue, Boolean localColor) {
    public static final long DEFAULT_STABILITY_NANOS = 120_000_000L;
    private static final double BODY_CONTRAST = 4.5;
    private static final double DISPLAY_CONTRAST = 3.0;
    // Provisional until Phase 6 calibration. Used only when framebuffer-size classification is enabled.
    private static final double PROVISIONAL_LARGE_TEXT_PIXELS = 24.0;
    private static final TextReadabilityPolicy INHERIT = new TextReadabilityPolicy(null, null, null, null, null);
    private static final TextReadabilityPolicy DEFAULTS = new TextReadabilityPolicy(false, false,
            ClassifiedContrast.INSTANCE, Classification.USAGE, PROVISIONAL_LARGE_TEXT_PIXELS,
            DEFAULT_STABILITY_NANOS, SamplingMode.AUTO, null, false);

    public TextReadabilityPolicy {
        if (largeTextFramebufferPixels != null) requirePositiveFinite(largeTextFramebufferPixels);
        if (stabilityNanos != null) requireStability(stabilityNanos);
    }

    public TextReadabilityPolicy(Boolean adaptiveColor, Boolean foregroundProtection, Contrast contrast,
                                 Classification classification, Double largeTextFramebufferPixels) {
        this(adaptiveColor, foregroundProtection, contrast, classification, largeTextFramebufferPixels, null);
    }

    public TextReadabilityPolicy(Boolean adaptiveColor, Boolean foregroundProtection, Contrast contrast,
                                 Classification classification, Double largeTextFramebufferPixels, Long stabilityNanos) {
        this(adaptiveColor, foregroundProtection, contrast, classification, largeTextFramebufferPixels, stabilityNanos,
                null, null, null);
    }

    public TextReadabilityPolicy withSamplingMode(SamplingMode mode) {
        return new TextReadabilityPolicy(adaptiveColor, foregroundProtection, contrast, classification,
                largeTextFramebufferPixels, stabilityNanos, mode, lockHue, localColor);
    }

    public TextReadabilityPolicy withLockHue(Boolean lock) {
        return new TextReadabilityPolicy(adaptiveColor, foregroundProtection, contrast, classification,
                largeTextFramebufferPixels, stabilityNanos, samplingMode, lock, localColor);
    }

    public TextReadabilityPolicy withLocalColor(Boolean local) {
        return new TextReadabilityPolicy(adaptiveColor, foregroundProtection, contrast, classification,
                largeTextFramebufferPixels, stabilityNanos, samplingMode, lockHue, local);
    }

    public static TextReadabilityPolicy inherit() {
        return INHERIT;
    }

    public static TextReadabilityPolicy defaults() {
        return DEFAULTS;
    }

    /** Applies the child's supplied fields, including explicit false or a reset to classified contrast. */
    public TextReadabilityPolicy overlay(TextReadabilityPolicy overrides) {
        Objects.requireNonNull(overrides, "overrides");
        return new TextReadabilityPolicy(
                preferOverride(adaptiveColor, overrides.adaptiveColor),
                preferOverride(foregroundProtection, overrides.foregroundProtection),
                preferOverride(contrast, overrides.contrast),
                preferOverride(classification, overrides.classification),
                preferOverride(largeTextFramebufferPixels, overrides.largeTextFramebufferPixels),
                preferOverride(stabilityNanos, overrides.stabilityNanos),
                preferOverride(samplingMode, overrides.samplingMode),
                preferOverride(lockHue, overrides.lockHue),
                preferOverride(localColor, overrides.localColor));
    }

    /** Fills remaining inherited fields with built-in defaults after applying all scope overrides. */
    public Resolved resolve() {
        var policy = DEFAULTS.overlay(this);
        return new Resolved(policy.adaptiveColor, policy.foregroundProtection, policy.contrast, policy.classification,
                policy.largeTextFramebufferPixels, policy.stabilityNanos, policy.samplingMode, policy.lockHue,
                policy.localColor);
    }

    /**
     * Converts the rendered height of one text line to physical framebuffer pixels. The GUI height includes the
     * visual transform and text scale, measured along the transformed line-height axis before clipping.
     * Use the active render target's pixel-to-GUI scale along that axis, including Minecraft's GUI scale,
     * with fractional values preserved. Measure each line independently for multiline text.
     */
    public static double framebufferHeight(double renderedGuiHeight, double framebufferPixelsPerGuiUnit) {
        requireHeight(renderedGuiHeight);
        requirePositiveFinite(framebufferPixelsPerGuiUnit);
        double height = renderedGuiHeight * framebufferPixelsPerGuiUnit;
        requireHeight(height);
        return height;
    }

    public enum Usage {
        BODY,
        /** Explicitly designated large display text in usage classification. */
        DISPLAY
    }

    public enum Classification { USAGE, FRAMEBUFFER_SIZE }

    /** The enclosing page or popup may restrict actual-background sampling to opted-in controls. */
    public enum SamplingMode { AUTO, EXPLICIT_ONLY }

    public sealed interface Contrast permits FixedContrast, ClassifiedContrast {}

    public record FixedContrast(double ratio) implements Contrast {
        public FixedContrast {
            if (!Double.isFinite(ratio) || ratio < 1 || ratio > 21) {
                throw new IllegalArgumentException("Text contrast ratio must be in [1, 21]");
            }
        }
    }

    /** Selects classification and can override an enclosing fixed contrast ratio. */
    public enum ClassifiedContrast implements Contrast { INSTANCE }

    public record Resolved(boolean adaptiveColor, boolean foregroundProtection, Contrast contrast,
                           Classification classification, double largeTextFramebufferPixels, long stabilityNanos,
                           SamplingMode samplingMode, Boolean lockHue, boolean localColor) {
        public Resolved {
            Objects.requireNonNull(contrast, "contrast");
            Objects.requireNonNull(classification, "classification");
            Objects.requireNonNull(samplingMode, "samplingMode");
            requirePositiveFinite(largeTextFramebufferPixels);
            requireStability(stabilityNanos);
        }

        public Resolved(boolean adaptiveColor, boolean foregroundProtection, Contrast contrast,
                        Classification classification, double largeTextFramebufferPixels) {
            this(adaptiveColor, foregroundProtection, contrast, classification, largeTextFramebufferPixels,
                    DEFAULT_STABILITY_NANOS);
        }

        public Resolved(boolean adaptiveColor, boolean foregroundProtection, Contrast contrast,
                        Classification classification, double largeTextFramebufferPixels, long stabilityNanos) {
            this(adaptiveColor, foregroundProtection, contrast, classification, largeTextFramebufferPixels,
                    stabilityNanos, SamplingMode.AUTO, null, false);
        }

        /** Fixed contrast takes priority. Only framebuffer-size classification uses the measured height. */
        public double targetContrast(Usage usage, double framebufferHeight) {
            if (contrast instanceof FixedContrast fixed) return fixed.ratio();
            if (classification == Classification.USAGE) {
                return Objects.requireNonNull(usage, "usage") == Usage.DISPLAY ? DISPLAY_CONTRAST : BODY_CONTRAST;
            }
            requireHeight(framebufferHeight);
            return framebufferHeight >= largeTextFramebufferPixels ? DISPLAY_CONTRAST : BODY_CONTRAST;
        }
    }

    private static <T> T preferOverride(T inherited, T override) {
        return override != null ? override : inherited;
    }

    private static void requireStability(long stabilityNanos) {
        if (stabilityNanos < 0) throw new IllegalArgumentException("Text stability time must be non-negative");
    }

    private static void requireHeight(double height) {
        if (!Double.isFinite(height) || height < 0) {
            throw new IllegalArgumentException("Text height must be finite and non-negative");
        }
    }

    private static void requirePositiveFinite(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("Text size or framebuffer scale must be finite and positive");
        }
    }
}
