// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.muoniumplayer.core.screens.ncm.effects.EffectsLayout.Box;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnchoredActionMenuTest {
    @Test
    void opensAboveTheAnchorAndStaysWithinTheLeftEdge() {
        var frame = new Box(20, 10, 460, 280);
        var anchor = new Box(78, 193, 20, 20);
        assertEquals(new Box(20, 42, 178, 146), AnchoredActionMenu.position(frame, anchor, 5));
    }

    @Test
    void opensBelowWhenTheTopEdgeBlocksTheMenu() {
        var frame = new Box(20, 10, 460, 280);
        var anchor = new Box(270, 40, 20, 20);
        assertEquals(new Box(112, 65, 178, 94), AnchoredActionMenu.position(frame, anchor, 3));
    }

    @Test
    void fitsWithinANarrowAndShortFrame() {
        var frame = new Box(20, 10, 150, 110);
        var anchor = new Box(130, 60, 20, 20);
        assertEquals(frame, AnchoredActionMenu.position(frame, anchor, 5));
    }
}
