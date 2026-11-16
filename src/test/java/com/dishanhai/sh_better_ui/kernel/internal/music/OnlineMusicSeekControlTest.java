// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLayout;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Construct the real native widget: a contained rectangle alone does not satisfy its minimum size. */
class OnlineMusicSeekControlTest {
    @Test void expandedPlayerConstructsItsRealSeekWidget() {
        for (int width : new int[]{360, 519, 520, 640, 960})
            for (int height : new int[]{340, 360, 540, 720}) verify(width, height, true);
    }
    @Test void compactPlayerRetainsItsValidSeekWidget() {
        for (int width : new int[]{208, 260, 359, 520, 960})
            for (int height : new int[]{240, 264, 319, 339}) verify(width, height, false);
        verify(359, 340, false);
        verify(359, 720, false);
    }
    private static void verify(int width, int height, boolean expanded) {
        var page = OnlineMusicLayout.of(new Bounds(19, 31, width, height));
        assertEquals(expanded, page.expanded());
        var area = page.seekBar(); var transport = page.transport();
        assertEquals(16, area.height());
        var slider = assertDoesNotThrow(() -> new UiSlider(area, 0, 1000, 1, 0, UiPreferences.DEFAULT,
                value -> Component.literal(Integer.toString(value)), ignored -> {}), width + "x" + height);
        assertTrue(area.x() >= transport.x() && area.y() >= transport.y());
        assertTrue(area.right() <= transport.right() && area.bottom() <= transport.bottom());
        slider.setValue(1000); assertEquals(1000, slider.value());
    }
}
