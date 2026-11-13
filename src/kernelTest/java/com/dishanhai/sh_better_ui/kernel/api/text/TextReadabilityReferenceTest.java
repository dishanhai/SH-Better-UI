// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.text;

import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Background;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.ClusterInput;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.ClusterPaint;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Input;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.ProtectionArea;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Sample;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Sampling;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.State;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Diagnostic.*;
import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Protection.*;
import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.Source.*;
import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.WHOLE_RUN;
import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.evaluate;
import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.evaluateVerifiedClusters;
import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.mergeVerifiedClusters;
import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.percentile10;
import static com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityReference.srgbLuminance;
import static org.junit.jupiter.api.Assertions.*;

class TextReadabilityReferenceTest {
    private static final int BLACK = 0xff000000;
    private static final int WHITE = 0xffffffff;
    private static final int RED = 0xffff0000;
    private static final int GRAY = 0xff757575;
    private static final long WINDOW = TextReadabilityPolicy.DEFAULT_STABILITY_NANOS;

    @Test
    void blackAndWhitePreservePassingOriginalsOnDeclaredBackdrops() {
        for (int fill : List.of(BLACK, WHITE)) {
            var decision = evaluate(input(fill, Background.declared(fill == WHITE ? BLACK : WHITE)), new State(), 0);
            assertEquals(DECLARED, decision.source());
            assertEquals(fill, decision.fillArgb());
            assertEquals(21, decision.fillP10());
            assertEquals(NONE, decision.protection());
            assertTrue(decision.diagnostics().isEmpty());
        }
        assertThrows(IllegalArgumentException.class, () -> Background.declared(0x00ffffff));
    }

    @Test
    void contrastVectorsDecodeOnlyEncodedSrgbAndUseLinearLuminance() {
        assertEquals(0.21586050011389926, srgbLuminance(0xff808080), 1e-15);
        assertEquals(0.2126, srgbLuminance(RED), 1e-15);
        assertEquals(5.252, TextReadabilityReference.contrastRatio(srgbLuminance(RED), 0), 1e-12);
        var encoded = Sample.actualSrgb(0, 0xff808080, 1, 1, true);
        var linear = Sample.actualLinear(0, 0.21586050011389926, 0.21586050011389926,
                0.21586050011389926, 1, 1, true);
        assertEquals(encoded.luminance(), linear.luminance(), 1e-15);
    }

    @Test
    void percentileUsesNearestRankAndKeepsOneIsolatedBrightSpotOutOfTheFillDecision() {
        assertEquals(1, percentile10(21, 21, 21, 21, 21, 1, 21, 21, 21, 21));
        assertEquals(21, percentile10(21, 21, 21, 21, 21, 1, 21, 21, 21, 21, 21));
        assertTrue(Double.isNaN(percentile10()));

        var decision = evaluate(input(WHITE, Background.actual(brightSpot(1), WHITE)), new State(), 0);
        assertEquals(ACTUAL, decision.source());
        assertEquals(WHITE, decision.fillArgb());
        assertEquals(21, decision.fillP10());
        assertEquals(21, decision.protectedP10());
        assertEquals(List.of(new ProtectionArea(4, OUTLINE, BLACK)), decision.protections());
        assertTrue(decision.diagnostics().contains(BACKGROUND_CONTRAST_INSUFFICIENT));
    }

    @Test
    void smallGlyphsAndWeakOutlineEscalateToLocalBacking() {
        var weakEdge = Background.actual(brightSpot(0.5), null);
        var weak = evaluate(input(WHITE, weakEdge), new State(), 0);
        assertEquals(List.of(new ProtectionArea(4, BACKING, BLACK)), weak.protections());

        var goodEdge = Background.actual(brightSpot(1), null);
        var small = evaluate(new Input(WHITE, false, true, true, 4.5, 1, 12, WINDOW, goodEdge), new State(), 0);
        assertEquals(List.of(new ProtectionArea(4, BACKING, BLACK)), small.protections());
        var restricted = evaluate(new Input(WHITE, false, true, false, 4.5, 1, 12, WINDOW, goodEdge),
                new State(), 0);
        assertEquals(NONE, restricted.protection());
        assertTrue(restricted.diagnostics().contains(PROTECTION_LIMITED));
    }

