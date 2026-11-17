package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicIslandStateTest {
    @Test void newTrackExpandsOnceAndExpires() {
        var state = new DynamicIslandState();
        state.observeTrack("a", 100);
        assertFalse(state.expanded(100));
        state.observeTrack("b", 200);
        assertTrue(state.expanded(200));
        state.observeTrack("b", 3000);
        assertFalse(state.expanded(3700));
    }
    @Test void hoverGraceAndPinAreIndependent() {
        var state = new DynamicIslandState();
        state.setHovered(true, 100);
        assertTrue(state.expanded(100));
        state.setHovered(false, 200);
        assertTrue(state.expanded(419));
        assertFalse(state.expanded(420));
        state.setPinned(true);
        assertTrue(state.expanded(500));
        state.dismiss();
        assertFalse(state.expanded(500));
    }
    @Test void sceneGatesNotificationsAndAvoidsDuplicateAmbient() {
        var state = new DynamicIslandState();
        state.setScene(true, false, false, true);
        assertFalse(state.visible(0));
        state.observeTrack("a", 0);
        state.observeTrack("b", 10);
        assertTrue(state.visible(10));
        state.setScene(true, true, false, false);
        assertFalse(state.visible(20));
        assertFalse(state.expanded(20));
        state.setScene(true, false, true, false);
        assertFalse(state.visible(30));
        state.setScene(false, false, false, false);
        assertFalse(state.visible(40));
    }
    @Test void missingMediaDismissAndSuspendDoNotResurrect() {
        var state = new DynamicIslandState();
        state.observeTrack("a", 0);
        state.observeTrack("b", 10);
        state.observeTrack(null, 20);
        assertFalse(state.expanded(20));
        state.observeTrack("c", 30);
        state.dismiss();
        assertFalse(state.expanded(30));
        state.setPinned(true);
        state.suspend();
        state.observeTrack("d", 40);
        assertFalse(state.expanded(40));
    }
    @Test void backwardsClockClearsTransientState() {
        var state = new DynamicIslandState();
        state.observeTrack("a", 100);
        state.observeTrack("b", 200);
        assertTrue(state.notificationActive(200));
        assertFalse(state.notificationActive(50));
    }
}
