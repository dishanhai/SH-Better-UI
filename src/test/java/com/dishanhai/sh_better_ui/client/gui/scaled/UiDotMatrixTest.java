package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证点阵的限额、颜色与透明度动画、输入反馈、可访问性及裁切。 */
class UiDotMatrixTest {
    private static final Bounds AREA = new Bounds(10, 20, 120, 96);
    private static final int ACCENT = 0xFFF2A0FF;
    private static final UiPreferences REDUCED = new UiPreferences(false, true, true);
    private static final DotMatrixProfile PROFILE = new DotMatrixProfile(
            new DotMatrixProfile.Palette(0, 0xFF51256C, ACCENT, 0f),
            new DotMatrixProfile.Grid(8, 2, 1f, 93L),
            new DotMatrixProfile.Motion(DotMatrixProfile.Effect.STEADY, 2000, 0f, 0, 2f),
            new DotMatrixProfile.Interaction(20, 40));

    @Test
    void sharedBeatBrightensTheFieldAndHonorsReducedMotion() {
        var matrix = new UiDotMatrix(PROFILE);
        assertNotEquals(colors(matrix, UiPreferences.DEFAULT, 0f), colors(matrix, UiPreferences.DEFAULT, 0.75f));
        assertEquals(colors(matrix, REDUCED, 0f), colors(matrix, REDUCED, 0.75f));
    }

    private static List<Integer> colors(UiDotMatrix matrix, UiPreferences preferences, float beat) {
        var colors = new ArrayList<Integer>();
        matrix.forEachDot(AREA, null, 200L, preferences, beat, (left, top, right, bottom, argb) -> {},
                (x, y, radius, feather, argb) -> colors.add(argb));
        return colors;
    }

    @Test
    void floatingHasAHardCapAndReturnsToTheGridBetweenCycles() {
        DotMatrixProfile profile = PROFILE.withMotion(new DotMatrixProfile.Motion(
                DotMatrixProfile.Effect.STEADY, 2000, 1f, 7, 2f));
        UiDotMatrix matrix = new UiDotMatrix(profile);
        List<Quad> rest = frame(matrix, 0L);
        assertEquals(7, movedDots(rest, frame(matrix, 1000L)));
        assertEquals(rest, frame(matrix, 2000L));
        assertEquals(7, movedDots(rest, frame(matrix, 3000L)));
        assertNotEquals(frame(matrix, 1000L), frame(matrix, 3000L));
        assertEquals(frame(new UiDotMatrix(profile), 57000L), frame(matrix, 57000L));
    }

    @Test
    void zeroProbabilityLimitOrDistanceDisablesFloating() {
        var steady = DotMatrixProfile.Effect.STEADY;
        for (var motion : List.of(new DotMatrixProfile.Motion(steady, 2000, 0f, 7, 2f),
                new DotMatrixProfile.Motion(steady, 2000, 1f, 0, 2f),
                new DotMatrixProfile.Motion(steady, 2000, 1f, 7, 0f))) {
            UiDotMatrix matrix = new UiDotMatrix(PROFILE.withMotion(motion));
            assertEquals(frame(matrix, 0L), frame(matrix, 1000L));
        }
    }

    @Test
    void changingProbabilityAndCapUpdatesTheCurrentCycle() {
        UiDotMatrix matrix = new UiDotMatrix(PROFILE);
        List<Quad> rest = frame(matrix, 1000L);
        matrix.setProfile(PROFILE.withMotion(new DotMatrixProfile.Motion(
                DotMatrixProfile.Effect.STEADY, 2000, 1f, 7, 2f)));
        assertEquals(7, movedDots(rest, frame(matrix, 1000L)));
        matrix.setProfile(PROFILE.withMotion(new DotMatrixProfile.Motion(
                DotMatrixProfile.Effect.STEADY, 2000, 1f, 3, 2f)));
        assertEquals(3, movedDots(rest, frame(matrix, 1000L)));
        matrix.setProfile(PROFILE);
        assertEquals(rest, frame(matrix, 1000L));
    }

    @Test
    void hoverIsLocalAndCoordinatesOutsideTheAreaLeaveItIdle() {
        UiDotMatrix matrix = new UiDotMatrix(PROFILE);
        List<Quad> idle = frame(matrix, 0L);
        List<Quad> hover = frame(matrix, AREA, AREA.x() + 4, AREA.y() + 4, 0L, UiPreferences.DEFAULT);
        assertEquals(0, movedDots(idle, hover));
        assertNotEquals(idle.getFirst().argb(), hover.getFirst().argb());
        assertEquals(idle.getLast(), hover.getLast());
        assertEquals(idle, frame(matrix, AREA, AREA.right(), AREA.y() + 4, 0L, UiPreferences.DEFAULT));
        Bounds translated = new Bounds(Integer.MAX_VALUE - 1, 20, AREA.width(), AREA.height());
        assertEquals(hover, frame(matrix, translated, translated.x() + 4d, translated.y() + 4,
                0L, UiPreferences.DEFAULT));
    }

