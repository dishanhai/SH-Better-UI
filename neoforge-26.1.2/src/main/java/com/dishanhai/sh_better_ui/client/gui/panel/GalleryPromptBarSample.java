// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPromptBar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

final class GalleryPromptBarSample extends GallerySampleScreen {
    private final PromptBarDemo demo = new PromptBarDemo();
    private Bounds area;
    private UiPromptBar prompt;

    GalleryPromptBarSample(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override protected int minimumBodyHeight() { return PromptBarDemo.HEIGHT; }

    @Override
    protected void initSample() {
        if (body.width() < UiPromptBar.MIN_WIDTH) return;
        area = PromptBarDemo.bounds(body);
        prompt = addRenderableWidget(demo.mount(font, area, preferences));
        initialFocus = prompt;
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (initialFocus != prompt || prompt == null) return;
        PanelText.drawLine(graphics, font, demo.status(), area.x(), area.bottom() + 4,
                area.width(), theme.text(), mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && prompt != null && getFocused() == prompt && prompt.dismissPopup()) {
            setDragging(false);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void cancelInteraction() {
        if (prompt != null) prompt.cancelInteraction();
    }
}
