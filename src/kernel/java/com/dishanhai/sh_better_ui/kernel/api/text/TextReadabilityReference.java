// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.text;

import com.dishanhai.sh_better_ui.theme.token.ColorValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;

/** CPU reference for one positioned, visually ordered text run with a uniform original style. */
public final class TextReadabilityReference {
    public static final int WHOLE_RUN = -1;
    public static final double CONTRAST_TOLERANCE = 0.000001;
    public static final int TRANSITION_NANOS = 320_000_000;
    public static final int MAX_TRANSITION_STEP_NANOS = 40_000_000;

    private static final int BLACK = 0xff000000;
    private static final int WHITE = 0xffffffff;
    private static final int LIGHTNESS_STEPS = 32;

    private TextReadabilityReference() {}

    public enum Source { ACTUAL, DECLARED, UNKNOWN }

    public enum Protection { NONE, OUTLINE, BACKING }

    public enum Diagnostic {
        INSUFFICIENT_ACTUAL_SAMPLES, UNKNOWN_BACKGROUND, BACKGROUND_CONTRAST_INSUFFICIENT, INDIVISIBLE_RUN,
        HUE_LOCKED, NO_ELIGIBLE_CANDIDATE, WAITING_FOR_STABILITY, UNSTABLE_TEXT_ID, PROTECTION_LIMITED,
        GROUP_OPACITY_CONTRAST_INSUFFICIENT
    }

    /** Provisional values for calibration on the native renderers. */
    public record Sampling(int minimumActualSamples, double minimumGlyphInteriorCoverage,
                           double minimumOutlineFramebufferPixels) {
        public static final Sampling DEFAULT = new Sampling(4, 0.75, 16);

        public Sampling {
            if (minimumActualSamples < 1 || minimumGlyphInteriorCoverage <= 0) {
                throw new IllegalArgumentException("Actual samples and glyph interior coverage must be positive");
            }
            requireUnit(minimumGlyphInteriorCoverage);
            requireHeight(minimumOutlineFramebufferPixels);
        }
    }

    /**
     * One background point under a verified visual glyph cluster or an indivisible run ({@link #WHOLE_RUN}).
     * Outline coverage estimates the coverage of the adjacent outline pixel. Clip applies in framebuffer space.
     */
    public record Sample(int glyphCluster, double luminance, double glyphCoverage, double outlineCoverage,
                         boolean insideEffectiveClip) {
        public Sample {
            if (glyphCluster < WHOLE_RUN) throw new IllegalArgumentException("Invalid glyph cluster");
            requireUnit(luminance);
            requireUnit(glyphCoverage);
            requireUnit(outlineCoverage);
        }

        /** A fully composited 8-bit sRGB sample. Unresolved alpha makes the sample ineligible. */
        public static Sample actualSrgb(int glyphCluster, int argb, double glyphCoverage, double outlineCoverage,
                                        boolean insideEffectiveClip) {
            return new Sample(glyphCluster, srgbLuminance(argb), glyphCoverage, outlineCoverage,
                    insideEffectiveClip && (argb >>> 24) == 255);
        }

        /** A fully composited sample already decoded to linear light by the render target. */
        public static Sample actualLinear(int glyphCluster, double red, double green, double blue,
                                          double glyphCoverage, double outlineCoverage, boolean insideEffectiveClip) {
            requireUnit(red);
            requireUnit(green);
            requireUnit(blue);
            return new Sample(glyphCluster, TextReadabilityReference.luminance(red, green, blue), glyphCoverage, outlineCoverage,
                    insideEffectiveClip);
        }
    }

    public static final class Background {
        private final List<Sample> actual;
        private final Integer declaredArgb;
        private final boolean attemptedActual;

        private Background(List<Sample> actual, Integer declaredArgb, boolean attemptedActual) {
            if (declaredArgb != null && (declaredArgb >>> 24) != 255) {
                throw new IllegalArgumentException("Declared background must represent fully composited sRGB");
            }
            this.actual = List.copyOf(actual);
            this.declaredArgb = declaredArgb;
            this.attemptedActual = attemptedActual;
        }

