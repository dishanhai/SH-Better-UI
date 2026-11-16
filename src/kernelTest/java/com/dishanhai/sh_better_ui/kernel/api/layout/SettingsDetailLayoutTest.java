// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SettingsDetailLayoutTest {
    @Test void cardsHaveStableRowsAndOneGap() {
        var layout = SettingsDetailLayout.of(500, List.of(3, 2));
        assertEquals(132, layout.cards().get(0).height());
        assertEquals(144, layout.cards().get(1).top());
        assertEquals(244, layout.contentHeight());
        assertEquals(26, layout.cards().get(0).fieldTop(0));
        assertEquals(90, layout.cards().get(0).fieldTop(2));
        assertThrows(IndexOutOfBoundsException.class, () -> layout.cards().get(0).fieldTop(3));
    }
    @Test void narrowLayoutsStackWithoutNegativeWidths() {
        for (int width : new int[]{0, 12, 20, 120, 299, 300, 560}) {
            var layout = SettingsDetailLayout.of(width, List.of(1));
            assertTrue(layout.controlWidth() >= 0);
            if (width >= 20) assertTrue(layout.controlX() + layout.controlWidth() <= width);
            assertEquals(width < 300, layout.stacked());
        }
    }
    @Test void emptyAndInvalidInput() {
        assertEquals(0, SettingsDetailLayout.of(300, List.of()).contentHeight());
        assertThrows(IllegalArgumentException.class, () -> SettingsDetailLayout.of(-1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> SettingsDetailLayout.of(300, List.of(-1)));
        assertThrows(ArithmeticException.class, () -> SettingsDetailLayout.of(300, List.of(Integer.MAX_VALUE)));
        assertThrows(UnsupportedOperationException.class,
                () -> SettingsDetailLayout.of(300, List.of(1)).cards().clear());
    }
    @Test void scrollClampsAndPreservesOnResize() {
        var layout = SettingsDetailLayout.of(500, List.of(15));
        var scroll = new VerticalScroll();
        scroll.resize(120, layout.contentHeight());
        scroll.scrollTo(100);
        scroll.resize(160, layout.contentHeight());
        assertEquals(100, scroll.offset());
        scroll.scrollTo(Double.MAX_VALUE);
        assertEquals(layout.contentHeight() - 160, scroll.offset());
        scroll.resize(1000, layout.contentHeight());
        assertEquals(0, scroll.offset());
    }
}
