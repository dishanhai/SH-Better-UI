// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FolderFloatMotionTest {
    @Test
    void reversesAnInFlightOpeningWithoutJumping() {
        var motion = new FolderFloatMotion(180, 72, new int[]{70, 70, 70}, false);
        motion.advance(0, true, 0, false);
        motion.setOpen(true);
        motion.advance(48, true, 0, false);
        var before = motion.pill(0);
        assertTrue(before.progress() > 0);
        assertEquals(0f, motion.pill(2).progress());
        motion.setOpen(false);
        assertEquals(before, motion.pill(0));
        advance(motion, 48);
        assertEquals(0f, motion.pill(0).progress());
    }

    @Test
    void clickSelectsDragDoesNotAndCancelRestoresTheOrigin() {
        var motion = new FolderFloatMotion(180, 72, new int[]{70}, true);
        var origin = motion.pill(0);
        double x = origin.x() + 8, y = origin.y() + 8;
        assertTrue(motion.press(x, y, 0));
        assertEquals(0, motion.release(x, y, 10, true));
        assertTrue(motion.press(x, y, 20));
        motion.drag(400, -80, 40, true);
        assertEquals(110f, motion.pill(0).x());
        assertEquals(0f, motion.pill(0).y());
        motion.cancel();
        assertEquals(origin, motion.pill(0));
        assertTrue(motion.press(x, y, 60));
        motion.drag(x + 10, y - 10, 80, true);
        assertEquals(-1, motion.release(x, y, 100, true));
        assertEquals(origin, motion.pill(0));
    }

    @Test
    void draggedPillTransfersMomentumToAnotherPill() {
        var motion = new FolderFloatMotion(220, 80, new int[]{54, 54}, true);
        var left = motion.pill(0);
        var right = motion.pill(1);
        double x = left.x() + 12, y = left.y() + 8;
        motion.advance(0, true, 0, false);
        assertTrue(motion.press(x, y, 0));
        motion.drag(x + 25, y, 32, true);
        motion.advance(32, true, 0, false);
        assertTrue(motion.pill(1).x() > right.x());
        var before = motion.pill(0);
        assertEquals(-1, motion.release(x + 25, y, 32, true));
        motion.advance(48, true, 0, false);
        assertTrue(motion.pill(0).x() > before.x());
        advance(motion, 48);
        assertTrue(motion.pill(1).x() + right.width() <= 220);
    }

    @Test
    void reducedMotionShowsStaticSelectableItemsImmediately() {
        var motion = new FolderFloatMotion(120, 72, new int[]{56, 56, 56}, false);
        motion.setOpen(true);
        motion.advance(0, true, 1, true);
        var last = motion.pill(2);
        assertEquals(1f, last.progress());
        assertEquals(1f, motion.flap());
        motion.advance(500, true, 1, true);
        assertEquals(last, motion.pill(2));
        assertTrue(motion.press(last.x() + 8, last.y() + 8, 500));
        assertEquals(2, motion.release(last.x() + 8, last.y() + 8, 510, false));
        motion.setOpen(false);
        motion.advance(510, true, 1, true);
        assertEquals(0f, motion.pill(2).progress());
    }

    private static void advance(FolderFloatMotion motion, long from) {
        for (long time = from + 16; time <= from + 800; time += 16) {
            motion.advance(time, true, 0, false);
        }
    }
}
