// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.TabScrollState;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

final class NativePageControlsTest {
    @Test
    void listScrollKeepsBoundsAfterLocalFilteringAndResize() {
        var state = new TabScrollState();
        var items = IntStream.range(0, 50).mapToObj(index -> "Row " + index).toList();
        var list = new NativePageList(100, 40, 10, state);
        list.update(items, "");
        assertEquals(460, state.maxOffset());
        assertTrue(list.mouseScrolled(15, 25, -5));
        assertEquals(50, state.offset());
        list.setFocused(true);
        assertTrue(list.keyPressed(GLFW.GLFW_KEY_END, 0, 0));
        assertEquals(460, state.offset());

        var resized = new NativePageList(100, 60, 10, state);
        resized.update(items, "");
        assertEquals(440, state.offset());
        resized.update(items, "ROW 1");
        assertEquals(50, state.maxOffset());
        assertEquals(50, state.offset());
        resized.update(List.of(), "");
        assertEquals(0, state.offset());
        assertFalse(resized.mouseScrolled(15, 25, -1));
    }
}
