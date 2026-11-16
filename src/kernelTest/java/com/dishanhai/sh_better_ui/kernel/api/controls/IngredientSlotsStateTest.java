// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IngredientSlotsStateTest {
    @Test void compactViewportUsesFewerRowsAndKeepsIconsVisible() {
        var fit = IngredientSlotsState.fit(180, 106, 6, 6, 18, 2);
        assertEquals(6, fit.columns());
        assertEquals(5, fit.rows());
        assertEquals(30, fit.capacity());
        assertEquals(0, IngredientSlotsState.fit(17, 106, 6, 6, 18, 2).capacity());
    }
    @Test void clickAndDragHaveDistinctOutcomes() {
        var state = new IngredientSlotsState(9, 3);
        assertTrue(state.press(0, 10, 10, true));
        assertEquals(new IngredientSlotsState.Action(IngredientSlotsState.Kind.CHOOSE, 0, 0), state.release(0));
        assertTrue(state.press(0, 10, 10, true));
        state.move(20, 10, 1);
        assertEquals(1, state.target());
        assertEquals(new IngredientSlotsState.Action(IngredientSlotsState.Kind.MOVE, 0, 1), state.release(1));
        assertEquals(-1, state.pressed());
    }

    @Test void cancelledOrInvalidDropNeverActivates() {
        var state = new IngredientSlotsState(5, 3);
        state.press(1, 0, 0, true);
        state.move(12, 0, -1);
        assertEquals(IngredientSlotsState.Kind.NONE, state.release(-1).kind());
        state.press(1, 0, 0, true);
        state.move(12, 0, 2);
        state.cancel();
        assertEquals(IngredientSlotsState.Kind.NONE, state.release(2).kind());
        assertFalse(state.press(5, 0, 0, true));
    }

    @Test void keyboardNavigationNeverWrapsRowsOrEntersMissingCells() {
        var state = new IngredientSlotsState(5, 3);
        assertEquals(0, state.selected());
        assertFalse(state.navigate(-1, 0));
        assertTrue(state.navigate(1, 0));
        assertTrue(state.navigate(1, 0));
        assertFalse(state.navigate(1, 0));
        assertFalse(state.navigate(0, 1));
        assertTrue(state.navigate(-1, 0));
        assertTrue(state.navigate(0, 1));
        assertEquals(4, state.selected());
    }
}