    @Test
    void eligibilityExcludesAntialiasingEdgesClippedPointsAndUnresolvedAlpha() {
        var samples = new ArrayList<>(List.of(sample(0, BLACK), sample(1, BLACK), sample(2, BLACK), sample(3, BLACK)));
        samples.add(Sample.actualSrgb(4, WHITE, 0.5, 1, true));
        samples.add(Sample.actualSrgb(5, WHITE, 1, 1, false));
        samples.add(Sample.actualSrgb(6, 0x40ffffff, 1, 1, true));
        var background = Background.actual(samples, WHITE);
        var actual = evaluate(input(WHITE, background), new State(), 0);
        assertEquals(ACTUAL, actual.source());
        assertEquals(21, actual.fillP10());
        assertEquals(NONE, actual.protection());

        var stricter = new Input(WHITE, false, true, true, 4.5, 1, 20, WINDOW, background,
                new Sampling(5, 0.75, 16), List.of());
        var declared = evaluate(stricter, new State(), 0);
        assertEquals(DECLARED, declared.source());
        assertEquals(1, declared.fillP10());
        assertTrue(declared.diagnostics().contains(INSUFFICIENT_ACTUAL_SAMPLES));
        assertEquals(BACKING, declared.protection());
    }

    @Test
    void anIndivisibleRunUsesOneProtectionRegionEvenWhenSomeClusterIdsAreKnown() {
        var samples = brightSpot(1);
        samples.set(4, sample(WHOLE_RUN, WHITE));
        var decision = evaluate(input(WHITE, Background.actual(samples, null)), new State(), 0);
        assertEquals(List.of(new ProtectionArea(WHOLE_RUN, OUTLINE, BLACK)), decision.protections());
        assertTrue(decision.diagnostics().contains(INDIVISIBLE_RUN));
    }

    @Test
    void semanticHueWinsBeforeNeutralAndLockedHueNeverBecomesNeutral() {
        var black = Background.declared(BLACK);
        var lit = evaluate(new Input(RED, true, true, true, 7, 1, 20, 0, black), null, 0);
        assertEquals(0xffff6060, lit.fillArgb());
        assertTrue(lit.fillP10() >= 7);
        assertEquals(NONE, lit.protection());

        var locked = evaluate(new Input(RED, true, true, true, 21, 1, 20, 0, black), new State(), 0);
        assertEquals(RED, locked.fillArgb());
        assertTrue(locked.diagnostics().containsAll(Set.of(HUE_LOCKED, NO_ELIGIBLE_CANDIDATE, PROTECTION_LIMITED)));
        var unlocked = evaluate(new Input(RED, false, true, true, 21, 1, 20, 0, black), null, 0);
        assertEquals(WHITE, unlocked.fillArgb());
        assertEquals(21, unlocked.fillP10());
    }

    @Test
    void neutralTiesFavorBlackAndThemeColorsAreTriedInCallerOrder() {
        var tie = evaluate(new Input(GRAY, false, true, true, 4.5, 1, 20, 0, Background.declared(GRAY)), null, 0);
        assertEquals(BLACK, tie.fillArgb());

        var mixed = Background.actual(List.of(sample(0, BLACK), sample(1, BLACK), sample(2, WHITE), sample(3, WHITE)),
                null);
        var themed = evaluate(new Input(0xffcccccc, false, true, true, 4.5, 1, 20, 0, mixed, Sampling.DEFAULT,
                List.of(0x00767676, GRAY)), null, 0);
        assertEquals(0xff767676, themed.fillArgb());
        assertTrue(themed.fillP10() >= 4.5);
    }

    @Test
    void quantizedHueExactlyTwoDegreesAwayRemainsEligible() {
        var background = Background.declared(0xff8e1c90);
        var input = new Input(0xff8e8487, true, true, true, 4.5, 1, 20, 0, background);
        var decision = evaluate(input, null, 0);
        // Original hue is 342 degrees. This first passing candidate is exactly 340 degrees.
        assertEquals(0xffd8d5d6, decision.fillArgb());
        assertEquals(NONE, decision.protection());
        assertTrue(decision.fillP10() >= 4.5);

        var green = new Input(0xff050600, true, true, true, 1.2, 1, 20, 0, Background.declared(BLACK));
        // The 70-to-72-degree boundary used to round above two degrees on the CPU.
        assertEquals(0xff1c1d18, evaluate(green, null, 0).fillArgb());
    }

