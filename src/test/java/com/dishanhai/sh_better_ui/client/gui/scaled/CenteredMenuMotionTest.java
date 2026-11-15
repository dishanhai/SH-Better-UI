// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import org.junit.jupiter.api.Test;

import static com.dishanhai.sh_better_ui.client.gui.scaled.UiExpansionController.State.*;
import static org.junit.jupiter.api.Assertions.*;

class CenteredMenuMotionTest {
    private static final Bounds SOURCE = new Bounds(10, 10, 40, 22);
    private static final Bounds TARGET = new Bounds(65, 48, 141, 61);
    private static final UiExpansionController.Config CONFIG = UiExpansionController.Config.CENTERED_MENU;

    @Test
    void centeredMenuExpandsAboutTheTargetCenterAndRetracesOnCancel() {
        var expansion = new UiExpansionController(SOURCE, TARGET, CONFIG, false, 0);
        var seed = expansion.outline();
        assertEquals(12f, seed.getW());
        assertEquals(12f, seed.getH());
        assertEquals(6f, expansion.radius());
        for (long now : new long[]{0, 40, 100, 180, 240}) {
            expansion.advance(now);
            var rect = expansion.outline();
            assertEquals(135.5f, rect.getX() + rect.getW() * 0.5f, 0.001f);
            assertEquals(78.5f, rect.getY() + rect.getH() * 0.5f, 0.001f);
        }
        assertEquals(TARGET, expansion.frame());
        assertEquals(ACTIVE, expansion.state());
        assertEquals(10f, expansion.radius());
        assertTrue(expansion.cancel(240));
        assertEquals(TARGET, expansion.advance(240));
        assertTrue(expansion.advance(275).width() < TARGET.width());
        expansion.advance(380);
        assertEquals(seed, expansion.outline());
        assertEquals(IDLE, expansion.state());
    }

    @Test
    void reducedMotionSnapsBothDirections() {
        var expansion = new UiExpansionController(SOURCE, TARGET, CONFIG, true, 0);
        assertEquals(TARGET, expansion.advance(0));
        assertEquals(ACTIVE, expansion.state());
        assertTrue(expansion.cancel(10));
        expansion.advance(10);
        assertEquals(IDLE, expansion.state());
    }

    @Test
    void glassBloomAndBlurPeakDuringTheTransition() {
        var defaults = UiPreferences.DEFAULT;
        assertEquals(0f, UiExpansionController.menuBloom(0f, defaults));
        float peak = UiExpansionController.menuBloom(0.5f, defaults);
        assertEquals(1f, peak);
        float settled = UiExpansionController.menuBloom(1f, defaults);
        assertEquals(0f, settled);
        assertTrue(UiExpansionController.menuBlurRadius(peak) > UiExpansionController.menuBlurRadius(settled));
        assertEquals(0f, UiExpansionController.menuBloom(0.5f, new UiPreferences(true, false, true)));
        assertEquals(0f, UiExpansionController.menuBloom(0.5f, new UiPreferences(false, true, true)));
    }
}
