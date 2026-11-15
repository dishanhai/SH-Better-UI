// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiHoldButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiHoldButton.Direction;
import com.dishanhai.sh_better_ui.kernel.api.input.HoldGesture.Timing;
import net.minecraft.network.chat.Component;

final class GalleryHoldButtonSample extends GallerySampleScreen {
    private static final String KEY = "sh_better_ui.hold_button.";

    GalleryHoldButtonSample(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected void initSample() {
        int width = Math.min(176, body.width());
        int height = Math.min(24, (body.height() - 6) / 2);
        if (width < 64 || height < 14) return;
        int x = body.x() + (body.width() - width) / 2;
        int y = body.y() + (body.height() - 2 * height - 6) / 2;
        var right = button(x, y, width, height, Direction.RIGHT, "right");
        var up = button(x, y + height + 6, width, height, Direction.UP, "up");
        initialFocus = addRenderableWidget(right);
        addRenderableWidget(up);
    }

    private UiHoldButton button(int x, int y, int width, int height, Direction direction, String label) {
        var button = ShBetterUiApi.controls().holdButton(new Bounds(x, y, width, height),
                Component.translatable(KEY + label), Component.translatable(KEY + "done"), Timing.DEFAULT,
                new UiHoldButton.Appearance(direction), preferences, () -> {}, null);
        button.setLeadingIcons(GraphicIcon.RUN, null);
        return button;
    }
}