    @Test
    void pointerWaveStaysAroundTheCursorAndMovesWithIt() {
        var style = PROFILE.withInteraction(new DotMatrixProfile.Interaction(0, 40))
                .withPointerWave(new DotMatrixProfile.PointerWave(16, 4f));
        var matrix = new UiDotMatrix(style);
        var idle = frame(matrix, 0L);
        double leftX = AREA.x() + 28d, rightX = AREA.x() + 84d, y = AREA.y() + 36d;
        int centerDot = 4 * 15 + 3;
        int crestDot = 4 * 15 + 5;

        var left = frame(matrix, AREA, leftX, y, 0L, UiPreferences.DEFAULT);
        assertEquals(idle.get(centerDot), left.get(centerDot));
        assertEquals(idle.get(crestDot).top() - 4f, left.get(crestDot).top());
        assertNotEquals(idle.get(crestDot).argb(), left.get(crestDot).argb());
        assertEquals(left, frame(matrix, AREA, leftX, y, 1000L, UiPreferences.DEFAULT));

        var right = frame(matrix, AREA, rightX, y, 1000L, UiPreferences.DEFAULT);
        assertEquals(idle.get(crestDot), right.get(crestDot));
        assertTrue(movedDots(idle, right) > 0);
        assertEquals(idle, frame(matrix, AREA, AREA.right(), y, 1000L, UiPreferences.DEFAULT));
        assertEquals(idle, frame(matrix, 1000L));

        var reduced = frame(matrix, AREA, leftX, y, 1000L, REDUCED);
        assertEquals(0, movedDots(idle, reduced));
        assertNotEquals(idle.get(crestDot).argb(), reduced.get(crestDot).argb());
        matrix.setProfile(style.withPointerWave(DotMatrixProfile.PointerWave.NONE));
        assertEquals(idle, frame(matrix, AREA, leftX, y, 1000L, UiPreferences.DEFAULT));
    }

    @Test
    void rippleIgnoresOutsideClicksAndExpiresAfterLatestPulse() {
        UiDotMatrix matrix = new UiDotMatrix(PROFILE);
        List<Quad> idle = frame(matrix, 0L);
        matrix.pulse(AREA, AREA.x() + 28, AREA.y() + 36, 2000L);
        assertNotEquals(idle, frame(matrix, 2016L));
        List<Quad> first = frame(matrix, 2160L);
        matrix.pulse(AREA, AREA.right(), AREA.y(), 2150L);
        assertEquals(first, frame(matrix, 2160L));
        matrix.pulse(AREA, AREA.x() + 84, AREA.y() + 36, 2150L);
        assertNotEquals(first, frame(matrix, 2160L));
        assertEquals(idle, frame(matrix, 2150L + ClickRipple.DURATION_MS));
    }

    @Test
    void reducedMotionKeepsStillDotsAndLocalFeedbackAndHighContrastUsesASolidBase() {
        UiDotMatrix matrix = new UiDotMatrix(PROFILE.withMotion(new DotMatrixProfile.Motion(
                DotMatrixProfile.Effect.TWINKLE, 2000, 1f, 12, 2f)));
        List<Quad> still = frame(matrix, AREA, Double.NaN, Double.NaN, 0L, REDUCED);
        assertEquals(still, frame(matrix, AREA, Double.NaN, Double.NaN, 1000L, REDUCED));
        matrix.pulse(AREA, AREA.x() + 4, AREA.y() + 4, 1000L);
        List<Quad> feedback = frame(matrix, AREA, Double.NaN, Double.NaN, 1080L, REDUCED);
        assertEquals(0, movedDots(still, feedback));
        assertNotEquals(still.getFirst().argb(), feedback.getFirst().argb());
        assertEquals(still.getLast(), feedback.getLast());
        assertEquals(List.of(new Quad(0, 0, AREA.width(), AREA.height(), DarkModeTheme.BACKGROUND)),
                frame(matrix, AREA, AREA.x() + 4, AREA.y() + 4, 1080L, new UiPreferences(true, false, true)));
    }

