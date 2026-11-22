// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.muoniumplayer.core.screens.ncm.effects.EffectsLayout.Box;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMenuMotionTest {
    @Test
    void reversesFromTheCurrentFrame() {
        var motion = new PlayerMenuMotion();
        assertEquals(0f, motion.sample(true, 1000, false));
        float opening = motion.sample(true, 1100, false);
        assertTrue(opening > 0 && opening < 1);
        assertEquals(opening, motion.sample(false, 1100, false), .001f);
        float closing = motion.sample(false, 1150, false);
        assertTrue(closing > 0 && closing < opening);
        assertEquals(closing, motion.sample(true, 1150, false), .001f);
        assertEquals(1f, motion.sample(true, 1400, false));
    }

    @Test
    void reducedMotionSnapsMenuAndRowStates() {
        var motion = new PlayerMenuMotion();
        var row = new PlayerMenuMotion.Row();
        assertEquals(1f, motion.sample(true, 1000, true));
        assertEquals(PlayerMenuStyle.rowOpacity(true, true), row.sample(true, true, 1000, true));
        assertEquals(PlayerMenuStyle.rowOpacity(false, false), row.sample(false, false, 1010, true));
        assertEquals(0f, motion.sample(false, 1010, true));
    }

    @Test
    void movesFromTheAnchorAndStaggersRows() {
        var target = new Box(12, 24, 178, 146);
        var row = new Box(22, 34, 158, 22);
        var intermediate = PlayerMenuMotion.frame(target, 184, 178, .5f);
        assertTrue(intermediate.y() > target.y());
        assertTrue(intermediate.width() < target.width());
        assertTrue(PlayerMenuMotion.mapRow(row, target, intermediate)
                .contains(intermediate.x() + intermediate.width() / 2, intermediate.y() + 15));
        assertEquals(target, PlayerMenuMotion.frame(target, 184, 178, 1));
        assertTrue(PlayerMenuMotion.rowReveal(.6f, 0) > PlayerMenuMotion.rowReveal(.6f, 4));
        assertEquals(1f, PlayerMenuMotion.rowReveal(1f, 4));
    }
}
