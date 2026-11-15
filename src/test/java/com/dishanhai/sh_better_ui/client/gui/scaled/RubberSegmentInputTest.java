// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RubberSegmentInputTest {
    private static RubberSegmentState segment() {
        var state = new RubberSegmentState(4, 1);
        state.resize(160);
        return state;
    }

    @Test
    void tapStretchesAcrossBothSlotsBeforeSettling() {
        var state = segment();
        state.press(139, 0, false);
        assertEquals(1, state.selected());
        assertEquals(3, state.release(139, true, 10, false));
        assertEquals(new RubberSegmentState.Edges(40, 160), state.sample(160, false));
        assertEquals(new RubberSegmentState.Edges(123, 160), state.sample(330, false));
        assertEquals(new RubberSegmentState.Edges(120, 160), state.sample(450, false));
    }

    @Test
    void dragPreviewUsesTheReleaseTargetAndKeepsSelectionUntilRelease() {
        var state = segment();
        state.press(60, 0, false);
        state.drag(85, 35, false);
        assertEquals(1, state.selected());
        assertEquals(3, state.preview(40, false));
        assertEquals(2, state.preview(140, false));
        assertEquals(3, state.release(85, true, 40, false));
    }

    @Test
    void reducedMotionClampsTheThumbAndCancelsWithoutChangingSelection() {
        var state = segment();
        state.press(60, 0, true);
        state.drag(-80, 20, true);
        assertEquals(new RubberSegmentState.Edges(0, 40), state.sample(20, true));
        assertEquals(0, state.preview(20, true));
        assertTrue(state.cancel());
        assertEquals(1, state.release(-80, false, 40, true));
        assertEquals(new RubberSegmentState.Edges(40, 80), state.sample(40, true));
    }

    @Test
    void thumbCompressesAtTheBoundaryAndResizeCancelsTheGesture() {
        var state = segment();
        state.press(60, 0, false);
        state.drag(240, 20, false);
        var edges = state.sample(20, false);
        assertEquals(160.0, edges.right());
        assertTrue(edges.left() > 120 && edges.left() < 160);
        state.resize(200);
        assertEquals(1, state.release(240, false, 30, false));
        assertEquals(new RubberSegmentState.Edges(50, 100), state.sample(30, false));
    }
}
