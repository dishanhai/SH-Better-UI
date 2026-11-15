// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCodeSlots;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** 26.x gallery host for the numeric input demo. */
final class GalleryCodeSlotsSample extends GallerySampleScreen {
    private static final String DEMO_CODE = "123456";
    private UiCodeSlots slots;

    GalleryCodeSlotsSample(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected void initSample() {
        int width = Math.min(184, body.width());
        int x = body.x() + (body.width() - width) / 2;
        int y = body.y() + (body.height() - 72) / 2;
        var bounds = new Bounds(x, y + 17, width, 22);
        if (slots == null) {
            slots = ShBetterUiApi.controls().codeSlots(bounds, 6,
                    Component.translatable("sh_better_ui.code_slots.label"), preferences);
            slots.setOnComplete(code -> slots.setStatus(DEMO_CODE.equals(code)
                    ? UiCodeSlots.Status.SUCCESS : UiCodeSlots.Status.ERROR));
        } else {
            slots.setX(bounds.x());
            slots.setY(bounds.y());
            slots.setWidth(bounds.width());
            slots.setPreferences(preferences);
        }
        initialFocus = addRenderableWidget(slots);
        int buttonWidth = Math.min(104, width);
        addButton(new Bounds(x + (width - buttonWidth) / 2, y + 56, buttonWidth, 17),
                Component.translatable("controls.reset"), ignored -> slots.setValue(""), false);
    }

    @Override
    protected void tickSample() {
        if (slots != null) slots.tick();
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (slots == null) return;
        drawCentered(graphics, Component.translatable("sh_better_ui.code_slots.demo", DEMO_CODE), slots.getY() - 13);
        drawCentered(graphics, slots.statusMessage(), slots.getBottom() + 3);
    }

    private void drawCentered(GuiGraphicsExtractor graphics, Component text, int y) {
        var lines = font.split(text, body.width());
        for (var line : lines) {
            graphics.text(font, line, body.x() + (body.width() - font.width(line)) / 2, y, theme.text(), false);
            y += font.lineHeight;
        }
    }
}
