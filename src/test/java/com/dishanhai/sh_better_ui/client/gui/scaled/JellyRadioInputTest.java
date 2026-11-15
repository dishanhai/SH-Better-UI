// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JellyRadioInputTest {
    @Test
    void skipsDisabledOptionsAndWrapsFromEitherEnd() {
        var state = new JellyRadioState(new boolean[]{false, true, false, true}, 0);
        assertEquals(1, state.selected());
        assertEquals(3, state.step(1, 1));
        assertEquals(3, state.step(1, -1));
        assertEquals(1, state.step(3, 1));
        assertEquals(3, state.step(4, -1));
        assertFalse(state.select(2, 0, true));
        assertEquals(-1, new JellyRadioState(new boolean[]{false}, 0).selected());
        assertEquals(-1, new JellyRadioState(new boolean[0], -1).step(-1, 1));
    }

    @Test
    void onlyCommitsTheEnabledChipPressedAndReleased() {
        var state = new JellyRadioState(new boolean[]{true, true, false}, 0);
        assertFalse(state.press(2));
        assertTrue(state.press(1));
        assertFalse(state.release(0, 100, false));
        assertEquals(0, state.selected());
        assertTrue(state.press(1));
        assertTrue(state.cancel());
        assertFalse(state.release(1, 110, false));
        assertTrue(state.press(1));
        assertTrue(state.release(1, 120, false));
        assertEquals(1, state.selected());
        assertFalse(state.release(1, 130, false));
    }

    @Test
    void pointerAnimationGrowsWidthBeforeHeightAndCanBeRetargetedOrReduced() {
        var state = new JellyRadioState(new boolean[]{true, true, true}, 0);
        var initial = state.chip(1, 240, 36, 100, false, false);
        state.select(1, 100, false);
        var growing = state.chip(1, 240, 36, 140, false, false);
        assertTrue(growing.width() > initial.width());
        assertEquals(initial.height(), growing.height());
        state.select(2, 140, false);
        assertEquals(growing, state.chip(1, 240, 36, 140, false, false));
        var reduced = state.chip(2, 240, 36, 145, true, false);
        var expected = new JellyRadioState(new boolean[]{true, true, true}, 2);
        assertEquals(expected.chip(2, 240, 36, 145, false, false), reduced);
        assertEquals(reduced, state.chip(2, 240, 36, 160, false, false));
    }

    @Test
    void hitTestingUsesTheVisibleChipShapeAndKeepsGeometryWithinBounds() {
        for (int width : new int[]{30, 90, 280}) {
            var state = new JellyRadioState(new boolean[]{true, true, true}, 1);
            state.select(0, 100, false);
            for (long time = 100; time < 650; time += 10) {
                for (int i = 0; i < 3; i++) {
                    var chip = state.chip(i, width, 36, time, false, false);
                    assertTrue(chip.x() >= 0 && chip.x() + chip.width() <= width + .001f);
                    assertTrue(chip.y() >= 0 && chip.y() + chip.height() <= 36);
                }
                var selected = state.chip(0, width, 36, time, false, false);
                assertEquals(0, state.hit(selected.x() + selected.width() / 2, 18, width, 36, time, false));
                assertEquals(-1, state.hit(-1, 18, width, 36, time, false));
            }
        }
        assertFalse(new JellyRadioState.Chip(0, 0, 20, 20).contains(0, 0));
    }
}