    @Test
    void customEffectAndPalettePreserveTheGridAndKeepArgb() {
        UiDotMatrix matrix = new UiDotMatrix(PROFILE);
        List<Quad> idle = frame(matrix, 0L);
        matrix.setProfile(PROFILE.withMotion(new DotMatrixProfile.Motion(
                (x, y, phase, variation) -> x < 0.5f ? 1f : Float.NaN, 2000, 0f, 0, 0f)));
        List<Quad> lit = frame(matrix, 0L);
        assertEquals(0, movedDots(idle, lit));
        assertEquals(ACCENT, lit.getFirst().argb());
        assertEquals(idle.getLast(), lit.getLast());

        int translucent = 0x4050AAE0;
        matrix.setProfile(PROFILE.withPalette(new DotMatrixProfile.Palette(0, translucent, translucent, 0f)));
        List<Quad> recolored = frame(matrix, 0L);
        assertEquals(0, movedDots(idle, recolored));
        assertTrue(recolored.stream().allMatch(quad -> quad.argb() == translucent));
    }

    @Test
    void aFadingDotAndItsGlowDisappearThenReturnToTheSameCell() {
        int period = 1600;
        int sampleMs = 20;
        Bounds cell = new Bounds(AREA.x(), AREA.y(), 8, 8);
        var lit = PROFILE.withPalette(new DotMatrixProfile.Palette(0, PROFILE.palette().dotArgb(), ACCENT, 1f));
        Quad original = frame(new UiDotMatrix(lit), cell, Double.NaN, Double.NaN, 0L,
                UiPreferences.DEFAULT).getLast();
        var matrix = new UiDotMatrix(lit.withFade(new DotMatrixProfile.Fade(1f, period)));
        List<Integer> alpha = new ArrayList<>();
        for (long time = 0L; time <= period * 3L; time += sampleMs) {
            var quads = frame(matrix, cell, Double.NaN, Double.NaN, time, UiPreferences.DEFAULT);
            assertTrue(quads.size() <= 2);
            if (quads.isEmpty()) {
                alpha.add(0);
                continue;
            }
            Quad dot = quads.getLast();
            assertEquals(new Quad(original.left(), original.top(), original.right(), original.bottom(), dot.argb()), dot);
            alpha.add(dot.argb() >>> 24);
        }

        int samplesPerCycle = period / sampleMs;
        assertEquals(alpha.subList(0, samplesPerCycle), alpha.subList(samplesPerCycle, samplesPerCycle * 2));
        int visible = alpha.indexOf(255);
        assertTrue(visible >= 0);
        int hidden = visible + alpha.subList(visible, alpha.size()).indexOf(0);
        assertTrue(hidden > visible && hidden + 1 < alpha.size());
        assertEquals(0, alpha.get(hidden + 1));
        int returned = hidden + alpha.subList(hidden, alpha.size()).indexOf(255);
        assertTrue(returned > hidden);
        assertTrue(alpha.subList(visible, hidden).stream().anyMatch(value -> value > 0 && value < 255));
        assertTrue(alpha.subList(hidden, returned).stream().anyMatch(value -> value > 0 && value < 255));
        assertTrue(frame(matrix, cell, cell.x() + 4d, cell.y() + 4d, (long) hidden * sampleMs,
                UiPreferences.DEFAULT).isEmpty());
    }

    @Test
    void fadeResamplesEachCycleIndependentlyOfFrameOrderAndResize() {
        var profile = PROFILE.withFade(new DotMatrixProfile.Fade(0.5f, 2400));
        var matrix = new UiDotMatrix(profile);
        List<Quad> earlier = frame(matrix, -400L);
        List<Quad> later = frame(matrix, 2000L);
        assertNotEquals(earlier, later);
        assertEquals(earlier, frame(matrix, -400L));

        var reversed = new UiDotMatrix(profile);
        assertEquals(later, frame(reversed, 2000L));
        assertEquals(earlier, frame(reversed, -400L));
        Bounds wider = new Bounds(AREA.x(), AREA.y(), AREA.width() + 16, AREA.height());
        assertEquals(earlier, frame(matrix, wider, Double.NaN, Double.NaN, -400L, UiPreferences.DEFAULT)
                .stream().filter(quad -> quad.right() <= AREA.width()).toList());
    }

    @Test
    void gradientInterpolatesArgbThroughItsLoopWithCustomFlow() {
        var gradient = new DotMatrixProfile.Gradient(List.of(
                new DotMatrixProfile.ColorStop(0f, 0x40FF0000),
                new DotMatrixProfile.ColorStop(0.5f, 0xC00000FF)), 2000, (x, y, phase) -> x + y + phase);

        assertEquals(0x40FF0000, gradient.sample(0f, 0f, 0f));
        assertEquals(0x80800080, gradient.sample(0.125f, 0.0625f, 0.0625f));
        assertEquals(0xC00000FF, gradient.sample(0.25f, 0.125f, 0.125f));
        assertEquals(0xA04000BF, gradient.sample(0.25f, 0.25f, 0.125f));
        assertEquals(0x40FF0000, gradient.sample(0.5f, 0.25f, 0.25f));
    }

