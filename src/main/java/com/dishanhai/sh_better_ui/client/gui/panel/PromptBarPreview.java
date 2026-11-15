// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPromptBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import net.minecraft.client.gui.Font;

final class PromptBarPreview implements PreviewScene {
    private final PromptBarDemo demo = new PromptBarDemo();
    private Bounds area;
    private Font font;
    private UiPromptBar prompt;

    @Override
    public void layout(PreviewContext host) {
        if (host.viewport().width() < UiPromptBar.MIN_WIDTH || host.viewport().height() < PromptBarDemo.HEIGHT) return;
        font = host.font();
        area = PromptBarDemo.bounds(host.viewport());
        prompt = host.add(demo.mount(font, area, host.preferences()));
    }

    @Override
    public void render(UiFrame frame) {
        if (area == null) return;
        var status = font.plainSubstrByWidth(demo.status().getString(), area.width());
        frame.graphics().drawString(font, status, area.x(), area.bottom() + 4, UiTheme.current().text() | 0xFF000000, false);
    }

    @Override
    public void suspend() {
        if (prompt != null) prompt.cancelInteraction();
        prompt = null;
        area = null;
        font = null;
    }
}