    @Test
    void stableCandidateGraduallyTransitionsInBothDirectionsWithProtection() {
        var state = new State();
        var low = input(WHITE, Background.declared(WHITE));
        var start = evaluate(low, state, 8);
        assertEquals(WHITE, start.fillArgb());
        assertEquals(BLACK, start.pendingArgb());
        assertEquals(BACKING, start.protection());
        assertTrue(start.diagnostics().contains(WAITING_FOR_STABILITY));
        assertEquals(WHITE, evaluate(low, state, 8 + WINDOW - 1).fillArgb());
        long accepted = 8 + WINDOW;
        assertEquals(WHITE, evaluate(low, state, accepted).fillArgb());
        assertEquals(WHITE, evaluate(low, state, accepted).fillArgb());

        var early = evaluate(low, state, accepted + 40_000_000L);
        assertEquals(0xffdfdfdf, early.fillArgb());
        assertEquals(BLACK, early.pendingArgb());
        assertEquals(BACKING, early.protection());
        assertFalse(early.diagnostics().contains(WAITING_FOR_STABILITY));
        for (int step = 2; step <= 8; step++) {
            early = evaluate(low, state, accepted + step * 40_000_000L);
        }
        assertEquals(BLACK, early.fillArgb());
        assertNull(early.pendingArgb());
        assertEquals(NONE, early.protection());

        var recovered = input(WHITE, Background.declared(BLACK));
        long recovery = accepted + 320_000_001L;
        assertEquals(BLACK, evaluate(recovered, state, recovery).fillArgb());
        assertEquals(BLACK, evaluate(recovered, state, recovery + WINDOW).fillArgb());
        assertEquals(0xff202020, evaluate(recovered, state, recovery + WINDOW + 40_000_000L).fillArgb());
    }

    @Test
    void zeroOverrideStillRejectsRapidChangesAndLargeFrameGapsCannotSkipIntermediateColors() {
        var state = new State();
        var bright = new Input(WHITE, false, false, true, 4.5, 1, 20, 0, Background.declared(WHITE));
        var dark = new Input(WHITE, false, false, true, 4.5, 1, 20, 0, Background.declared(BLACK));
        assertEquals(WHITE, evaluate(bright, state, 0).fillArgb());
        assertEquals(WHITE, evaluate(dark, state, WINDOW - 1).fillArgb());
        assertEquals(WHITE, evaluate(bright, state, WINDOW).fillArgb());
        assertEquals(WHITE, evaluate(bright, state, 2 * WINDOW).fillArgb());

        long afterStall = 1_000_000_000L;
        int intermediate = evaluate(bright, state, afterStall).fillArgb();
        assertEquals(0xffdfdfdf, intermediate);
        assertEquals(intermediate, evaluate(bright, state, afterStall).fillArgb());
        assertEquals(intermediate, evaluate(dark, state, afterStall + 1).fillArgb());
        assertEquals(intermediate, evaluate(dark, state, afterStall + WINDOW + 1).fillArgb());
        assertEquals(0xffe3e3e3, evaluate(dark, state, afterStall + WINDOW + 40_000_001L).fillArgb());
    }

    @Test
    void changingSamplingSourceRetainsTheDisplayedColorAndRestartsTheStabilityWindow() {
        var state = new State();
        var declared = input(WHITE, Background.declared(WHITE));
        var actual = input(WHITE, Background.actual(List.of(sample(0, WHITE), sample(1, WHITE),
                sample(2, WHITE), sample(3, WHITE)), WHITE));
        evaluate(declared, state, 0);
        evaluate(declared, state, WINDOW);
        long intermediateAt = WINDOW + 40_000_000L;
        int fill = evaluate(declared, state, intermediateAt).fillArgb();
        assertEquals(0xffdfdfdf, fill);

        long changedAt = intermediateAt + 1;
        var changed = evaluate(actual, state, changedAt);
        assertEquals(ACTUAL, changed.source());
        assertEquals(fill, changed.fillArgb());
        assertEquals(BLACK, changed.pendingArgb());
        assertTrue(changed.diagnostics().contains(WAITING_FOR_STABILITY));
        assertEquals(fill, evaluate(actual, state, changedAt + WINDOW).fillArgb());
        assertEquals(0xffc3c3c3, evaluate(actual, state, changedAt + WINDOW + 40_000_000L).fillArgb());
    }

