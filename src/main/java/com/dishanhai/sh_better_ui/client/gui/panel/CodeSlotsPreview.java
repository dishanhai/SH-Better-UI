// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCodeSlots;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.addButton;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.drawCentered;

/** Interactive local demo for both rejection and success. */
final class CodeSlotsPreview implements PreviewScene {
    private static final String DEMO_CODE = "123456";
    private UiCodeSlots slots;
    private Font font;

    @Override
    public void layout(PreviewContext host) {
        var area = host.viewport();
        font = host.font();
        if (area.width() < 60 || area.height() < 80) return;

        int width = Math.min(184, area.width() - 8);
        int x = area.x() + (area.width() - width) / 2;
        int y = area.y() + (area.height() - 80) / 2;
        var bounds = new Bounds(x, y + 20, width, 22);
        if (slots == null) {
            slots = ShBetterUiApi.controls().codeSlots(bounds, 6,
                    Component.translatable("sh_better_ui.code_slots.label"), host.preferences());
            slots.setOnComplete(code -> slots.setStatus(DEMO_CODE.equals(code)
                    ? UiCodeSlots.Status.SUCCESS : UiCodeSlots.Status.ERROR));
        } else {
            slots.setX(bounds.x());
            slots.setY(bounds.y());
            slots.setWidth(bounds.width());
            slots.setPreferences(host.preferences());
        }
        host.add(slots);
        int buttonWidth = Math.min(104, width);
        addButton(host, new Bounds(x + (width - buttonWidth) / 2, y + 60, buttonWidth, 20),
                Component.translatable("controls.reset"), GraphicIcon.RESET, () -> slots.setValue(""));
    }

    @Override
    public void render(UiFrame frame) {
        var area = frame.viewport();
        if (slots == null || area.width() < 60 || area.height() < 80) return;
        slots.tick();
        int y = area.y() + (area.height() - 80) / 2;
        drawCentered(frame.graphics(), font, Component.translatable("sh_better_ui.code_slots.demo", DEMO_CODE),
                area.x() + 4, y - 1, area.width() - 8);
        drawCentered(frame.graphics(), font, slots.statusMessage(), area.x() + 4, y + 41, area.width() - 8);
    }
}