        /** Current frame samples plus an optional fully composited representative sRGB fallback. */
        public static Background actual(List<Sample> currentFrameSamples, Integer declaredArgb) {
            return new Background(currentFrameSamples, declaredArgb, true);
        }

        public static Background declared(int opaqueArgb) {
            return new Background(List.of(), opaqueArgb, false);
        }

        public static Background unknown() {
            return new Background(List.of(), null, false);
        }
    }

    /** Theme candidate order is caller-supplied. Candidate RGB values always keep the original glyph alpha. */
    public record Input(int originalArgb, boolean lockHue, boolean allowOutline, boolean allowBacking,
                        double targetContrast, double groupOpacity, double framebufferHeight, long stabilityNanos,
                        Background background, Sampling sampling, List<Integer> themeCandidateArgb) {
        public Input {
            if (!Double.isFinite(targetContrast) || targetContrast < 1 || targetContrast > 21) {
                throw new IllegalArgumentException("Text contrast ratio must be in [1, 21]");
            }
            requireUnit(groupOpacity);
            requireHeight(framebufferHeight);
            if (stabilityNanos < 0) throw new IllegalArgumentException("Text stability time must be non-negative");
            Objects.requireNonNull(background, "background");
            Objects.requireNonNull(sampling, "sampling");
            themeCandidateArgb = List.copyOf(themeCandidateArgb);
        }

        public Input(int originalArgb, boolean lockHue, boolean allowOutline, boolean allowBacking,
                     double targetContrast, double groupOpacity, double framebufferHeight, long stabilityNanos,
                     Background background) {
            this(originalArgb, lockHue, allowOutline, allowBacking, targetContrast, groupOpacity, framebufferHeight,
                    stabilityNanos, background, Sampling.DEFAULT, List.of());
        }
    }

    /** Keep one instance per scope and stable run identity. Reset on invalidation or a break in observations. */
    public static final class State {
        private Source source;
        private int originalArgb;
        private final TextColorTransition transition = new TextColorTransition();
        private Integer pendingArgb;
        private long pendingSinceNanos;
        private long lastNanos;
        private double targetContrast;
        private long stabilityNanos;
        private boolean lockHue;
        private Integer seed;

        /** Starts the next evaluation from the color previously displayed by another backend. */
        public void seedDisplayedFill(int argb) {
            reset();
            seed = argb;
        }

        /** The last displayed fill, or null until the first evaluation. */
        public Integer displayedFill() {
            if (source == null) return seed;
            return transition.fill();
        }

        public void reset() {
            source = null;
            pendingArgb = null;
            seed = null;
        }
    }

    /** Restores a displayed color from a backend's sRGB transition state. */
    public static int interpolateFill(int from, int target, int elapsedNanos) {
        return TextColorTransition.fill(from, target, elapsedNanos);
    }

    public record ProtectionArea(int glyphCluster, Protection kind, int argb) {}

    /** A cluster that the layout adapter has verified and positioned in visual order. */
    public record ClusterInput(Input input, State state) {
        public ClusterInput { Objects.requireNonNull(input, "input"); }
    }

    public record ClusterPaint(int firstCluster, int endCluster, int fillArgb) {}

    public record ClusterDecision(List<Decision> decisions, List<ClusterPaint> paints) {
        public ClusterDecision {
            decisions = List.copyOf(decisions);
            paints = List.copyOf(paints);
        }
    }

    /** Explicit local-color path. Each verified cluster may use its own sample set and stable state. */
    public static ClusterDecision evaluateVerifiedClusters(List<ClusterInput> clustersInVisualOrder,
                                                           Set<Integer> explicitBoundaries, long nowNanos) {
        var clusters = List.copyOf(clustersInVisualOrder);
        var boundaries = validateBoundaries(explicitBoundaries, clusters.size());
        var decisions = new ArrayList<Decision>(clusters.size());
        var colors = new ArrayList<Integer>(clusters.size());
        for (var cluster : clusters) {
            var decision = evaluate(cluster.input, cluster.state, nowNanos);
            decisions.add(decision);
            colors.add(decision.hidden ? null : decision.fillArgb);
        }
        return new ClusterDecision(decisions, mergeClusters(colors, boundaries));
    }

