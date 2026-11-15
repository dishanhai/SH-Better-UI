// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TooltipBoundsTest {
    @Test
    void selectedTooltipStaysLeftOfTheIngredientGrid() {
        var position = TooltipBounds.leftOf(300).positionTooltip(440, 270, 302, 200, 110, 60);
        assertTrue(position.x() >= 0);
        assertTrue(position.x() + 110 + 4 <= 300);
        assertTrue(position.y() + 60 + 4 <= 270);
    }

    @Test
    void narrowWindowsAndLargeTooltipsUseTheSameBoundary() {
        for (int right : new int[]{80, 160, 300}) {
            int width = right - 32;
            var position = TooltipBounds.leftOf(right).positionTooltip(440, 270, right + 2, 250, width, 90);
            assertTrue(position.x() >= 0);
            assertTrue(position.x() + width + 4 <= right);
        }
    }

    @Test
    void viewportCannotExtendPastTheScreen() {
        assertEquals(200, TooltipBounds.rightEdge(200, 300));
        assertEquals(120, TooltipBounds.rightEdge(200, 120));
        assertEquals(1, TooltipBounds.rightEdge(200, -1));
    }

    @Test
    void oversizedGraphicsFitWithTheirFrame() {
        assertFits(130, 225, 175, 160, true);
        assertFits(240, 100, 160, 180, true);
        assertFits(240, 225, 160, 160, false);
    }

    private static void assertFits(int right, int screenHeight, int width, int height, boolean oversized) {
        int padding = 12;
        double[] scale = {1};
        var position = TooltipBounds.fitLeftOf(right, padding, value -> scale[0] = value)
                .positionTooltip(440, screenHeight, right, screenHeight - 1, width, height);
        assertEquals(oversized, scale[0] < 1);
        assertTrue((position.x() - padding) * scale[0] >= 0);
        assertTrue((position.x() + width + padding) * scale[0] <= right);
        assertTrue((position.y() - padding) * scale[0] >= 0);
        assertTrue((position.y() + height + padding) * scale[0] <= screenHeight);
    }
}
