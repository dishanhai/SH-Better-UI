// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PopupMotionTest {
    @Test void reducedMotionAndInterruptedReveal() {
        var motion = new PopupMotion();
        var seed = motion.sample(true, 100, 100, 120, 80, 1, false);
        assertEquals(32, seed.width());
        assertEquals(10, seed.height());
        var opening = motion.sample(true, 100, 100, 120, 80, 80, false);
        assertTrue(opening.progress() > 0 && opening.progress() < 1);
        var reverse = motion.sample(false, 100, 100, 120, 80, 80, false);
        assertEquals(opening.progress(), reverse.progress(), 0.001f);
        var complete = motion.sample(true, 80, 90, 140, 90, 100, true);
        assertEquals(1, complete.progress());
        assertEquals(140, complete.width());
        assertEquals(80, complete.x());
    }
}