    /**
     * Coalesces fills after an adapter explicitly enables local color and evaluates its verified visual glyph clusters.
     * Boundaries are insertion positions between these shaped clusters, in [0, size]. An indivisible native run
     * supplies one color, so its only valid positions are 0 and 1. Source string offsets are never consumed here.
     */
    public static List<ClusterPaint> mergeVerifiedClusters(List<Integer> fillArgbInVisualOrder,
                                                          Set<Integer> explicitBoundaries) {
        var colors = List.copyOf(fillArgbInVisualOrder);
        return mergeClusters(colors, validateBoundaries(explicitBoundaries, colors.size()));
    }

    private static Set<Integer> validateBoundaries(Set<Integer> explicitBoundaries, int clusterCount) {
        var boundaries = Set.copyOf(explicitBoundaries);
        for (int position : boundaries) {
            if (position < 0 || position > clusterCount) {
                throw new IllegalArgumentException("Boundary is outside the verified visual clusters");
            }
        }
        return boundaries;
    }

    private static List<ClusterPaint> mergeClusters(List<Integer> colors, Set<Integer> boundaries) {
        if (colors.isEmpty()) return List.of();
        var groups = new ArrayList<ClusterPaint>();
        int first = 0;
        Integer current = colors.get(0);
        for (int cluster = 1; cluster < colors.size(); cluster++) {
            Integer color = colors.get(cluster);
            if (Objects.equals(color, current) && !boundaries.contains(cluster)) continue;
            if (current != null) groups.add(new ClusterPaint(first, cluster, current));
            first = cluster;
            current = color;
        }
        if (current != null) groups.add(new ClusterPaint(first, colors.size(), current));
        return List.copyOf(groups);
    }

    /**
     * Fill and protected metrics ignore group fading. Final metrics include it. A NaN is unassessable.
     * For unknown background, protected and final metrics are lower bounds over all opaque backdrop colors.
     */
    public record Decision(int fillArgb, Source source, boolean hidden, double targetContrast, double fillP10,
                           double protectedP10, double finalP10, Integer pendingArgb,
                           List<ProtectionArea> protections, Set<Diagnostic> diagnostics) {
        public Decision {
            protections = List.copyOf(protections);
            diagnostics = Set.copyOf(diagnostics);
        }

        public Protection protection() {
            var strongest = strongestArea();
            return strongest == null ? Protection.NONE : strongest.kind;
        }

        /** Whole-run color for declared/unknown background, or the first strongest local area's color. */
        public int protectionArgb() {
            var strongest = strongestArea();
            return strongest == null ? 0 : strongest.argb;
        }

        private ProtectionArea strongestArea() {
            ProtectionArea strongest = null;
            for (var area : protections) {
                if (strongest == null || area.kind.ordinal() > strongest.kind.ordinal()) strongest = area;
            }
            return strongest;
        }
    }

