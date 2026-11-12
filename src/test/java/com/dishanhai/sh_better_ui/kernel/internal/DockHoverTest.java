// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DockHoverTest {
    @Test void appliesIndependentOpenAndCloseDelays() {
        var hover = new DockHover();
        assertFalse(step(hover, 0, true, false, false));
        assertFalse(step(hover, 119, true, false, false));
        assertTrue(step(hover, 120, true, false, false));
        assertTrue(step(hover, 150, false, false, true));
        assertTrue(step(hover, 499, false, false, true));
        assertFalse(step(hover, 500, false, false, true));
    }

    @Test void focusOrCaptureRestartsCloseDelayAndReentryCancelsIt() {
        var hover = new DockHover();
        assertTrue(step(hover, 0, true, false, true));
        assertTrue(step(hover, 200, false, true, true));
        assertTrue(step(hover, 950, false, true, true));
        assertTrue(step(hover, 1200, false, false, true));
        assertTrue(step(hover, 1240, true, false, true));
        assertTrue(step(hover, 1500, false, false, true));
        assertFalse(step(hover, 1850, false, false, true));
    }

    @Test void explicitCollapseSuppressesHoverUntilPointerLeaves() {
        var hover = new DockHover();
        assertTrue(step(hover, 0, true, false, true));
        hover.command();
        assertFalse(step(hover, 500, true, false, false));
        assertFalse(step(hover, 520, false, false, false));
        assertFalse(step(hover, 540, true, false, false));
        assertTrue(step(hover, 660, true, false, false));
    }

    @Test void suspendClearsAnExpiredHover() {
        var hover = new DockHover();
        assertFalse(step(hover, 0, true, false, false));
        hover.suspend(500);
        assertFalse(step(hover, 520, true, false, false));
        assertTrue(step(hover, 640, true, false, false));
    }

    private static boolean step(DockHover hover, long millis, boolean inside, boolean protectedInteraction, boolean target) {
        return hover.update(millis, inside, protectedInteraction, target, true, 120, 350);
    }
}
