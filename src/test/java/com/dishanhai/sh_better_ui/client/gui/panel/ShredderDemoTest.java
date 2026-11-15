// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShredderDemoTest {
    private final ShredderDemo demo = new ShredderDemo();

    ShredderDemoTest() {
        demo.layout(new Bounds(0, 0, 200, 140));
    }

    @Test
    void developerGalleryRegistersItAsAControl() {
        var spec = ShBetterUiApi.developerPreviews().find("shredder").orElseThrow();
        assertEquals(PreviewSpec.Category.CONTROLS, spec.category());
        assertEquals(ShredderDemo.TITLE, spec.titleKey());
    }

    @Test
    void dragReordersOnlyOnReleaseAndUndoRestoresOriginalOrder() {
        assertTrue(demo.press(20, 10));
        demo.drag(20, 58);
        assertEquals("drop", status());
        assertEquals(1, selection()[1]);
        assertTrue(demo.release(20, 58, 0, true));
        assertEquals(3, selection()[1]);
        assertTrue(demo.undo());
        assertEquals(1, selection()[1]);
    }

    @Test
    void escapeAndReleaseOutsideTheMachineKeepTheCard() {
        assertTrue(demo.press(20, 58));
        demo.drag(20, 84);
        assertEquals("armed", status());
        demo.cancelDrag();
        assertFalse(demo.release(20, 84, 0, true));
        assertTrue(demo.press(20, 58));
        assertFalse(demo.release(210, 84, 0, true));
        assertEquals(3, selection()[2]);
        assertFalse(demo.canUndo());
    }

    @Test
    void feedConsumesAtTheSlitAndUndoCanRestoreDuringTheFall() {
        assertTrue(demo.press(20, 58));
        assertTrue(demo.release(20, 84, 10, false));
        assertEquals("feeding", status());
        assertFalse(demo.advance(40, false));
        assertEquals(3, selection()[2]);
        assertTrue(demo.advance(250, false));
        assertEquals("shredded", status());
        assertEquals(2, selection()[2]);
        assertTrue(demo.undo());
        assertEquals(3, selection()[2]);
        assertFalse(demo.advance(900, false));
    }

    @Test
    void reducedMotionCommitsImmediatelyAndRelayoutCancelsUnfinishedFeed() {
        assertTrue(demo.shred(0, false));
        demo.layout(new Bounds(6, 4, 170, 110));
        assertEquals(3, selection()[2]);
        assertFalse(demo.advance(1_000, false));
        assertTrue(demo.shred(1_000, true));
        assertEquals("shredded", status());
        assertEquals(2, selection()[2]);
        assertTrue(demo.move(1));
        assertEquals(2, selection()[1]);
        assertTrue(demo.undo());
        assertEquals(1, selection()[1]);
    }

    private String status() {
        var content = (TranslatableContents) demo.status().getContents();
        return content.getKey().substring(content.getKey().lastIndexOf('.') + 1);
    }

    private Object[] selection() {
        return ((TranslatableContents) demo.narration().getContents()).getArgs();
    }
}