    /**
     * Applies isolated group opacity and waits for a stable candidate before interpolating the fill.
     * The original is the initial fill and the first candidate once its contrast recovers. Protection
     * and contrast diagnostics are computed for the fill rendered at the current point in the transition.
     */
    public static Decision evaluate(Input input, State state, long nowNanos) {
        var diagnostics = EnumSet.noneOf(Diagnostic.class);
        var samples = usableSamples(input);
        Source source;
        if (samples.size() >= input.sampling.minimumActualSamples) {
            source = Source.ACTUAL;
            if (samples.stream().anyMatch(sample -> sample.glyphCluster == WHOLE_RUN)) {
                diagnostics.add(Diagnostic.INDIVISIBLE_RUN);
            }
        } else {
            if (input.background.attemptedActual) diagnostics.add(Diagnostic.INSUFFICIENT_ACTUAL_SAMPLES);
            if (input.background.declaredArgb != null) {
                source = Source.DECLARED;
                samples = List.of(new Sample(WHOLE_RUN, srgbLuminance(input.background.declaredArgb), 1, 1, true));
            } else {
                source = Source.UNKNOWN;
                diagnostics.add(Diagnostic.UNKNOWN_BACKGROUND);
            }
        }

        if (input.groupOpacity == 0 || alpha(input.originalArgb) == 0) {
            if (state != null) state.reset();
            return new Decision(input.originalArgb, source, true, input.targetContrast, Double.NaN, Double.NaN,
                    Double.NaN, null, List.of(), diagnostics);
        }
        if (source == Source.UNKNOWN) return unknown(input, state, nowNanos, diagnostics);

        final var currentSamples = samples;
        Integer candidate = chooseCandidate(input,
                color -> fillPasses(color, currentSamples, input.targetContrast));
        int fillArgb = stabilize(input, source, candidate, state, nowNanos, diagnostics);
        var fill = new Ink(fillArgb);
        var bare = assess(fill, samples, null, 1);
        var protections = new ArrayList<ProtectionArea>();
        var full = bare;
        var visible = assess(fill, samples, null, input.groupOpacity);
        if (!meets(bare.minimum, input.targetContrast)) {
            diagnostics.add(Diagnostic.BACKGROUND_CONTRAST_INSUFFICIENT);
            if (!meets(bare.p10, input.targetContrast) && input.allowBacking) {
                protections.add(bestProtection(fill, WHOLE_RUN, Protection.BACKING, samples));
            } else {
                protectLocal(input, fill, samples, protections);
            }
            full = assess(fill, samples, protections, 1);
            visible = assess(fill, samples, protections, input.groupOpacity);
            if (!meets(full.minimum, input.targetContrast)) diagnostics.add(Diagnostic.PROTECTION_LIMITED);
        }
        addOpacityDiagnostic(input, diagnostics, full.minimum, visible.minimum);
        return new Decision(fillArgb, source, false, input.targetContrast, bare.p10, full.p10, visible.p10,
                pending(candidate, fillArgb), protections, diagnostics);
    }

    private static List<Sample> usableSamples(Input input) {
        return input.background.actual.stream()
                .filter(sample -> sample.insideEffectiveClip
                        && sample.glyphCoverage >= input.sampling.minimumGlyphInteriorCoverage)
                .toList();
    }

    private static Decision unknown(Input input, State state, long nowNanos, EnumSet<Diagnostic> diagnostics) {
        boolean useBacking = input.allowBacking && !meets(1, input.targetContrast);
        Integer candidate = useBacking ? chooseCandidate(input,
                color -> meets(bestUnknownBacking(new Ink(color)).ratio, input.targetContrast))
                : Integer.valueOf(input.originalArgb);
        int fillArgb = stabilize(input, Source.UNKNOWN, candidate, state, nowNanos, diagnostics);
        var fill = new Ink(fillArgb);
        var backing = useBacking ? bestUnknownBacking(fill) : new UnknownBacking(0, 1);
        var protections = useBacking
                ? List.of(new ProtectionArea(WHOLE_RUN, Protection.BACKING, backing.argb)) : List.<ProtectionArea>of();
        double visible = useBacking ? unknownRatio(fill, backing.argb, input.groupOpacity) : 1;
        if (!meets(backing.ratio, input.targetContrast)) diagnostics.add(Diagnostic.PROTECTION_LIMITED);
        addOpacityDiagnostic(input, diagnostics, backing.ratio, visible);
        return new Decision(fillArgb, Source.UNKNOWN, false, input.targetContrast, Double.NaN, backing.ratio,
                visible, pending(candidate, fillArgb), protections, diagnostics);
    }

    private static void protectLocal(Input input, Ink fill, List<Sample> samples, List<ProtectionArea> protections) {
        var byCluster = new LinkedHashMap<Integer, List<Sample>>();
        if (samples.stream().anyMatch(sample -> sample.glyphCluster == WHOLE_RUN)) {
            byCluster.put(WHOLE_RUN, samples);
        } else {
            for (var sample : samples) {
                byCluster.computeIfAbsent(sample.glyphCluster, ignored -> new ArrayList<>()).add(sample);
            }
        }
        for (var entry : byCluster.entrySet()) {
            var clusterSamples = entry.getValue();
            if (meets(minimumRatio(fill, null, clusterSamples), input.targetContrast)) continue;
            ProtectionArea area = null;
            if (input.allowOutline && input.framebufferHeight >= input.sampling.minimumOutlineFramebufferPixels) {
                area = bestProtection(fill, entry.getKey(), Protection.OUTLINE, clusterSamples);
            }
            if (input.allowBacking && (area == null
                    || !meets(minimumRatio(fill, area, clusterSamples), input.targetContrast))) {
                area = bestProtection(fill, entry.getKey(), Protection.BACKING, clusterSamples);
            }
            if (area != null) protections.add(area);
        }
    }

