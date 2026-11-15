// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiBellToggle;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

final class GalleryBellToggleSample extends GallerySampleScreen {
    private boolean enabled;
    private int count = 2;

    GalleryBellToggleSample(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected void initSample() {
        if (body.width() < 72 || body.height() < 60) return;
        int width = Math.min(176, body.width());
        int x = body.x() + (body.width() - width) / 2;
        int y = body.y() + (body.height() - 60) / 2;
        var bell = new UiBellToggle(x, y, width, 26, text("off"), text("on"), () -> preferences,
                () -> enabled, () -> count, value -> {
                    enabled = value;
                    return true;
                });
        bell.setX(body.x() + (body.width() - bell.getWidth()) / 2);
        bell.setTooltip(Tooltip.create(text("hint")));
        initialFocus = addRenderableWidget(bell);
        int half = (width - 4) / 2;
        addButton(new Bounds(x, y + 34, half, 20), text("add"),
                ignored -> count = Math.min(99, count + 1), false);
        addButton(new Bounds(x + half + 4, y + 34, width - half - 4, 20), text("clear"),
                ignored -> count = 0, false);
    }

    private static Component text(String key) {
        return Component.translatable("sh_better_ui.bell_toggle." + key);
    }
}
