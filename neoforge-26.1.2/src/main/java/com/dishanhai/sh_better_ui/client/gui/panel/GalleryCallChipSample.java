// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCallChip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class GalleryCallChipSample extends GallerySampleScreen {
    private final CallChipDemo demo = new CallChipDemo();
    private UiCallChip chip;

    GalleryCallChipSample(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected int minimumBodyHeight() {
        return 58;
    }

    @Override
    protected void initSample() {
        int width = Math.min(212, body.width() - 12);
        int x = body.x() + (body.width() - width) / 2;
        int y = body.y() + (body.height() - 58) / 2;
        chip = addRenderableWidget(ShBetterUiApi.controls().callChip(new Bounds(x, y, width, 30),
                label("demo_name"), label("demo_argument"), GraphicIcon.CODE, demo.state, preferences, true, demo::retry));
        initialFocus = addButton(new Bounds(x, y + 36, width, 22), label("start"), ignored -> demo.start(), false);
    }

    @Override
    protected void tickSample() {
        demo.update();
    }

    @Override
    protected void cancelInteraction() {
        if (chip != null) chip.cancelPress();
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!previewAvailable()) cancelInteraction();
        if (chip != null && chip.mouseReleased(event)) {
            setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && chip != null && chip.cancelPress()) {
            setDragging(false);
            return true;
        }
        return super.keyPressed(event);
    }

    private static Component label(String key) {
        return Component.translatable("sh_better_ui.call_chip." + key);
    }
}