    private static ProtectionArea bestProtection(Ink fill, int cluster, Protection kind, List<Sample> samples) {
        var black = new ProtectionArea(cluster, kind, BLACK);
        var white = new ProtectionArea(cluster, kind, WHITE);
        double blackMinimum = minimumRatio(fill, black, samples);
        double whiteMinimum = minimumRatio(fill, white, samples);
        return whiteMinimum > blackMinimum ? white : black;
    }

    private static double minimumRatio(Ink fill, ProtectionArea area, List<Sample> samples) {
        double minimum = 21;
        for (var sample : samples) minimum = Math.min(minimum, contrastAt(fill, sample, area, 1));
        return minimum;
    }

    private static UnknownBacking bestUnknownBacking(Ink fill) {
        // Maximize the guaranteed full-opacity ratio for the fill currently eligible to be drawn.
        double black = unknownRatio(fill, BLACK, 1);
        double white = unknownRatio(fill, WHITE, 1);
        return white > black ? new UnknownBacking(WHITE, white) : new UnknownBacking(BLACK, black);
    }

    private static double unknownRatio(Ink fill, int backingArgb, double opacity) {
        // Both compared pixels receive the same external term. White is the worst-case backdrop.
        return contrastOnBacking(fill, srgbLuminance(backingArgb), 1, opacity);
    }

    private static void addOpacityDiagnostic(Input input, EnumSet<Diagnostic> diagnostics,
                                             double fullContrast, double finalContrast) {
        if (input.groupOpacity < 1 && meets(fullContrast, input.targetContrast)
                && !meets(finalContrast, input.targetContrast)) {
            diagnostics.add(Diagnostic.GROUP_OPACITY_CONTRAST_INSUFFICIENT);
        }
    }

    private static int stabilize(Input input, Source source, Integer candidate, State state, long nowNanos,
                                 EnumSet<Diagnostic> diagnostics) {
        if (candidate == null) {
            diagnostics.add(Diagnostic.NO_ELIGIBLE_CANDIDATE);
            if (input.lockHue) diagnostics.add(Diagnostic.HUE_LOCKED);
        }
        if (state == null) {
            if (input.stabilityNanos == 0 && candidate != null) return candidate;
            if (pending(candidate, input.originalArgb) != null) diagnostics.add(Diagnostic.UNSTABLE_TEXT_ID);
            return input.originalArgb;
        }

        long delta = nowNanos - state.lastNanos;
        if (state.source == null || state.originalArgb != input.originalArgb || state.lockHue != input.lockHue
                || state.targetContrast != input.targetContrast || state.stabilityNanos != input.stabilityNanos
                || delta < 0) {
            int fill = state.seed == null ? input.originalArgb
                    : (state.seed & 0x00ffffff) | (input.originalArgb & 0xff000000);
            state.reset();
            state.originalArgb = input.originalArgb;
            state.transition.start(fill, fill);
            state.lockHue = input.lockHue;
            state.targetContrast = input.targetContrast;
            state.stabilityNanos = input.stabilityNanos;
        } else if (state.source != source) {
            int fill = state.transition.fill();
            state.transition.start(fill, fill);
            state.pendingArgb = null;
        }
        state.source = source;
        state.lastNanos = nowNanos;
        if (candidate == null) {
            state.pendingArgb = null;
        } else if (candidate == state.transition.target()) {
            if (state.pendingArgb == null) state.transition.advance(delta);
            state.pendingArgb = null;
        } else {
            if (!candidate.equals(state.pendingArgb)) {
                state.pendingArgb = candidate;
                state.pendingSinceNanos = nowNanos;
            }
            long stability = Math.max(input.stabilityNanos, TextReadabilityPolicy.DEFAULT_STABILITY_NANOS);
            if (nowNanos - state.pendingSinceNanos >= stability) {
                state.transition.start(state.transition.fill(), candidate);
                state.pendingArgb = null;
            } else {
                diagnostics.add(Diagnostic.WAITING_FOR_STABILITY);
            }
        }
        return state.transition.fill();
    }

