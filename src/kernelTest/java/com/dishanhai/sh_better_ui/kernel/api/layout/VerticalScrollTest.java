// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerticalScrollTest {
    @Test void clampsAndResizesWithoutLosingValidPosition() {
        var scroll = new VerticalScroll();
        scroll.resize(100, 400);
        assertTrue(scroll.scrollBy(130));
        assertEquals(130, scroll.offset());
        assertFalse(scroll.scrollBy(Double.NaN));
        scroll.scrollTo(999);
        assertEquals(300, scroll.offset());
        scroll.resize(100, 180);
        assertEquals(80, scroll.offset());
        scroll.resize(100, 80);
        assertEquals(0, scroll.offset());
        assertThrows(IllegalArgumentException.class, () -> scroll.resize(-1, 1));
    }
    @Test void thumbDragUsesGrabOffsetAndCancellationRestoresStart() {
        var scroll = new VerticalScroll();
        scroll.resize(100, 400);
        scroll.scrollTo(100);
        int top = scroll.thumbTop(100);
        assertTrue(scroll.beginDrag(top + 3, 100));
        assertEquals(100, scroll.offset(), 2);
        assertTrue(scroll.dragTo(90, 100));
        assertTrue(scroll.cancelDrag());
        assertEquals(100, scroll.offset());
        assertFalse(scroll.cancelDrag());
        assertTrue(scroll.beginDrag(99, 100));
        assertEquals(300, scroll.offset());
        assertTrue(scroll.endDrag());
    }
    @Test void splitActionsNeverOverlapIncludingNarrowWidths() {
        for (int width = 0; width < 300; width++) {
            var row = SplitActionRow.of(width, 22, 2);
            assertTrue(row.actionWidth() <= row.disclosureX());
            assertEquals(width, row.disclosureX() + row.disclosureWidth());
        }
    }
}
