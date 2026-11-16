// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

final class GalleryRichTextSample extends GallerySampleScreen {
    private boolean alternate;
    private List<FormattedCharSequence> lines = List.of();
    private int top;

    GalleryRichTextSample(DeveloperGalleryScreen parent, String id) { super(parent, id); }
    @Override protected int minimumBodyHeight() { return RichTextDemo.HEIGHT; }

    @Override
    protected void initSample() {
        top = body.y() + (body.height() - RichTextDemo.HEIGHT) / 2;
        var button = addButton(new Bounds(body.x() + 8, top, Math.min(body.width() - 16, 220), 22),
                RichTextDemo.button(alternate), pressed -> {
                    alternate = !alternate;
                    pressed.setMessage(RichTextDemo.button(alternate));
                }, false);
        button.setTooltip(Tooltip.create(RichTextDemo.text("tooltip")));
        initialFocus = button;
        lines = RichTextDemo.lines(font, Math.max(1, body.width() - 16));
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int y = top + 30;
        for (var line : lines) {
            if (y + font.lineHeight > Math.min(body.bottom(), top + RichTextDemo.HEIGHT)) break;
            graphics.text(font, line, body.x() + 8, y, theme.text(), false);
            y += font.lineHeight + 4;
        }
    }
}