    private static Integer pending(Integer candidate, int fillArgb) {
        return candidate == null || candidate == fillArgb ? null : candidate;
    }

    private static Integer chooseCandidate(Input input, IntPredicate passes) {
        return chooseCandidate(input.originalArgb, input.lockHue, input.themeCandidateArgb, passes);
    }

    /**
     * Chooses an opaque fill against a continuous range of backdrop luminances. Intended for
     * panels which share one palette across multiple runs and paint their own glyphs.
     * If no color meets the target, returns the neutral fill with the highest worst-case contrast.
     */
    public static int readableFill(int argb, double darkest, double lightest, double targetContrast) {
        requireUnit(darkest);
        requireUnit(lightest);
        if (darkest > lightest || !Double.isFinite(targetContrast) || targetContrast < 1 || targetContrast > 21) {
            throw new IllegalArgumentException("Invalid background luminance range or contrast target");
        }
        Integer candidate = chooseCandidate(argb | 0xff000000, false, List.of(), color -> {
            double fill = srgbLuminance(color);
            double nearest = Math.max(darkest, Math.min(lightest, fill));
            return meets(contrastRatio(fill, nearest), targetContrast);
        });
        if (candidate != null) return candidate;
        return contrastRatio(1, lightest) >= contrastRatio(0, darkest) ? WHITE : BLACK;
    }

    private static Integer chooseCandidate(int original, boolean lockHue, List<Integer> themeCandidateArgb,
                                           IntPredicate passes) {
        if (passes.test(original)) return original;
        var hue = hue(original);
        if (hue.chroma != 0) {
            for (int step = 1; step < LIGHTNESS_STEPS; step++) {
                // Equal change in encoded RGB favors the darker candidate first.
                int darker = lightness(original, 0, step);
                if (sameHue(hue, darker) && passes.test(darker)) return darker;
                int lighter = lightness(original, 255, step);
                if (sameHue(hue, lighter) && passes.test(lighter)) return lighter;
            }
        }
        if (lockHue) return null;
        int black = withAlpha(original, BLACK);
        if (passes.test(black)) return black;
        int white = withAlpha(original, WHITE);
        if (passes.test(white)) return white;
        for (int theme : themeCandidateArgb) {
            int color = withAlpha(original, theme);
            if (passes.test(color)) return color;
        }
        return null;
    }

    private static int lightness(int argb, int endpoint, int step) {
        int red = moveChannel((argb >>> 16) & 255, endpoint, step);
        int green = moveChannel((argb >>> 8) & 255, endpoint, step);
        int blue = moveChannel(argb & 255, endpoint, step);
        return withAlpha(argb, red << 16 | green << 8 | blue);
    }

    private static int moveChannel(int channel, int endpoint, int step) {
        return (channel * (LIGHTNESS_STEPS - step) + endpoint * step + LIGHTNESS_STEPS / 2) / LIGHTNESS_STEPS;
    }

    private static boolean sameHue(Hue expected, int argb) {
        var candidate = hue(argb);
        if (candidate.chroma == 0) return false;
        int divisor = expected.chroma * candidate.chroma;
        int distance = Math.abs(expected.sector * candidate.chroma - candidate.sector * expected.chroma);
        // Six sectors make a full turn, so two degrees is exactly 1/30 of a sector.
        // Compare the quantized channels as integers to share the inclusive boundary with GLSL.
        return 30 * Math.min(distance, 6 * divisor - distance) <= divisor;
    }

