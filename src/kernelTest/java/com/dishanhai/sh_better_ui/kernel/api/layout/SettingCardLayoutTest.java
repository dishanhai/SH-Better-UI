package com.dishanhai.sh_better_ui.kernel.api.layout;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class SettingCardLayoutTest {
    @Test void wideAndNarrowNeverOverlapPrimaryAndArrow() {
        for (int width : new int[]{180, 239, 359, 360, 440, 1000}) {
            var l = SettingCardLayout.measure(width, 49, 20, List.of(24, 36));
            assertTrue(l.primary().x() + l.primary().width() < l.disclosure().x());
            assertTrue(l.disclosure().x() + l.disclosure().width() <= width - 10);
            assertFalse(l.primary().contains(l.disclosure().x(), l.disclosure().y()));
            assertTrue(l.details().get(0).y() > l.primary().y() + l.primary().height());
            assertEquals(width - 20, l.details().get(0).width());
            assertEquals(l.details().get(1).y() + 36 + 10, l.height());
            if (width < 360) assertTrue(l.primary().y() >= l.text().y() + l.text().height());
            else assertTrue(l.text().x() + l.text().width() < l.primary().x());
        }
    }
    @Test void rejectsInvalidDimensionsAndImmutableDetails() {
        assertThrows(IllegalArgumentException.class, () -> SettingCardLayout.measure(179, 20, 20, List.of()));
        assertThrows(IllegalArgumentException.class, () -> SettingCardLayout.measure(180, 20, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> SettingCardLayout.measure(180, 20, 20, List.of(-1)));
        var l = SettingCardLayout.measure(400, 20, 24, List.of(20));
        assertThrows(UnsupportedOperationException.class, () -> l.details().clear());
    }
}
