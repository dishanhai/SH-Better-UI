// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NavigationRailStateTest {
    private NavigationRailState state() { return new NavigationRailState(4, 0, i -> i != 1); }
    @Test void focusIsNotSelectionAndSkipsDisabled() {
        var s = state(); s.move(1); assertEquals(2, s.focusedIndex()); assertEquals(0, s.selectedIndex());
        assertTrue(s.activate()); assertEquals(2, s.selectedIndex()); assertFalse(s.activate());
        s.move(-1); assertEquals(0, s.focusedIndex()); s.move(-1); assertEquals(3, s.focusedIndex());
    }
    @Test void edgesSkipDisabledAndDoNotActivate() {
        var s = new NavigationRailState(5, 2, i -> i > 0 && i < 4);
        s.edge(false); assertEquals(1, s.focusedIndex()); s.edge(true); assertEquals(3, s.focusedIndex());
        assertEquals(2, s.selectedIndex());
    }
    @Test void releaseMustMatchPressAndLeavingCancelsPermanently() {
        var s = state(); assertFalse(s.press(1)); assertFalse(s.press(-1));
        assertTrue(s.press(2)); s.drag(-1); s.drag(2); assertFalse(s.release(2));
        assertEquals(0, s.selectedIndex()); assertTrue(s.press(3)); assertTrue(s.release(3));
        assertFalse(s.release(3));
    }
    @Test void cancelSyncAndKeyboardClearPress() {
        var s = state(); s.press(2); s.cancel(); assertFalse(s.release(2));
        s.press(2); assertTrue(s.select(3)); assertFalse(s.release(2));
        s.press(2); s.move(1); assertFalse(s.release(2));
    }
    @Test void reentryFocusesSelectionButRetainsPointerPress() {
        var s = state(); s.move(1); s.focusEntered(); assertEquals(0, s.focusedIndex());
        s.press(3); s.focusEntered(); assertEquals(3, s.focusedIndex());
        assertTrue(s.release(3)); assertEquals(3, s.selectedIndex());
    }
    @Test void narrationFollowsHoverUnlessKeyboardFocused() {
        var s = state(); s.move(1);
        assertEquals(3, s.narrationIndex(false, 3));
        assertEquals(2, s.narrationIndex(true, 3));
        assertEquals(2, s.narrationIndex(false, 1));
        assertEquals(2, s.narrationIndex(false, -1));
    }
    @Test void singleEnabledEntryRemainsStableAndInvalidInputsFail() {
        var s = new NavigationRailState(3, 1, i -> i == 1); s.move(1); s.move(-1); s.edge(true);
        assertEquals(1, s.focusedIndex()); assertFalse(s.activate());
        assertThrows(IllegalArgumentException.class, () -> new NavigationRailState(2, 0, i -> false));
        assertThrows(IndexOutOfBoundsException.class, () -> new NavigationRailState(0, 0, i -> true));
    }
}