    private static Hue hue(int argb) {
        int red = (argb >>> 16) & 255;
        int green = (argb >>> 8) & 255;
        int blue = argb & 255;
        int max = Math.max(red, Math.max(green, blue));
        int delta = max - Math.min(red, Math.min(green, blue));
        int sector;
        if (max == red) sector = green - blue;
        else if (max == green) sector = 2 * delta + blue - red;
        else sector = 4 * delta + red - green;
        return new Hue(sector < 0 ? sector + 6 * delta : sector, delta);
    }

    private static int withAlpha(int original, int rgb) {
        return (original & 0xff000000) | (rgb & 0xffffff);
    }

    private static boolean fillPasses(int argb, List<Sample> samples, double targetContrast) {
        int permittedRank = (samples.size() - 1) / 10;
        int failures = 0;
        var fill = new Ink(argb);
        for (var sample : samples) {
            if (!meets(contrastAt(fill, sample, null, 1), targetContrast) && ++failures > permittedRank) return false;
        }
        return true;
    }

    private static Assessment assess(Ink fill, List<Sample> samples, List<ProtectionArea> protections, double opacity) {
        double[] ratios = new double[samples.size()];
        for (int index = 0; index < ratios.length; index++) {
            var sample = samples.get(index);
            ProtectionArea selected = null;
            if (protections != null) {
                for (var area : protections) {
                    if (area.glyphCluster == WHOLE_RUN || area.glyphCluster == sample.glyphCluster) {
                        selected = area;
                        break;
                    }
                }
            }
            ratios[index] = contrastAt(fill, sample, selected, opacity);
        }
        Arrays.sort(ratios);
        return new Assessment(ratios[(ratios.length - 1) / 10], ratios[0]);
    }

    private static double contrastAt(Ink fill, Sample sample, ProtectionArea area, double opacity) {
        double backdrop = sample.luminance;
        if (area == null) return contrastRatio(blend(fill.luminance, backdrop, fill.alpha * opacity), backdrop);

        double protection = srgbLuminance(area.argb);
        if (area.kind == Protection.OUTLINE) {
            double glyph = blend(fill.luminance, backdrop, fill.alpha * opacity);
            double edge = blend(protection, backdrop, sample.outlineCoverage * opacity);
            return contrastRatio(glyph, edge);
        }

        return contrastOnBacking(fill, protection, backdrop, opacity);
    }

    private static double contrastOnBacking(Ink fill, double protection, double backdrop, double opacity) {
        // The opaque backing and glyph compose first. Apply the group opacity exactly once per pixel.
        double glyphOnBacking = blend(fill.luminance, protection, fill.alpha);
        return contrastRatio(blend(glyphOnBacking, backdrop, opacity), blend(protection, backdrop, opacity));
    }

    /** Nearest rank, one-indexed ceil(0.10 * n), with no interpolation. */
    public static double percentile10(double... ratios) {
        if (ratios.length == 0) return Double.NaN;
        double[] sorted = ratios.clone();
        Arrays.sort(sorted);
        return sorted[(sorted.length - 1) / 10];
    }

    public static double srgbLuminance(int argb) {
        return luminance(ColorValue.linear((argb >>> 16) & 255), ColorValue.linear((argb >>> 8) & 255),
                ColorValue.linear(argb & 255));
    }

    private static double luminance(double red, double green, double blue) {
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    public static double contrastRatio(double first, double second) {
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double alpha(int argb) {
        return (argb >>> 24) / 255.0;
    }

    private static double blend(double foreground, double backdrop, double alpha) {
        return foreground * alpha + backdrop * (1 - alpha);
    }

    private static boolean meets(double ratio, double targetContrast) {
        return ratio + CONTRAST_TOLERANCE >= targetContrast;
    }

    private static void requireUnit(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException("Expected finite value in [0, 1]");
        }
    }

    private static void requireHeight(double height) {
        if (!Double.isFinite(height) || height < 0) {
            throw new IllegalArgumentException("Framebuffer text height must be finite and non-negative");
        }
    }

    private record Ink(double luminance, double alpha) {
        Ink(int argb) { this(srgbLuminance(argb), TextReadabilityReference.alpha(argb)); }
    }

    private record Assessment(double p10, double minimum) {}

    private record Hue(int sector, int chroma) {}

    private record UnknownBacking(int argb, double ratio) {}
}
