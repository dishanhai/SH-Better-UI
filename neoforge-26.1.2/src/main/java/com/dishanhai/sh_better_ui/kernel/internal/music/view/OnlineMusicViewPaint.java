// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

final class OnlineMusicViewPaint {
    static boolean editing(UiSlider slider) { return slider.isPointerEditing(); }
    static String clipboard() { return net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard(); }
    static void line(UiFrame ui, Font font, Component text, int x, int y, int width, int color) {
        var value = font.substrByWidth(text, Math.max(0, width));
        ui.graphics().text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(value), x, y, color, false);
    }
    static void paragraph(UiFrame ui, Font font, Component text, Bounds bounds, int color) {
        int y = bounds.y();
        for (var line : font.split(text, Math.max(1, bounds.width()))) {
            if (y + font.lineHeight > bounds.bottom()) break;
            ui.graphics().text(font, line, bounds.x(), y, color, false);
            y += font.lineHeight + 3;
        }
    }
    static void panel(UiFrame ui, Bounds bounds, int color, boolean border) {
        RoundedCorners.draw(ui.graphics(), bounds.x(), bounds.y(), bounds.width(), bounds.height(), 6, border ? 1 : 0,
                ui.preferences().highContrast() ? UiTheme.current().text() : UiTheme.current().border(), color);
    }
    static void artwork(UiFrame ui, Bounds bounds) {
        if (bounds.width() < 24 || bounds.height() < 24) return;
        panel(ui, bounds, UiTheme.current().surface(), true);
        int scale = Math.max(1, Math.min(5, (Math.min(bounds.width(), bounds.height()) - 12) / 16));
        GraphicIcon.MUSIC.draw(ui.graphics(), bounds.x() + (bounds.width() - 16 * scale) / 2,
                bounds.y() + (bounds.height() - 16 * scale) / 2, scale, UiTheme.current().accent(), ui.preferences());
    }
}
