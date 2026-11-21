// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NavigationTest {
    @Test void tabHitsMatchClippedDrawingAndExcludeGaps() {
        var strip = new TabStrip(new int[]{20, 30, 10}, 5, 25);
        assertEquals(45, strip.maxScroll());
        assertEquals(-10, strip.start(0, 10));
        assertEquals(10, strip.end(0, 10));
        assertEquals(0, strip.left(0, 10));
        assertEquals(10, strip.right(0, 10));
        assertEquals(15, strip.left(1, 10));
        assertEquals(25, strip.right(1, 10));
        assertEquals(-1, strip.hitTest(-1, 10));
        assertEquals(0, strip.hitTest(9, 10));
        assertEquals(-1, strip.hitTest(10, 10));
        assertEquals(-1, strip.hitTest(14, 10));
        assertEquals(1, strip.hitTest(15, 10));
        assertEquals(1, strip.hitTest(24, 10));
        assertEquals(-1, strip.hitTest(25, 10));
        assertEquals(0, strip.clampScroll(Integer.MIN_VALUE));
        assertEquals(45, strip.clampScroll(Integer.MAX_VALUE));
    }

    @Test void revealAndKeyboardHandleNarrowAndEmptyViewports() {
        var strip = new TabStrip(new int[]{10, 30, 10}, 5, 15);
        assertEquals(0, strip.reveal(0, 0));
        assertEquals(15, strip.reveal(1, 0)); // Oversized tab aligns its leading edge.
        assertEquals(45, strip.reveal(2, 0));
        assertEquals(0, strip.reveal(0, 45));
        assertEquals(0, strip.move(1, Integer.MIN_VALUE));
        assertEquals(2, strip.move(1, Integer.MAX_VALUE));
        assertEquals(1, strip.move(0, 1));

        var empty = new TabStrip(new int[0], 0, 0);
        assertEquals(0, empty.maxScroll());
        assertEquals(-1, empty.move(0, 1));
        assertEquals(-1, empty.hitTest(0, 0));
        var zeroViewport = new TabStrip(new int[]{10}, 0, 0);
        assertEquals(0, zeroViewport.left(0, 0));
        assertEquals(0, zeroViewport.right(0, 0));
        assertEquals(-1, zeroViewport.hitTest(0, 0));
    }

    @Test void tabGeometryChecksOverflowAndKeepsItsOwnWidths() {
        int[] widths = {Integer.MAX_VALUE};
        var strip = new TabStrip(widths, 0, 1);
        widths[0] = 0;
        assertEquals(Integer.MAX_VALUE - 1, strip.maxScroll());
        assertEquals(1, strip.right(0, Integer.MAX_VALUE));
        assertEquals(0, strip.hitTest(0, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
            () -> new TabStrip(new int[]{Integer.MAX_VALUE, 1}, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TabStrip(new int[]{-1}, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TabStrip(new int[0], -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new TabStrip(new int[0], 0, -1));
    }

    @Test void historyDiscardsForwardBranchOnlyForNewDestinations() {
        var history = new NavigationHistory<>("home");
        assertFalse(history.canBack());
        assertEquals("home", history.back());
        assertTrue(history.push("appearance"));
        assertTrue(history.push("fonts"));
        assertEquals("appearance", history.back());
        assertTrue(history.canForward());
        assertFalse(history.push("appearance"));
        assertEquals("fonts", history.forward());
        assertEquals("appearance", history.back());
        assertTrue(history.push("sound"));
        assertFalse(history.canForward());
        assertEquals("sound", history.forward());
        assertEquals("appearance", history.back());
        assertEquals("home", history.back());
        assertFalse(history.canBack());
        assertEquals("appearance", history.forward());
        assertEquals("sound", history.forward());
        assertThrows(NullPointerException.class, () -> history.push(null));
        assertThrows(NullPointerException.class, () -> new NavigationHistory<>(null));
    }
}
