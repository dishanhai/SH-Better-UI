package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalMusicIslandNoticeTest {
    @Test void noticeCanRevealHiddenAmbientBeforeAnyRender() {
        var island = ShBetterUiApi.music().island();
        island.setScene(true, false, false, true);
        assertFalse(island.visible(0));
        island.notify(new IslandNoticeQueue.Notice("a", "First", "", -1, 1000));
        island.notify(new IslandNoticeQueue.Notice("b", "Second", "", -1, 1000));
        assertTrue(island.visible(10));
        assertTrue(island.notificationActive(10));
        assertTrue(island.visible(10));
        assertEquals("First\n", island.narration().getString());
        assertTrue(island.visible(1010));
        assertTrue(island.notificationActive(1010));
        assertEquals("First\n", island.narration().getString());
        island.cancelNotice("a");
        assertTrue(island.visible(1010));
        assertEquals("Second\n", island.narration().getString());
        island.cancelNotice("b");
        assertFalse(island.visible(2010));
        assertFalse(island.notificationActive(2010));
    }
}
