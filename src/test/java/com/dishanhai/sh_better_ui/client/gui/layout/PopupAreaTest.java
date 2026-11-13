// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.layout;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PopupAreaTest {
    @Test void avoidsContainerAndAnchorAcrossResizes() {
        var host = new PopupArea.Rect(30, 30, 220, 170);
        var below = PopupArea.leftOf(440, 270, 300, host, 4);
        assertTrue(below.y() >= host.y() + host.height());
        assertTrue(below.x() + below.width() <= 300);
        var beside = PopupArea.leftOf(440, 220, 350, host, 4);
        assertTrue(beside.x() >= host.x() + host.width());
        assertTrue(beside.x() + beside.width() <= 350);
        var narrow = PopupArea.leftOf(100, 80, 3, host, 4);
        assertEquals(0, narrow.width());
    }
}
