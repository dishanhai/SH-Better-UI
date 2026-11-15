// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwipeRowStateTest {
    private static SwipeRowState row(SwipeRowState.Direction direction, boolean fullSwipe, boolean closeOnAction) {
        var options = new SwipeRowState.Options(direction, 50, .55, .6, .2, 200, fullSwipe, closeOnAction);
        var row = new SwipeRowState(options, 2);
        row.resize(240);
        return row;
    }

    @Test
    void bothDirectionsRevealTheSameDrawer() {
        for (var direction : SwipeRowState.Direction.values()) {
            var row = row(direction, true, true);
            row.begin();
            row.drag(70 * direction.sign());
            assertEquals(70, row.offset(), .001);
            row.release(0, 20);
            assertTrue(row.open());
            assertEquals(-1, row.advance(300, false));
            assertEquals(100, row.offset(), .001);
        }
    }

    @Test
    void releaseBeyondTheThresholdFillsThenCollapsesOnce() {
        var row = row(SwipeRowState.Direction.LEFT, true, true);
        row.begin();
        row.drag(-192);
        assertTrue(row.armed());
        row.release(0, 10);
        assertEquals(0, row.pendingAction());
        assertEquals(-1, row.advance(149, false));
        assertEquals(1, row.heightFraction(149, false));
        assertEquals(-1, row.advance(250, false));
        assertTrue(row.heightFraction(250, false) < 1);
        assertEquals(0, row.advance(350, false));
        assertTrue(row.collapsed());
        assertEquals(-1, row.advance(380, false));
        row.reset();
        assertFalse(row.collapsed());
        assertEquals(1, row.heightFraction(400, false));
    }

    @Test
    void reversingOrCancelingAnArmedDragDoesNotCommit() {
        var row = row(SwipeRowState.Direction.LEFT, true, true);
        row.setOpen(true, 0);
        row.advance(0, true);
        row.begin();
        row.drag(-160);
        assertTrue(row.armed());
        row.drag(30);
        assertFalse(row.armed());
        row.drag(-160);
        row.cancel(10);
        row.release(-1500, 20);
        assertTrue(row.open());
        assertEquals(-1, row.advance(400, false));
        assertEquals(100, row.offset(), .001);
    }

    @Test
    void flickCannotCommitAndFullSwipeCanBeDisabled() {
        var row = row(SwipeRowState.Direction.RIGHT, true, true);
        row.begin();
        row.drag(14);
        row.release(1500, 10);
        assertTrue(row.open());
        assertEquals(-1, row.pendingAction());

        row = row(SwipeRowState.Direction.RIGHT, false, true);
        row.begin();
        row.drag(1000);
        assertFalse(row.armed());
        row.release(0, 10);
        assertTrue(row.open());
        assertFalse(row.collapsed());
    }

    @Test
    void narrowRowsKeepActionsReachableAndDisableWholeRowCommit() {
        var row = row(SwipeRowState.Direction.RIGHT, true, true);
        row.resize(62);
        row.begin();
        row.drag(1000);
        assertFalse(row.armed());
        row.release(0, 0);
        row.advance(0, true);
        assertEquals(31, row.actionWidth(), .001);
        assertEquals(62, row.offset(), .001);
        row.select(0, false, 20);
        assertEquals(0, row.advance(20, true));
    }

    @Test
    void secondaryActionCanRemainOpenOrCollapseAndReduceMotion() {
        var row = row(SwipeRowState.Direction.LEFT, true, false);
        row.setOpen(true, 0);
        row.select(1, false, 10);
        assertTrue(row.open());
        assertEquals(-1, row.advance(10, true));
        row.select(1, true, 20);
        assertFalse(row.open());
        assertEquals(1, row.advance(20, true));
        assertEquals(-1, row.advance(21, true));
    }
}
