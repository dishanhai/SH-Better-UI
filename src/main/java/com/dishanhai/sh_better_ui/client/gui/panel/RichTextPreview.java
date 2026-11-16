// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

final class RichTextPreview implements PreviewScene {
    private boolean alternate;
    private Font font;
    private List<FormattedCharSequence> lines = List.of();
    private int left, top;

    @Override
    public void layout(PreviewContext host) {
        var area = host.viewport();
        font = host.font();
        if (area.width() < 68 || area.height() < RichTextDemo.HEIGHT) { lines = List.of(); return; }
        int width = Math.max(1, area.width() - 16);
        left = area.x() + 8;
        top = area.y() + (area.height() - RichTextDemo.HEIGHT) / 2;
        int buttonWidth = Math.min(width, 220);
        var button = new UiButton(left, top, buttonWidth, 22, RichTextDemo.button(alternate), pressed -> {
            alternate = !alternate;
            pressed.setMessage(RichTextDemo.button(alternate));
        }, host.preferences(), false, UiStyle.DEFAULT);
        button.setTooltip(Tooltip.create(RichTextDemo.text("tooltip")));
        button.active = host.interactive();
        host.add(button);
        lines = RichTextDemo.lines(font, width);
    }

    @Override
    public void render(UiFrame frame) {
        int y = top + 30;
        for (var line : lines) {
            if (y + font.lineHeight > Math.min(frame.viewport().bottom(), top + RichTextDemo.HEIGHT)) break;
            frame.graphics().drawString(font, line, left, y, LiquidGlassTheme.textColor(), false);
            y += font.lineHeight + 4;
        }
    }
}
