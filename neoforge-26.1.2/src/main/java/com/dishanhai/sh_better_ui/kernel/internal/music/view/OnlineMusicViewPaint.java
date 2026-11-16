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
    static void artwork(UiFrame ui, Bounds bounds) { artwork(ui, bounds, 0f); }

    /** A calm editorial heading. Text is bounded independently of the decorative accent. */
    static void hero(UiFrame ui, Font font, Bounds area, Component kicker, Component title,
                     Component subtitle, float emphasis) {
        if (area.width() < 32 || area.height() < font.lineHeight + 6) return;
        var theme = UiTheme.current();
        int accent = theme.accent();
        int inset = area.width() >= 180 ? 12 : 6;
        int available = Math.max(1, area.width() - inset * 2);
        int y = area.y() + 3;
        if (area.height() >= 44) {
            line(ui, font, kicker, area.x() + inset, y, available, accent);
            y += font.lineHeight + 5;
        }
        float scale = area.height() >= 32 ? 1.35f : 1f;
        var heading = Component.literal(font.plainSubstrByWidth(title.getString(), (int)(available / scale)));
        ui.drawScaledText(font, heading, area.x() + inset, y, theme.text(), scale);
        y += (int)Math.ceil(font.lineHeight * scale) + 6;
        if (y + font.lineHeight <= area.bottom() - 4)
            line(ui, font, subtitle, area.x() + inset, y, available, muted(ui));
        int length = Math.min(available, 22 + Math.round(22 * clamp(emphasis)));
        ui.graphics().fill(area.x() + inset, area.bottom() - 2, area.x() + inset + length, area.bottom(), accent);
    }

    /** Empty/loading/error copy remains supplied by the owner; no fake results or network work. */
    static void emptyState(UiFrame ui, Font font, Bounds area, Component title, Component detail, float emphasis) {
        if (area.width() < 24 || area.height() < font.lineHeight + 12) return;
        var theme = UiTheme.current();
        panel(ui, area, GuiMotion.lerpColor(theme.background(), theme.surface(), .65f), ui.preferences().highContrast());
        int inset = Math.min(16, Math.max(6, area.width() / 12));
        boolean illustrated = area.width() >= 300 && area.height() >= 72
                && font.split(title, Math.max(1, area.width() - inset * 2 - 80)).size()
                    * (font.lineHeight + 3) <= area.height() - 12;
        int x = area.x() + inset;
        if (illustrated) {
            int size = Math.min(64, area.height() - inset * 2);
            artwork(ui, new Bounds(x, area.y() + (area.height() - size) / 2, size, size), emphasis);
            x += size + 16;
        }
        int width = Math.max(1, area.right() - inset - x);
        // Status/error text has priority over ornamental detail, including long localization.
        var titleLines = font.split(title, width);
        int titleHeight = titleLines.size() * (font.lineHeight + 3);
        int padding = 6;
        int y = area.y() + Math.max(padding, (area.height() - Math.min(titleHeight, area.height() - 2 * padding)) / 2);
        if (titleHeight + font.lineHeight + 8 <= area.height() - 2 * padding)
            y = area.y() + Math.max(padding, (area.height() - titleHeight - font.lineHeight - 8) / 2);
        for (var textLine : titleLines) {
            if (y + font.lineHeight > area.bottom() - padding) break;
            ui.graphics().text(font, textLine, x, y, theme.text(), false);
            y += font.lineHeight + 3;
        }
        y += 5;
        if (y + font.lineHeight <= area.bottom() - padding)
            paragraph(ui, font, detail, new Bounds(x, y, width, area.bottom() - y - padding), muted(ui));
    }

    static void transportSurface(UiFrame ui, Bounds area, float activity) {
        if (area.width() <= 0 || area.height() <= 0) return;
        var theme = UiTheme.current();
        int surface = GuiMotion.lerpColor(theme.surface(), theme.accent(), ui.preferences().highContrast() ? 0 : .035f + .035f * clamp(activity));
        panel(ui, area, surface, ui.preferences().highContrast());
        // A restrained top edge separates persistent playback from the browsing canvas.
        int inset = Math.min(12, area.width() / 4);
        ui.graphics().fill(area.x() + inset, area.y(), area.right() - inset, area.y() + 1,
                ui.preferences().highContrast() ? theme.text() : GuiMotion.lerpColor(theme.border(), theme.accent(), .35f));
    }

    static void resultSurface(UiFrame ui, Bounds area, boolean selected, boolean hovered) {
        if (area.width() <= 0 || area.height() <= 0) return;
        var theme = UiTheme.current();
        int color = selected ? GuiMotion.lerpColor(theme.surface(), theme.accent(), .16f)
                : hovered ? GuiMotion.lerpColor(theme.background(), theme.surface(), .8f) : theme.background();
        panel(ui, area, color, ui.preferences().highContrast() && (selected || hovered));
        if (selected && area.height() >= 7) ui.graphics().fill(area.x(), area.y() + 3, area.x() + Math.min(2, area.width()),
                Math.max(area.y() + 3, area.bottom() - 3), theme.accent());
    }

    /** Original vector sleeve/record, not remote artwork. Motion changes only ornamental bars. */
    static void artwork(UiFrame ui, Bounds bounds, float activity) {
        int size = Math.min(bounds.width(), bounds.height());
        if (size < 16) return;
        var theme = UiTheme.current();
        int x = bounds.x() + (bounds.width() - size) / 2, y = bounds.y() + (bounds.height() - size) / 2;
        int border = ui.preferences().highContrast() ? theme.text() : theme.border();
        RoundedCorners.draw(ui.graphics(), x, y, size, size, Math.min(9, size / 5f), 1,
                border, GuiMotion.lerpColor(theme.surface(), theme.accent(), .13f));
        int diameter = Math.max(10, size - 10), cx = x + size / 2, cy = y + size / 2;
        RoundedCorners.draw(ui.graphics(), cx - diameter / 2, cy - diameter / 2, diameter, diameter,
                diameter / 2f, 1, border, theme.background());
        int inner = Math.max(6, diameter / 3);
        RoundedCorners.draw(ui.graphics(), cx - inner / 2, cy - inner / 2, inner, inner,
                inner / 2f, 0, theme.accent(), theme.accent());
        ui.graphics().fill(cx, cy, cx + 1, cy + 1, theme.text());
        if (size >= 32) {
            float amount = ui.preferences().reducedMotion() ? 0f : clamp(activity);
            for (int i = 0; i < 4; i++) {
                int h = 2 + Math.round((2 + i % 3) * amount);
                int bx = x + size - 4 - i * 3;
                ui.graphics().fill(bx, y + size - 4 - h, bx + 1, y + size - 4, theme.accent());
            }
        }
    }
    private static int muted(UiFrame ui) {
        var theme = UiTheme.current();
        return ui.preferences().highContrast() ? theme.text() : GuiMotion.lerpColor(theme.text(), theme.background(), .3f);
    }
    private static float clamp(float value) { return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0f; }
}
