// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import net.minecraft.client.gui.GuiGraphicsExtractor;

final class GalleryFuseButtonSample extends GallerySampleScreen {
    private final FuseButtonDemo demo = new FuseButtonDemo();

    GalleryFuseButtonSample(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected void initSample() {
        demo.layout(body, preferences, button -> {
            addRenderableWidget(button);
            if (initialFocus == null) initialFocus = button;
        });
    }

    @Override protected int minimumBodyHeight() { return FuseButtonDemo.HEIGHT; }

    @Override protected void tickSample() { demo.tick(); }

    @Override protected void cancelInteraction() { demo.cancelPress(); }

    @Override
    public void removed() {
        demo.close();
        super.removed();
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (body.width() < 68 || body.height() < FuseButtonDemo.HEIGHT) return;
        int y = demo.statusY(body);
        for (var line : font.split(demo.status(), body.width() - 8)) {
            graphics.text(font, line, body.x() + (body.width() - font.width(line)) / 2, y, theme.text(), false);
            y += font.lineHeight;
        }
    }
}
