// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ListEntranceTest {
    @Test void rowsEnterWithAStaggerAndReducedMotionSkipsIt() {
        var entrance = new ListEntrance();
        assertEquals(0, entrance.offset(0, 1000, false));
        entrance.start(1000);
        assertEquals(5, entrance.offset(0, 1000, false));
        assertTrue(entrance.offset(0, 1080, false) < entrance.offset(4, 1080, false));
        assertEquals(0, entrance.offset(4, 1080, true));
        assertEquals(0, entrance.offset(50, 1420, false));
    }
}
