// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiHudPanel;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class GalleryShortcutKeycapsSample extends GallerySampleScreen {
    private UiHudPanel panel;

    GalleryShortcutKeycapsSample(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected void initSample() {
        panel = new UiHudPanel(font, ShortcutKeycapsDemo.rows().stream()
                .map(text -> new UiHudPanel.Row(UiHudPanel.icon("keyboard"), 0xFFDBE4EF, text)).toList());
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var placement = panel.fit(body.width(), body.height());
        panel.draw(graphics, font, preferences, body.x() + (body.width() - placement.width()) / 2,
                body.y() + (body.height() - placement.height()) / 2, placement.scale());
    }
}
