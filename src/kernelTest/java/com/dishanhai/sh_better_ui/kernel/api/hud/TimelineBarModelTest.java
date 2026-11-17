// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TimelineBarModelTest {
    @Test void clampsInvalidAndOutOfRangeFractions() {
        assertEquals(0, TimelineBarModel.fillWidth(100, Double.NaN));
        assertEquals(0, TimelineBarModel.fillWidth(100, Double.POSITIVE_INFINITY));
        assertEquals(0, TimelineBarModel.fillWidth(100, -1));
        assertEquals(100, TimelineBarModel.fillWidth(100, 2));
        assertEquals(0, TimelineBarModel.fillWidth(-1, 0.5));
    }
    @Test void reversedFillKeepsItsRightEdge() {
        assertEquals(60, TimelineBarModel.fillStart(10, 100, 0.5, true));
        assertEquals(10, TimelineBarModel.fillStart(10, 100, 0.5, false));
        assertEquals(110, TimelineBarModel.fillStart(10, 100, 0, true));
    }
}