    @Test
    void candidateFailureSourceChangesAndStateResetBreakTheContinuousWindow() {
        var state = new State();
        var actualWhite = Background.actual(List.of(sample(0, WHITE), sample(1, WHITE), sample(2, WHITE),
                sample(3, WHITE)), WHITE);
        var low = input(WHITE, actualWhite);
        var declined = input(WHITE, Background.actual(List.of(sample(0, BLACK), sample(1, BLACK), sample(2, BLACK),
                sample(3, BLACK)), WHITE));
        assertEquals(BLACK, evaluate(low, state, 0).pendingArgb());
        assertNull(evaluate(declined, state, WINDOW - 1).pendingArgb());
        assertEquals(WHITE, evaluate(low, state, WINDOW).fillArgb());

        var missing = input(WHITE, Background.actual(List.of(sample(0, WHITE)), WHITE));
        assertEquals(DECLARED, evaluate(missing, state, 2 * WINDOW - 1).source());
        assertEquals(WHITE, evaluate(low, state, 2 * WINDOW).fillArgb());
        assertEquals(WHITE, evaluate(low, state, 3 * WINDOW).fillArgb());
        assertEquals(0xffdfdfdf, evaluate(low, state, 3 * WINDOW + 40_000_000L).fillArgb());

        state.reset();
        assertEquals(WHITE, evaluate(low, state, 4 * WINDOW).fillArgb());
        assertEquals(WHITE, evaluate(low, state, 3 * WINDOW).fillArgb());
        assertEquals(WHITE, evaluate(low, state, 4 * WINDOW - 1).fillArgb());
    }

    @Test
    void changingTheCandidateRestartsTheClockWithinTheSameBackgroundSource() {
        var state = new State();
        var dark = new Input(RED, false, true, true, 7, 1, 20, WINDOW, Background.declared(BLACK));
        var light = new Input(RED, false, true, true, 7, 1, 20, WINDOW, Background.declared(WHITE));
        int brightCandidate = evaluate(dark, state, -WINDOW).pendingArgb();
        int darkCandidate = evaluate(light, state, -1).pendingArgb();
        assertNotEquals(brightCandidate, darkCandidate);
        assertEquals(RED, evaluate(dark, state, 0).fillArgb());
        assertEquals(RED, evaluate(dark, state, WINDOW - 1).fillArgb());
        assertEquals(brightCandidate, evaluate(dark, state, WINDOW).pendingArgb());
        assertNotEquals(RED, evaluate(dark, state, WINDOW + 40_000_000L).fillArgb());
    }

    @Test
    void scopePolicyCanOverrideStabilityIncludingZeroAndMissingIdHasImmediateProtection() {
        var parent = new TextReadabilityPolicy(true, null, null, null, null, 80_000_000L);
        var child = new TextReadabilityPolicy(null, null, null, null, null, 0L);
        assertEquals(WINDOW, TextReadabilityPolicy.defaults().resolve().stabilityNanos());
        assertEquals(80_000_000L, parent.overlay(TextReadabilityPolicy.inherit()).resolve().stabilityNanos());
        assertEquals(0, parent.overlay(child).resolve().stabilityNanos());
        assertThrows(IllegalArgumentException.class,
                () -> new TextReadabilityPolicy(null, null, null, null, null, -1L));
        var unstable = evaluate(input(WHITE, Background.declared(WHITE)), null, 0);
        assertEquals(WHITE, unstable.fillArgb());
        assertEquals(BACKING, unstable.protection());
        assertTrue(unstable.diagnostics().contains(UNSTABLE_TEXT_ID));
    }

    @Test
    void unknownBackdropGetsWorstCaseBackingAndReportsRestrictions() {
        var unknown = evaluate(input(WHITE, Background.actual(List.of(), null)), new State(), 0);
        assertEquals(UNKNOWN, unknown.source());
        assertEquals(BACKING, unknown.protection());
        assertEquals(BLACK, unknown.protectionArgb());
        assertEquals(21, unknown.protectedP10());
        assertTrue(Double.isNaN(unknown.fillP10()));
        assertTrue(unknown.diagnostics().containsAll(Set.of(INSUFFICIENT_ACTUAL_SAMPLES, UNKNOWN_BACKGROUND)));

        var dark = evaluate(input(BLACK, Background.unknown()), new State(), 0);
        assertEquals(WHITE, dark.protectionArgb());
        var restricted = evaluate(new Input(0x10ff2020, true, true, true, 4.5, 1, 20, 0,
                Background.unknown()), new State(), 0);
        assertEquals(0x10ff2020, restricted.fillArgb());
        assertEquals(BLACK, restricted.protectionArgb());
        assertEquals((srgbLuminance(0x10ff2020) * (16 / 255.0) + 0.05) / 0.05,
                restricted.protectedP10(), 1e-12);
        assertTrue(restricted.diagnostics().containsAll(Set.of(HUE_LOCKED, PROTECTION_LIMITED)));
        var prohibited = evaluate(new Input(WHITE, false, true, false, 4.5, 1, 20, 0, Background.unknown()),
                new State(), 0);
        assertEquals(NONE, prohibited.protection());
        assertTrue(prohibited.diagnostics().contains(PROTECTION_LIMITED));
    }

