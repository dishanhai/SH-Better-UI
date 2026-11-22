// Author: dishanhai
package com.muoniumplayer.core.screens.ncm;

import com.muoniumplayer.compat.lwjgl2.Keyboard;
import com.muoniumplayer.core.rendering.ui.container.Panel;
import com.muoniumplayer.core.rendering.ui.container.ScrollPanel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MusicEndKeyboardFocusTest {
    @Test
    void endOnlyMovesAndLoadsTheFocusedList() {
        var page = new NCMPanel() {
            @Override public void onInit() {}
        };
        var firstLoads = new AtomicInteger();
        var secondLoads = new AtomicInteger();
        var first = list(0, firstLoads);
        var second = list(120, secondLoads);
        page.addChild(first, second);

        assertFalse(page.onKeyTypedReceived('\0', Keyboard.KEY_END));
        assertFalse(page.handleFocusedListEnd());

        assertTrue(page.focusNextEndList(true));
        assertTrue(page.handleFocusedListEnd());
        assertEquals(60.0, second.actualScrollOffset);
        assertEquals(0.0, first.actualScrollOffset);
        assertEquals(0, secondLoads.get());
        assertTrue(page.handleFocusedListEnd());
        assertEquals(1, secondLoads.get());

        page.focusEndListAt(10, 10);
        assertTrue(page.handleFocusedListEnd());
        assertTrue(page.handleFocusedListEnd());
        assertEquals(1, firstLoads.get());
        assertEquals(1, secondLoads.get());

        first.setHidden(true);
        assertFalse(page.handleFocusedListEnd());
        page.clearEndKeyboardFocus();
        assertFalse(page.handleFocusedListEnd());
    }

    private static ScrollPanel list(double x, AtomicInteger loads) {
        var list = new ScrollPanel();
        list.setPosition(x, 0).setWidth(100).setHeight(40);
        var row = new Panel();
        row.setHeight(100);
        list.addChild(row);
        list.setEndPull(() -> true, () -> ScrollPanel.EndStatus.MORE, loads::incrementAndGet);
        return list;
    }
}
