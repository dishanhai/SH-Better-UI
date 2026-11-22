// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class NativeHudPanelSizingTest {
    @Test void enlargedIconsDoNotMoveTheTextOrChangePanelPlacement() {
        var font = CardNavTestFont.create();
        var row = new UiHudPanel.Row(GraphicIcon.INFO, 0xffabcdef, Component.literal("HUD"));
        assertEquals(0xffabcdef, row.iconColor());
        var compact = new UiHudPanel(font, List.of(row), 100);
        var enlarged = new UiHudPanel(font, List.of(row), 100, 10);
        assertEquals(compact.fit(300, 180), enlarged.fit(300, 180));
        assertEquals(compact.fit(90, 40, 0.75f), enlarged.fit(90, 40, 0.75f));
        assertTrue(enlarged.fit(300, 180).width() > new UiHudPanel(font, List.of(row)).fit(300, 180).width());
        var fit = enlarged.fit(300, 180);
        assertEquals(0.5, fit.moveX(fit.x(0.5), 0), 1d / fit.travelX());
        assertEquals(0.5, fit.moveY(fit.y(0.5), 0), 1d / fit.travelY());
    }
}
