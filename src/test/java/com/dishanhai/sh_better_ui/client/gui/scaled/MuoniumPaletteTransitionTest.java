package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MuoniumPaletteTransitionTest {
    private static AmbientBackdropProfile profile(int color, float opacity) {
        return new AmbientBackdropProfile(.2f, 1, .8f, opacity, color, color, color);
    }
    @Test void initialStableAndReducedMotionAreImmediate() {
        var motion = new MuoniumPaletteTransition();
        var a = profile(0x805588cc, .5f); var b = profile(0x80dd8855, .8f);
        assertEquals(a, motion.sample(a, 0, false));
        assertEquals(b, motion.sample(b, 50, true));
        motion.reset(); assertEquals(a, motion.sample(a, 100, false));
    }
    @Test void interruptedTransitionStartsFromVisibleColorAndPreservesAlpha() {
        var motion = new MuoniumPaletteTransition();
        var a = profile(0x805588cc, .5f); var b = profile(0x80dd8855, .8f); var c = profile(0x8022dd55, .9f);
        motion.sample(a, 0, false);
        assertEquals(a.primaryColor(), motion.sample(b, 50, false).primaryColor());
        var mid = motion.sample(b, 100, false);
        assertNotEquals(a.primaryColor(), mid.primaryColor());
        assertNotEquals(b.primaryColor(), mid.primaryColor());
        assertEquals(0x80, mid.primaryColor() >>> 24);
        var interrupted = motion.sample(c, 150, false);
        assertEquals(mid.primaryColor(), interrupted.primaryColor());
        assertEquals(.9f, interrupted.opacity());
        for (int i=0; i<13; i++) motion.sample(c, 200 + 50L*i, false);
        assertEquals(c, motion.sample(c, 900, false));
    }
    @Test void giantClockJumpsAreBoundedAndClockReversalIsStable() {
        var motion = new MuoniumPaletteTransition();
        var a = profile(0xff101010, 1); var b = profile(0xffeeeeee, 1);
        motion.sample(a, 0, false); motion.sample(b, 1, false);
        var first = motion.sample(b, Long.MAX_VALUE, false);
        assertNotEquals(b, first);
        assertEquals(first, motion.sample(b, 0, false));
    }
    @Test void coverProfileOptInAndLegacyConstructorRemainCompatible() {
        assertFalse(MuoniumFluidProfile.DEFAULT.followArtwork());
        var p = new MuoniumFluidProfile(.2f, 1, .5f, 1, 1, 2, 3, true);
        assertFalse(p.followArtwork());
        var worker = new MuoniumCoverPalette();
        assertNull(worker.sample(null));
        assertDoesNotThrow(worker::close);
        assertDoesNotThrow(worker::close);
        assertNull(worker.sample(null));
    }
}