    @Test
    void reducedMotionDisablesFadeAndFreezesGradientButKeepsItsColors() {
        int base = 0x804A6980;
        var gradient = new DotMatrixProfile.Gradient(List.of(
                new DotMatrixProfile.ColorStop(0f, 0x80FF0000),
                new DotMatrixProfile.ColorStop(0.5f, 0xFF0000FF)), 1600, (x, y, phase) -> phase);
        var profile = PROFILE.withPalette(new DotMatrixProfile.Palette(0, base, base, 0f)).withGradient(gradient);
        var matrix = new UiDotMatrix(profile.withFade(new DotMatrixProfile.Fade(1f, 2000)));
        List<Quad> still = frame(matrix, AREA, Double.NaN, Double.NaN, 0L, REDUCED);
        assertEquals(frame(new UiDotMatrix(PROFILE), 0L).size(), still.size());
        assertTrue(frame(matrix, 800L).size() < still.size());
        assertEquals(still, frame(matrix, AREA, Double.NaN, Double.NaN, 800L, REDUCED));
        assertTrue(still.stream().allMatch(quad -> quad.argb() >>> 24 == 64
                && (quad.argb() >>> 16 & 255) > (quad.argb() & 255)));

        List<Quad> moving = frame(new UiDotMatrix(profile), 800L);
        assertEquals(0, movedDots(still, moving));
        assertTrue(moving.stream().allMatch(quad -> quad.argb() >>> 24 == 128
                && (quad.argb() >>> 16 & 255) < (quad.argb() & 255)));
    }

    @Test
    void gridIsBoundedAtLargeSizesAndPreservesSharedCellsOnResize() {
        UiDotMatrix matrix = new UiDotMatrix(PROFILE);
        Bounds wider = new Bounds(AREA.x(), AREA.y(), AREA.width() + 16, AREA.height());
        assertEquals(frame(matrix, 0L), frame(matrix, wider, Double.NaN, Double.NaN, 0L, UiPreferences.DEFAULT)
                .stream().filter(quad -> quad.right() <= AREA.width()).toList());

        matrix.setProfile(PROFILE.withGrid(new DotMatrixProfile.Grid(1, 1, 1f, 93L)));
        Bounds large = new Bounds(0, 0, 7680, 4320);
        List<Quad> quads = frame(matrix, large, Double.NaN, Double.NaN, 0L, UiPreferences.DEFAULT);
        assertTrue(quads.size() <= UiDotMatrix.MAX_GRID_CELLS);
        assertTrue(quads.getLast().right() >= large.width() - 32);
        assertTrue(quads.getLast().bottom() >= large.height() - 32);
    }

    @Test
    void allQuadsStayInsideEvenWhenTheDotsDriftAtAnEdge() {
        UiDotMatrix matrix = new UiDotMatrix(PROFILE.withPalette(new DotMatrixProfile.Palette(0xFF000000,
                        0xFF51256C, ACCENT, 1f))
                .withMotion(new DotMatrixProfile.Motion(DotMatrixProfile.Effect.STEADY, 2000, 1f, 200, 9f)));
        Bounds area = new Bounds(30, 40, 53, 41);
        List<Quad> quads = frame(matrix, area, Double.NaN, Double.NaN, 1000L, UiPreferences.DEFAULT);
        assertTrue(quads.size() > 1);
        for (Quad quad : quads) {
            assertTrue(quad.left() >= 0 && quad.top() >= 0);
            assertTrue(quad.right() <= area.width() && quad.bottom() <= area.height());
            assertTrue(quad.right() > quad.left() && quad.bottom() > quad.top());
        }
        assertTrue(frame(matrix, new Bounds(0, 0, 0, 10), 0, 0, 1000L, UiPreferences.DEFAULT).isEmpty());
    }

    private static List<Quad> frame(UiDotMatrix matrix, long time) {
        return frame(matrix, AREA, Double.NaN, Double.NaN, time, UiPreferences.DEFAULT);
    }

    private static List<Quad> frame(UiDotMatrix matrix, Bounds area, double x, double y, long time,
                                   UiPreferences preferences) {
        List<Quad> quads = new ArrayList<>();
        matrix.forEachQuad(area, x, y, time, preferences,
                (left, top, right, bottom, argb) -> quads.add(new Quad(left, top, right, bottom, argb)));
        return quads;
    }

    private static int movedDots(List<Quad> from, List<Quad> to) {
        assertEquals(from.size(), to.size());
        int moved = 0;
        for (int i = 0; i < from.size(); i++) {
            if (from.get(i).left() != to.get(i).left() || from.get(i).top() != to.get(i).top()) moved++;
        }
        return moved;
    }

    private record Quad(float left, float top, float right, float bottom, int argb) {}
}
