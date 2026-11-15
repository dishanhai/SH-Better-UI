// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.dishanhai.sh_better_ui.client.gui.scaled.UiCodeSlots.Status.ERROR;
import static com.dishanhai.sh_better_ui.client.gui.scaled.UiCodeSlots.Status.IDLE;
import static com.dishanhai.sh_better_ui.client.gui.scaled.UiCodeSlots.Status.SUCCESS;
import static org.junit.jupiter.api.Assertions.*;

class CodeSlotsInputTest {
    private final CodeSlotsState state = new CodeSlotsState(6);
    private final List<String> edits = new ArrayList<>();
    private final List<String> completed = new ArrayList<>();

    CodeSlotsInputTest() {
        state.onChange = edits::add;
        state.onComplete = completed::add;
    }

    @Test
    void pasteFiltersAndTruncatesThenCompletesOnlyWhenAllSlotsAreFilled() {
        assertTrue(state.insert("12-3 45x67", 100));
        assertEquals(List.of("123456"), completed);
        assertTrue(state.insert("9", 200));
        assertEquals(List.of("123456", "123459"), edits);
        assertEquals(List.of("123456"), completed);

        state.select(2);
        state.delete(false, 300);
        assertEquals(5, state.filled());
        assertEquals('4', state.digit(3));
        state.insert("3", 400);
        assertEquals(List.of("123456", "123459"), completed);
    }

    @Test
    void rejectionDrainsBackwardsAndClearsExactlyOnce() {
        state.setValue("123456", 0);
        state.setStatus(ERROR, 1000, false);
        assertFalse(state.insert("9", 1001));
        assertEquals(0, state.transitionAge(5, 1000));
        assertEquals(-100, state.transitionAge(0, 1000));
        state.advance(1399);
        assertEquals("123456", state.value());
        state.advance(1400);
        state.advance(1500);
        assertEquals(List.of(""), edits);
        assertEquals(0, state.selected());
        assertTrue(state.insert("1", 1501));
        assertEquals(IDLE, state.status());
    }

    @Test
    void successLocksInputAndNewAuthoritativeValueCancelsAnyOldRejection() {
        state.insert("123456", 100);
        state.setStatus(SUCCESS, 101, false);
        assertFalse(state.delete(true, 102));
        assertFalse(state.select(0));
        state.setValue("12", 103);
        assertEquals(IDLE, state.status());

        state.setStatus(ERROR, 104, true);
        state.setValue("74", 105);
        state.advance(500);
        assertEquals("74", state.value());
        assertEquals(List.of("123456"), edits);
        assertEquals(List.of("123456"), completed);
    }

    @Test
    void clickSelectsEachVisibleSlotEvenWithAnUnevenWidth() {
        for (int width : List.of(43, 49, 57, 121, 184)) {
            int gap = CodeSlotsPaint.gap(width, 6);
            int usable = width - gap * 5;
            for (int slot = 0; slot < 6; slot++) {
                int first = slot * gap + slot * usable / 6;
                int last = slot * gap + (slot + 1) * usable / 6 - 1;
                assertEquals(slot, CodeSlotsPaint.slotAt(first, width, 6));
                assertEquals(slot, CodeSlotsPaint.slotAt(last, width, 6));
            }
        }
    }
}
