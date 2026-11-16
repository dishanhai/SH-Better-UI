// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.controls;

import com.dishanhai.sh_better_ui.kernel.api.controls.AnimatedListState;
import com.dishanhai.sh_better_ui.kernel.api.controls.AnimatedListState.Navigation;
import com.dishanhai.sh_better_ui.kernel.api.layout.ViewportReveal;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalEdgeFade;
import com.dishanhai.sh_better_ui.kernel.api.motion.SelectionIndicator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnimatedListStateTest {
    private AnimatedListState list() {
        var state = new AnimatedListState(AnimatedListState.Options.DEFAULT);
        state.layout(List.of("a", "b", "c"), new double[]{30, 50, 40}, 8, 70);
        return state;
    }

    @Test void keyboardSelectionSurvivesScrollUntilPointerActuallyMoves() {
        var s = list();
        s.pointer(10, 10, 0);
        assertEquals(0, s.selected());
        assertTrue(s.navigate(Navigation.DOWN, 100, true));
        s.pointer(10, 10, 0);
        assertEquals(1, s.selected());
        s.pointer(11, 10, 0);
        assertEquals(0, s.selected());
    }

    @Test void navigationClampsAndKeepsVariableHeightRowInView() {
        var s = list();
        s.navigate(Navigation.END, 100, true);
        assertEquals(2, s.selected());
        assertEquals(66, s.scroll().offset(), .001);
        s.navigate(Navigation.DOWN, 110, true);
        assertEquals(2, s.selected());
        assertEquals(-1, s.indexAt(32)); // gap is not an option
    }

    @Test void keysPreserveSelectionAndSeenStateAcrossReordering() {
        var s = list();
        s.select(1);
        s.layout(List.of("c", "b"), new double[]{40, 50}, 8, 70);
        assertEquals(1, s.selected());
        s.layout(List.of("c"), new double[]{40}, 8, 70);
        assertEquals(-1, s.selected());
        s.layout(List.of(), new double[]{}, 8, 70);
        assertFalse(s.navigate(Navigation.UP, 100, false));
        assertEquals(0, s.scroll().maxOffset());
    }

    @Test void loopingAndDisabledArrowNavigationAreExplicit() {
        var d = AnimatedListState.Options.DEFAULT;
        var s = new AnimatedListState(new AnimatedListState.Options(true, true, true, d.animation(), false, 50, true, 56));
        s.layout(List.of("a", "b"), new double[]{30, 30}, 8, 50);
        s.navigate(Navigation.UP, 0, true);
        assertEquals(1, s.selected());
        s.navigate(Navigation.DOWN, 0, true);
        assertEquals(0, s.selected());
        s.options(new AnimatedListState.Options(true, false, true, d.animation(), false, 50, true, 56));
        assertFalse(s.navigate(Navigation.END, 0, true));
    }

    @Test void revealUsesHalfVisibleThresholdAndOnceDoesNotHideAgain() {
        var r = new ViewportReveal();
        r.update(81, 40, 100, ViewportReveal.Animation.POP, false, 0, 0, false);
        assertEquals(0, r.sample(1000).opacity(), .001);
        r.update(80, 40, 100, ViewportReveal.Animation.POP, true, 50, 1000, false);
        assertEquals(0, r.sample(1020).opacity(), .001);
        assertEquals(1, r.sample(1800).opacity(), .001);
        r.update(150, 40, 100, ViewportReveal.Animation.POP, true, 0, 1800, false);
        assertEquals(1, r.sample(2500).opacity(), .001);
    }

    @Test void revealRetargetIsContinuousAndReducedMotionNeverTranslates() {
        var r = new ViewportReveal();
        r.update(0, 40, 100, ViewportReveal.Animation.SLIDE, false, 0, 100, false);
        var before = r.sample(200);
        r.update(-50, 40, 100, ViewportReveal.Animation.SLIDE, false, 0, 200, false);
        assertEquals(before.y(), r.sample(200).y(), .0001);
        assertEquals(before.opacity(), r.sample(200).opacity(), .0001);
        r.update(0, 40, 100, ViewportReveal.Animation.POP, false, 0, 300, true);
        assertEquals(0, r.sample(300).y(), .0001);
        assertEquals(1, r.sample(300).scale(), .0001);
    }

    @Test void fadesTrackRemainingScrollAndDoNotFadeUnscrollableContent() {
        var f = VerticalEdgeFade.of(0, 400, 100, 56, true);
        assertEquals(0, f.top());
        assertEquals(56, f.bottom());
        assertEquals(1, f.opacity(20, 100), .001);
        assertEquals(0, f.opacity(100, 100), .001);
        var g = VerticalEdgeFade.of(10, 80, 100, 56, true);
        assertEquals(1, g.opacity(0, 100), .001);
        assertEquals(1, VerticalEdgeFade.of(50, 400, 100, 56, false).opacity(0, 100), .001);
    }

    @Test void indicatorInterruptsWithoutJumpingAndSnapsForReducedMotion() {
        var r = new SelectionIndicator();
        r.target(0, 30, 0, false);
        r.target(80, 50, 100, false);
        r.advance(120);
        double before = r.y();
        r.target(20, 40, 120, false);
        assertEquals(before, r.y(), .001);
        r.target(20, 40, 130, true);
        assertEquals(20, r.y(), .001);
        assertEquals(40, r.height(), .001);
        r.hide();
        assertFalse(r.visible());
    }

    @Test void visualHitFollowsEntranceAndRejectsClippedOrInvisibleRows() {
        var s = list();
        s.advance(100, false);
        assertEquals(-1, s.hit(40, 15, 100, 100));
        s.advance(1000, false);
        assertEquals(0, s.hit(40, 15, 100, 1000));
        assertEquals(-1, s.hit(40, 32, 100, 1000));
        assertEquals(-1, s.hit(40, 71, 100, 1000));
        assertEquals(-1, s.hit(Double.NaN, 15, 100, 1000));
    }

    @Test void invalidLayoutDoesNotDestroyCurrentSelectionOrScroll() {
        var s = list();
        s.select(1);
        assertThrows(IllegalArgumentException.class, () -> s.layout(List.of("a", "a"), new double[]{30, 40}, 8, 70));
        assertThrows(IllegalArgumentException.class, () -> s.layout(List.of("a"), new double[]{Double.NaN}, 8, 70));
        assertEquals(3, s.size());
        assertEquals(1, s.selected());
    }

    @Test void disabledAnimationAndReducedMotionLeaveVisibleRowsImmediatelyUsable() {
        var r = new ViewportReveal();
        r.update(-100, 40, 70, ViewportReveal.Animation.NONE, false, 999, 0, false);
        assertEquals(new ViewportReveal.Pose(0, 1, 1), r.sample(0));
        r.update(0, 40, 70, ViewportReveal.Animation.POP, false, 999, 100, true);
        assertEquals(new ViewportReveal.Pose(0, 1, 1), r.sample(250));
    }

    @Test void scrollbarCancellationRestoresOffsetAndSmoothMotionDoesNotResumeIt() {
        var s = list();
        s.scroll().scrollTo(20);
        s.synchronizeScroll();
        assertTrue(s.scroll().beginDrag(65, 70));
        s.scroll().dragTo(0, 70);
        assertTrue(s.scroll().cancelDrag());
        s.synchronizeScroll();
        s.advance(1000, false);
        assertEquals(20, s.scroll().offset(), .001);
    }
}
