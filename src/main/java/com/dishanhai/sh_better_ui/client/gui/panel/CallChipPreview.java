// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCallChip;
import net.minecraft.network.chat.Component;

final class CallChipPreview implements PreviewScene {
    private final CallChipDemo demo = new CallChipDemo();
    private UiCallChip chip;

    @Override
    public void layout(PreviewContext host) {
        suspend();
        var area = host.viewport();
        if (area.width() < 88 || area.height() < 58) return;
        int width = Math.min(212, area.width() - 12);
        int x = area.x() + (area.width() - width) / 2;
        int y = area.y() + (area.height() - 58) / 2;
        chip = host.add(ShBetterUiApi.controls().callChip(new Bounds(x, y, width, 30),
                text("demo_name"), text("demo_argument"), GraphicIcon.CODE, demo.state, host.preferences(), true,
                () -> { if (host.interactive()) demo.retry(); }));
        GalleryPreviewLayout.addButton(host, new Bounds(x, y + 36, width, 22), text("start"), GraphicIcon.CODE, demo::start);
    }

    @Override
    public void render(UiFrame frame) {
        demo.update();
    }

    @Override
    public void suspend() {
        if (chip != null) chip.cancelPress();
        chip = null;
    }

    private static Component text(String key) {
        return Component.translatable("sh_better_ui.call_chip." + key);
    }
}
