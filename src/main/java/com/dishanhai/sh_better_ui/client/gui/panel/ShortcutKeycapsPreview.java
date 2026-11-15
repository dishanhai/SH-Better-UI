// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiHudPanel;
import net.minecraft.client.gui.Font;

final class ShortcutKeycapsPreview implements PreviewScene {
    private Font font;
    private UiHudPanel panel;

    @Override
    public void layout(PreviewContext host) {
        font = host.font();
        panel = new UiHudPanel(font, ShortcutKeycapsDemo.rows().stream()
                .map(text -> new UiHudPanel.Row(GraphicIcon.KEYBOARD, 0xFFDBE4EF, text)).toList());
    }

    @Override
    public void render(UiFrame frame) {
        var area = frame.viewport();
        var placement = panel.fit(area.width(), area.height());
        panel.draw(frame.graphics(), font, frame.preferences(), area.x() + (area.width() - placement.width()) / 2,
                area.y() + (area.height() - placement.height()) / 2, placement.scale());
    }
}
