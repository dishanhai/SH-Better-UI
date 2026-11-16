// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.dishanhai.sh_better_ui.kernel.api.controls.PillNavState.*;

class PillNavStateTest {
    private static Options options(Layout layout, HoverEffect effect, Indicator indicator, Collapse collapse) {
        return new Options(layout, Size.MD, Surface.GLASS, ItemStyle.PILL, effect, indicator, collapse, 1, 1, false);
    }
    private static PillNavState nav(Options options) {
        var nav = new PillNavState(4, 0, options);
        nav.layout(600, 280, true, new float[]{30, 45, 60, 30});
        return nav;
    }
    @Test void sourceWidthFormulaAndHalfPixelBoundaryAreSharedByJoinedAndSplit() {
        for (Layout layout : Layout.values()) {
            var nav = nav(options(layout, HoverEffect.FILL, Indicator.DOT, Collapse.AUTO));
            float width = nav.metrics().width();
            assertEquals(layout == Layout.SPLIT ? 362 : 348, width);
            assertFalse(nav.layout(width - .5f, 280, true, new float[]{30,45,60,30}).collapsed());
            assertTrue(nav.layout(width - .6f, 280, true, new float[]{30,45,60,30}).collapsed());
        }
    }
    @Test void controlledActivationReportsTargetWithoutChangingActiveItem() {
        var nav = nav(Options.DEFAULT);
        nav.setSelected(2, true);
        assertEquals(1, nav.activate(1));
        assertEquals(2, nav.selected());
        nav.setSelected(-1, true);
        assertEquals(-1, nav.selected());
        nav.setSelected(0, false);
        nav.activate(3);
        assertEquals(3, nav.selected());
    }
    @Test void pressCancelAndReleaseOffTargetNeverActivate() {
        var nav = nav(Options.DEFAULT);
        assertTrue(nav.press(1));
        assertEquals(-1, nav.release(2));
        assertEquals(0, nav.selected());
        nav.press(1); assertTrue(nav.cancel());
        assertEquals(-1, nav.release(1));
        nav.press(1); assertEquals(1, nav.release(1));
    }
    @Test void collapseAndResizeCloseMenuAndKeepSelection() {
        var nav = nav(Options.DEFAULT);
        nav.layout(100, 280, true, new float[]{30,45,60,30});
        assertTrue(nav.toggle()); assertTrue(nav.isOpen());
        nav.activate(2); assertFalse(nav.isOpen());
        nav.toggle();
        nav.layout(600, 280, true, new float[]{30,45,60,30});
        assertFalse(nav.isOpen()); assertEquals(2, nav.selected());
        nav.layout(100, 44, false, new float[]{30,45,60,30});
        assertFalse(nav.toggle(), "A menu without room for a row must not open");
    }
    @Test void shortMenusScrollAndKeyboardKeepsFocusVisibleAcrossMeasurements() {
        var nav = nav(options(Layout.SPLIT, HoverEffect.FILL, Indicator.DOT, Collapse.ALWAYS));
        nav.layout(180, 160, false, new float[]{30,45,60,30});
        assertEquals(2, nav.metrics().visibleRows());
        nav.toggle(); nav.scroll(2);
        nav.layout(180, 160, false, new float[]{30,45,60,30});
        assertEquals(2, nav.firstRow());
        nav.focusEdge(false); assertEquals(0, nav.firstRow());
        nav.focusEdge(true); assertEquals(2, nav.firstRow());
        nav.moveFocus(1); assertEquals(0, nav.focused()); assertEquals(0, nav.firstRow());
    }
    @Test void maximumBounceSpringsConvergeAfterFastReversalsAndLongFrameGaps() {
        var nav = nav(options(Layout.SPLIT, HoverEffect.GLIDE, Indicator.PILL, Collapse.NEVER));
        nav.sample(0, false, false);
        nav.hover(3);
        for (int t = 16; t < 160; t += 16) nav.sample(t, false, false);
        nav.hover(1); nav.sample(5000, false, false); nav.hover(2);
        Frame frame = null;
        for (int t = 5016; t < 12000; t += 16) frame = nav.sample(t, false, false);
        assertEquals(nav.metrics().slots().get(2).left(), frame.left(), .02);
        assertEquals(nav.metrics().slots().get(2).width(), frame.width(), .02);
        assertEquals(1, frame.alpha(), .001);
        assertEquals(1, frame.scale(), .001);
        assertTrue(Float.isFinite(frame.dotX()));
    }
    @Test void reducedMotionSnapsAllGeometryAndDisclosuresOnTheFirstFrame() {
        var nav = nav(options(Layout.JOINED, HoverEffect.GLIDE, Indicator.DOT, Collapse.NEVER));
        nav.hover(2); nav.press(2);
        Frame frame = nav.sample(0, true, false);
        assertEquals(nav.metrics().slots().get(2).left(), frame.left());
        assertEquals(.95f, frame.scale(), .001);
        assertEquals(1, frame.alpha()); assertEquals(1, frame.dotAlpha()); assertEquals(1, frame.intro());
    }
    @Test void emptyAndInvalidInputCannotCreatePhantomTargetsOrNonFiniteSprings() {
        var empty = new PillNavState(0, 0, Options.DEFAULT);
        empty.layout(80, 100, false, new float[0]);
        assertFalse(empty.toggle()); assertEquals(-1, empty.activate(0));
        assertEquals(0, empty.sample(0, true, false).alpha());
        assertThrows(IllegalArgumentException.class, () -> empty.layout(Float.NaN, 100, false, new float[0]));
        assertThrows(IllegalArgumentException.class, () -> nav(Options.DEFAULT).layout(80, 100, false, new float[]{Float.NaN,0,0,0}));
    }

    @Test void shrinkingAnOpenMenuBelowOneRowClosesIt() {
        var nav = nav(options(Layout.JOINED, HoverEffect.FILL, Indicator.DOT, Collapse.ALWAYS));
        nav.toggle(); assertTrue(nav.isOpen());
        nav.layout(180, 44, true, new float[]{30,45,60,30});
        assertFalse(nav.isOpen()); assertEquals(0, nav.metrics().visibleRows());
    }

    @Test void activeDotAndHoveredGlideHaveIndependentTargetsAndIntroCanReplay() {
        var nav = nav(options(Layout.SPLIT, HoverEffect.GLIDE, Indicator.DOT, Collapse.NEVER));
        nav.setSelected(1, false); nav.hover(3);
        Frame frame = nav.sample(0, true, false);
        var active = nav.metrics().slots().get(1);
        assertEquals(active.left() + active.width() / 2, frame.dotX());
        assertEquals(nav.metrics().slots().get(3).left(), frame.left());
        nav.setOptions(Options.DEFAULT);
        frame = nav.sample(1000, false, false);
        assertEquals(0, frame.intro()); assertEquals(0, frame.introAge());
        assertEquals(1, nav.sample(2000, false, false).intro());
    }
    @Test void roundedHitTestsRejectTransparentCornersAndExclusiveEdges() {
        assertTrue(containsPill(40, 18, 80, 36));
        assertTrue(containsPill(1, 18, 80, 36));
        assertFalse(containsPill(1, 1, 80, 36));
        assertFalse(containsPill(80, 18, 80, 36));
        assertFalse(containsPill(20, -1, 80, 36));
    }
}