    @Test
    void groupOpacityIsAppliedOnceToTheCompositedGlyphAndBackingAndZeroHidesBoth() {
        int fill = 0x80ffffff;
        double fillOpacity = 128 / 255.0;
        var faded = evaluate(new Input(fill, false, true, true, 4.5, 0.5, 20, WINDOW, Background.unknown()),
                new State(), 0);
        assertEquals((fillOpacity + 0.05) / 0.05, faded.protectedP10(), 1e-12);
        assertEquals((0.5 + 0.5 * fillOpacity + 0.05) / 0.55, faded.finalP10(), 1e-12);
        assertTrue(faded.diagnostics().contains(GROUP_OPACITY_CONTRAST_INSUFFICIENT));
        assertFalse(faded.diagnostics().contains(PROTECTION_LIMITED));

        var hidden = evaluate(new Input(fill, false, true, true, 4.5, 0, 20, WINDOW, Background.unknown()),
                new State(), 0);
        assertTrue(hidden.hidden());
        assertEquals(NONE, hidden.protection());
        assertTrue(Double.isNaN(hidden.finalP10()));
    }

    @Test
    void explicitBoundariesMergeOnlyVerifiedAdjacentVisualClusters() {
        assertEquals(List.of(new ClusterPaint(0, 2, WHITE), new ClusterPaint(2, 3, WHITE),
                        new ClusterPaint(3, 5, BLACK), new ClusterPaint(5, 6, WHITE)),
                mergeVerifiedClusters(List.of(WHITE, WHITE, WHITE, BLACK, BLACK, WHITE), Set.of(0, 2, 6)));
        assertEquals(List.of(new ClusterPaint(0, 1, WHITE)), mergeVerifiedClusters(List.of(WHITE), Set.of(1)));
        assertThrows(IllegalArgumentException.class, () -> mergeVerifiedClusters(List.of(WHITE), Set.of(2)));
    }

    @Test
    void optInClusterEvaluationUsesIndependentBackgroundsAndSkipsHiddenClusters() {
        var dark = new ClusterInput(new Input(WHITE, false, true, true, 4.5, 1, 20, 0,
                Background.declared(BLACK)), null);
        var light = new ClusterInput(new Input(WHITE, false, true, true, 4.5, 1, 20, 0,
                Background.declared(WHITE)), null);
        var hidden = new ClusterInput(new Input(WHITE, false, true, true, 4.5, 0, 20, 0,
                Background.declared(BLACK)), null);
        var decision = evaluateVerifiedClusters(List.of(dark, dark, hidden, dark, light, light), Set.of(5), 0);
        assertEquals(List.of(new ClusterPaint(0, 2, WHITE), new ClusterPaint(3, 4, WHITE),
                new ClusterPaint(4, 5, BLACK), new ClusterPaint(5, 6, BLACK)), decision.paints());
        assertTrue(decision.decisions().get(2).hidden());
        assertThrows(IllegalArgumentException.class, () -> evaluateVerifiedClusters(List.of(dark), Set.of(2), 0));
    }

    private static Input input(int argb, Background background) {
        return new Input(argb, false, true, true, 4.5, 1, 20, WINDOW, background);
    }

    private static Sample sample(int cluster, int argb) {
        return Sample.actualSrgb(cluster, argb, 1, 1, true);
    }

    private static List<Sample> brightSpot(double outlineCoverage) {
        var samples = new ArrayList<Sample>();
        for (int cluster = 0; cluster < 11; cluster++) {
            samples.add(cluster == 4 ? Sample.actualSrgb(cluster, WHITE, 1, outlineCoverage, true)
                    : sample(cluster, BLACK));
        }
        return samples;
    }
}
