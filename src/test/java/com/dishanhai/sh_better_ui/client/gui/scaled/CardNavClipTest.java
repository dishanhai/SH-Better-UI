// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CardNavClipTest {
    @Test void clipsFollowTheNavigationAndAnimatedCard() {
        var pose = new Matrix4f().translate(250, 40, 0).translate(8, 30, 0).scale(.5f, .5f, 1);
        assertArrayEquals(new int[]{259, 72, 269, 77}, UiCardNav.clipBounds(pose, 2, 4, 22, 14));
    }
}
