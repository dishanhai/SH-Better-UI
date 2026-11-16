// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.input;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GestureFocusRetentionTest {
    @Test
    void retainsFocusDuringPickupAndRestoresOnceAfterRelease() {
        var retention = new GestureFocusRetention();
        var screen = new Object();
        var field = new Object();
        assertFalse(retention.restore(screen, field, true, true));
        retention.begin(screen, field);
        assertTrue(retention.restore(screen, field, true, true), "pickup must not close the search");
        assertTrue(retention.restore(screen, field, true, true), "holding must retain the same search");
        assertTrue(retention.restore(screen, field, true, false), "native release may clear focus again");
        assertFalse(retention.restore(screen, field, true, false), "later normal focus changes remain available");
    }

    @Test
    void clearsOnChangedScreenFieldHiddenTargetAndCancellation() {
        var retention = new GestureFocusRetention();
        var screen = new Object();
        var field = new Object();
        retention.begin(screen, field);
        assertFalse(retention.restore(new Object(), field, true, true));
        assertFalse(retention.restore(screen, field, true, true));
        retention.begin(screen, field);
        assertFalse(retention.restore(screen, new Object(), true, true));
        assertFalse(retention.restore(screen, field, true, true));
        retention.begin(screen, field);
        assertFalse(retention.restore(screen, field, false, true));
        assertFalse(retention.restore(screen, field, true, true));
        retention.begin(screen, field);
        retention.clear();
        assertFalse(retention.restore(screen, field, true, true));
    }

    @Test
    void aNewGestureReplacesTheOldFocusAndQuickClicksStillRestore() {
        var retention = new GestureFocusRetention();
        var screen = new Object();
        var field = new Object();
        retention.begin(new Object(), new Object());
        retention.begin(screen, field);
        assertTrue(retention.restore(screen, field, true, false));
        assertFalse(retention.restore(screen, field, true, false));
    }
}
