// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiBranchedMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class GalleryBranchedMenuSample extends GallerySampleScreen {
    private final BranchedMenuDemo demo = new BranchedMenuDemo();
    private UiBranchedMenu menu;

    GalleryBranchedMenuSample(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected int minimumBodyHeight() { return BranchedMenuDemo.MIN_HEIGHT; }

    @Override
    protected void initSample() {
        menu = demo.layout(body, preferences);
        menu.buttons().forEach(this::addRenderableWidget);
        initialFocus = menu.selectedButton();
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean contrast = preferences.highContrast();
        int rail = contrast ? GuiMotion.lerpColor(theme.surface(), theme.text(), 0.55f) : theme.border();
        menu.draw(graphics::fill, rail,
                contrast ? theme.text() : theme.accent(0xFF93C5FD));
    }
}
