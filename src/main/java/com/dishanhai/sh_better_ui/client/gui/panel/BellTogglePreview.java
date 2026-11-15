// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiBellToggle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

final class BellTogglePreview implements PreviewScene {
    private boolean enabled;
    private int count = 2;
    private UiBellToggle bell;
    private PreviewContext context;

    @Override
    public void layout(PreviewContext host) {
        var area = host.viewport();
        if (area.width() < 72 || area.height() < 60) return;
        context = host;
        int width = Math.min(176, area.width());
        int x = area.x() + (area.width() - width) / 2;
        int y = area.y() + (area.height() - 60) / 2;
        bell = new UiBellToggle(x, y, width, 26, text("off"), text("on"), host::preferences,
                () -> enabled, () -> count, value -> {
                    enabled = value;
                    return true;
                }, host::interactive, host.screen());
        bell.setX(area.x() + (area.width() - bell.getWidth()) / 2);
        bell.setTooltip(Tooltip.create(text("hint")));
        host.add(bell);
        int half = (width - 4) / 2;
        GalleryPreviewLayout.addButton(host, new Bounds(x, y + 34, half, 20), text("add"), GraphicIcon.ADD,
                () -> count = Math.min(99, count + 1));
        GalleryPreviewLayout.addButton(host, new Bounds(x + half + 4, y + 34, width - half - 4, 20),
                text("clear"), GraphicIcon.RESET, () -> count = 0);
    }

    @Override
    public void suspend() {
        if (bell != null && bell.cancelPress()) UiPointerCapture.cancel(context.screen());
        bell = null;
        context = null;
    }

    private static Component text(String key) {
        return Component.translatable("sh_better_ui.bell_toggle." + key);
    }
}
