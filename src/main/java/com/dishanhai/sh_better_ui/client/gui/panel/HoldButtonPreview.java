// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiHoldButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiHoldButton.Direction;
import com.dishanhai.sh_better_ui.kernel.api.input.HoldGesture.Timing;
import net.minecraft.network.chat.Component;

final class HoldButtonPreview implements PreviewScene {
    private static final String KEY = "sh_better_ui.hold_button.";

    private UiHoldButton right;
    private UiHoldButton up;

    @Override
    public void layout(PreviewContext host) {
        suspend();
        var area = host.viewport();
        int width = Math.min(176, area.width());
        int height = Math.min(24, (area.height() - 6) / 2);
        if (width < 64 || height < 14) return;
        int x = area.x() + (area.width() - width) / 2;
        int y = area.y() + (area.height() - 2 * height - 6) / 2;
        right = button(x, y, width, height, Direction.RIGHT, "right", host.preferences());
        up = button(x, y + height + 6, width, height, Direction.UP, "up", host.preferences());
        host.add(right);
        host.add(up);
    }

    @Override
    public void suspend() {
        if (right != null) right.cancelHold();
        if (up != null) up.cancelHold();
        right = null;
        up = null;
    }

    @Override
    public void close() {
        suspend();
    }

    private static UiHoldButton button(int x, int y, int width, int height, Direction direction, String label,
                                       UiPreferences preferences) {
        var button = ShBetterUiApi.controls().holdButton(new Bounds(x, y, width, height),
                Component.translatable(KEY + label), Component.translatable(KEY + "done"), Timing.DEFAULT,
                new UiHoldButton.Appearance(direction), preferences, () -> {}, null);
        button.setLeadingIcons(GraphicIcon.RUN, null);
        return button;
    }
}
