// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiHudPanelWidthTest {
    private static final Font FONT = new Font(ignored -> null, false) {
        @Override
        public int width(FormattedText text) {
            return text.getString().length() * 6;
        }
    };

    @Test
    void reservedWidthKeepsPlacementStableWhenTheLabelChanges() {
        var shortLabel = row("543 / 81 / -199 南");
        var longLabel = row("543 / 81 / -199 西南");
        int reservation = FONT.width(longLabel.text());
        var shortPanel = new UiHudPanel(FONT, List.of(shortLabel), reservation);
        var longPanel = new UiHudPanel(FONT, List.of(longLabel), reservation);

        assertEquals(shortPanel.fit(320, 180), longPanel.fit(320, 180));
        assertEquals(shortPanel.fit(90, 180), longPanel.fit(90, 180));
        assertTrue(shortPanel.fit(320, 180).width() > new UiHudPanel(FONT, List.of(shortLabel)).fit(320, 180).width());
    }

    @Test
    void largerIconsLeaveTheTextAndPanelGeometryUnchanged() {
        var rows = List.of(row("玩家"), row("服务器"));
        var standard = new UiHudPanel(FONT, rows, 100);
        var enlarged = new UiHudPanel(FONT, rows, 100, 10);

        assertEquals(standard.fit(320, 180, 0.75f), enlarged.fit(320, 180, 0.75f));
        assertEquals(standard.fit(90, 40), enlarged.fit(90, 40));
    }

    private static UiHudPanel.Row row(String text) {
        return new UiHudPanel.Row(GraphicIcon.PIN, 0xffffffff, Component.literal(text));
    }
}
