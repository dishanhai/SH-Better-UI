package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalMusicIslandNoticeTest {
    @Test void emptyClassicWithoutAmbientAlsoReleasesItsSurface() {
        var island = ShBetterUiApi.music().island();
        island.setStyle("classic");
        island.setScene(true, false, false, false);
        assertFalse(island.visible(0));
        island.setAmbient(IslandAmbientState.Settings.DEFAULT);
        island.updateAmbient(60, 20, 12, 0, 1);
        assertTrue(island.visible(1));
        island.setAmbient(IslandAmbientState.Settings.DISABLED);
        assertFalse(island.visible(2));
        island.suspend();
    }

    @Test void capsuleWithoutMediaDoesNotInventPlaybackButNoticesAndEditorStillWork() {
        var island = ShBetterUiApi.music().island();
        island.setStyle("music_capsule");
        island.setScene(true, false, false, false);
        assertFalse(island.visible(0));
        island.setExpanded(true, 1, true);
        assertTrue(island.visible(1));
        island.setExpanded(false, 2, true);
        assertFalse(island.visible(2));
        island.notify(new IslandNoticeQueue.Notice("message", "Notice", "Real event", -1, 1000));
        assertTrue(island.visible(3));
        island.setScene(true, false, true, false);
        assertFalse(island.visible(4));
        island.suspend();
    }

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
