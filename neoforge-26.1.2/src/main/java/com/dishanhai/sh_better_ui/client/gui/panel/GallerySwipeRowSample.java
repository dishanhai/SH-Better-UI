// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import net.minecraft.client.gui.GuiGraphicsExtractor;

final class GallerySwipeRowSample extends GallerySampleScreen {
    private final SwipeRowDemo demo = new SwipeRowDemo();

    GallerySwipeRowSample(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected void initSample() {
        demo.layout(body, preferences, widget -> {
            addRenderableWidget(widget);
            if (initialFocus == null && widget.visible && widget.isActive()) initialFocus = widget;
        });
    }

    @Override protected int minimumBodyHeight() { return SwipeRowDemo.HEIGHT; }

    @Override protected void tickSample() { demo.tick(); }

    @Override protected void cancelInteraction() { demo.cancelPress(); }

    @Override
    public void removed() {
        demo.cancelPress();
        super.removed();
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (body.width() < SwipeRowDemo.MIN_WIDTH + 8 || body.height() < SwipeRowDemo.HEIGHT) return;
        int y = demo.statusY(body);
        for (var line : font.split(demo.status(), body.width() - 8)) {
            graphics.text(font, line, body.x() + (body.width() - font.width(line)) / 2, y, theme.text(), false);
            y += font.lineHeight;
        }
    }
}
