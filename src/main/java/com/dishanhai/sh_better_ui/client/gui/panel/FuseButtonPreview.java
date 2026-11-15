// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import net.minecraft.client.gui.Font;

final class FuseButtonPreview implements PreviewScene {
    private final FuseButtonDemo demo = new FuseButtonDemo();
    private Font font;

    @Override
    public void layout(PreviewContext host) {
        font = host.font();
        demo.layout(host.viewport(), host.preferences(), host::add);
    }

    @Override
    public void render(UiFrame frame) {
        var area = frame.viewport();
        if (area.width() < 68 || area.height() < FuseButtonDemo.HEIGHT) return;
        demo.tick();
        GalleryPreviewLayout.drawCentered(frame.graphics(), font, demo.status(), area.x() + 4,
                demo.statusY(area), area.width() - 8);
    }

    @Override public void suspend() { demo.close(); }
}
