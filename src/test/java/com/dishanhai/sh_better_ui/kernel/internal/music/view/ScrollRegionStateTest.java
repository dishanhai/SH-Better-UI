// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScrollRegionStateTest {
    @Test void accumulatesSmallTouchpadDeltasAndBubblesAtEdges() {
        var scroll = new ScrollRegionState(); scroll.configure(0, 20, 5);
        assertFalse(scroll.wheel(.25));
        for (int i = 0; i < 3; i++) { assertTrue(scroll.wheel(-.25)); assertEquals(0, scroll.offset()); }
        assertTrue(scroll.wheel(-.25)); assertEquals(1, scroll.offset());
        scroll.moveTo(15); assertFalse(scroll.wheel(-1));
        assertTrue(scroll.wheel(1)); assertEquals(14, scroll.offset());
        assertFalse(scroll.wheel(Double.NaN)); assertFalse(scroll.wheel(Double.POSITIVE_INFINITY));
    }
    @Test void dampedPositionIsFractionalInterruptibleAndEndsWithoutOvershoot() {
        var scroll = new ScrollRegionState();
        scroll.pixelRows(); scroll.configure(0, 20, 5); scroll.setStep(2);
        assertTrue(scroll.wheel(-1, 1000, false));
        assertEquals(0, scroll.offset());
        scroll.advance(1008);
        assertEquals(0, scroll.offset());
        assertTrue(scroll.shift(32) > 0);
        int shift = scroll.shift(32);
        scroll.configure(scroll.offset(), 20, 5);
        assertEquals(shift, scroll.shift(32), "frame configuration must not snap the fractional paint offset");
        assertTrue(scroll.wheel(1, 1016, false));
        for (int i=2;i<50;i++) scroll.advance(1000L + i * 8);
        assertEquals(0, scroll.offset());
        assertEquals(0, scroll.shift(32));
        scroll.wheel(-300, 1500, false);
        for (int i=1;i<50;i++) {
            scroll.advance(1500L + i * 8);
            assertTrue(scroll.offset() <= 15);
        }
        assertEquals(15, scroll.offset());
        assertTrue(scroll.atForwardLimit());
        assertFalse(scroll.wheel(-1, 1900, false));
    }

    @Test void repeatedReducedMotionInputStillRetainsFractionalWheelDeltas() {
        var scroll = new ScrollRegionState(); scroll.configure(0, 20, 5);
        for (int i=0;i<4;i++) assertTrue(scroll.wheel(-.25, i, true));
        assertEquals(1, scroll.offset());
    }

    @Test void supportsPixelScrollStepAndViewportPageSize() {
        var scroll = new ScrollRegionState(); scroll.configure(0, 280, 100); scroll.setStep(24);
        scroll.wheel(-1); assertEquals(24, scroll.offset());
        scroll.wheel(-.25); assertEquals(30, scroll.offset());
        assertEquals(100, scroll.page()); assertEquals(24, scroll.step());
    }
    @Test void directionChangeDoesNotMakeWheelFeelStuck() {
        var scroll = new ScrollRegionState(); scroll.configure(3, 20, 5);
        scroll.wheel(-.75); scroll.wheel(1);
        assertEquals(2, scroll.offset());
    }
    @Test void clampsConfigurationAndLargeMovesWithoutOverflow() {
        var scroll = new ScrollRegionState(); scroll.configure(100, 20, 5);
        assertEquals(15, scroll.offset()); assertEquals(5, scroll.page());
        scroll.moveTo(Long.MAX_VALUE); assertEquals(15, scroll.offset());
        scroll.moveTo(Long.MIN_VALUE); assertEquals(0, scroll.offset());
        scroll.configure(3, 1, 5); assertEquals(0, scroll.maximum()); assertFalse(scroll.wheel(-1));
        scroll.configure(3, 100, 0); assertEquals(0, scroll.maximum()); assertFalse(scroll.beginDrag(5, 100));
    }
    @Test void dragTracksPointerAndCancelRestoresInitialOffset() {
        var scroll = new ScrollRegionState(); scroll.configure(4, 30, 10);
        int top = scroll.thumbTop(100);
        assertTrue(scroll.beginDrag(top + 2, 100));
        scroll.drag(1000, 100); assertEquals(20, scroll.offset());
        assertTrue(scroll.cancelDrag()); assertEquals(4, scroll.offset()); assertFalse(scroll.dragging());
        scroll.beginDrag(98, 100); scroll.endDrag(); assertEquals(20, scroll.offset());
        assertFalse(scroll.cancelDrag());
    }
    @Test void sameConfigurationKeepsDragButResizeCancelsIt() {
        var scroll = new ScrollRegionState(); scroll.configure(2, 30, 10);
        scroll.beginDrag(scroll.thumbTop(100) + 2, 100);
        scroll.configure(scroll.offset(), 30, 10); assertTrue(scroll.dragging());
        scroll.configure(scroll.offset(), 12, 10); assertFalse(scroll.dragging()); assertTrue(scroll.offset() <= 2);
    }
}
