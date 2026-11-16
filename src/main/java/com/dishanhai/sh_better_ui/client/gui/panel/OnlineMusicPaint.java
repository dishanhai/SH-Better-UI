// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

final class OnlineMusicPaint {
    static boolean editing(com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider slider) { return slider.isEditing(); }
    static String clipboard() { return net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard(); }
    static void line(UiFrame ui, Font font, Component text, int x, int y, int width) {
        var value = font.substrByWidth(text, Math.max(0, width));
        ui.graphics().drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(value), x, y,
                UiTheme.current().text(), true);
    }
}
