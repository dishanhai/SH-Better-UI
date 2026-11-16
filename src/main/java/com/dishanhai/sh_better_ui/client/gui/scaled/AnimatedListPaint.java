// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.controls.AnimatedListState;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.locale.Language;

import java.util.List;

/** Shared material and masked content painting for both native GUI renderers. */
final class AnimatedListPaint {
    private AnimatedListPaint() {}

    static <T> void draw(UiAnimatedList.Canvas c, Font font, Bounds area, Bounds view, List<UiAnimatedList.Item<T>> items,
                         AnimatedListState state, UiAnimatedList.Style style, UiPreferences prefs, boolean focused,
                         boolean enabled, int pressed, int hovered, UiAnimatedList.ItemRenderer<T> renderer, long now) {
        UiTheme theme = style.theme() == UiAnimatedList.Theme.CURRENT ? UiTheme.current()
                : new UiTheme(false, 0, 0, 0, 0, 0, 0);
        boolean light = style.theme() == UiAnimatedList.Theme.LIGHT || theme.light();
        int ink = theme.text(light ? 0xFF27272A : 0xFFF4F4F5);
        int muted = prefs.highContrast() ? ink : theme.secondaryText(light ? 0xFF89898B : 0xFF9A989F, .45f);
        int accent = prefs.highContrast() || style.accent() == null ? ink : style.accent() | 0xFF000000;
        int edge = prefs.highContrast() ? ink : (light ? 0x1427272A : 0x14FFFFFF);
        int frame = prefs.highContrast() ? (light ? 0xFFF0F0F3 : 0xFF26232E) : (light ? 0xCCF0F0F3 : 0xA826232E);
        c.opacity(1);
        if (style.frame()) {
            if (!prefs.highContrast()) {
                c.box(area.x() - 2, area.y() + 7, area.width() + 4, area.height(), style.radius() + 14, 0, light ? 0x1227272A : 0x38000000);
                c.box(area.x(), area.y() + 2, area.width(), area.height(), style.radius() + 12, 0, light ? 0x1027272A : 0x30000000);
            }
            c.plate(area, style.radius() + 12, edge, frame, prefs);
            if (!prefs.highContrast()) highlight(c, area.x(), area.y(), area.width(), area.height(), style.radius() + 12,
                    light ? 0xF2FFFFFF : 0x12FFFFFF);
        }
        if (focused) c.box(area.x() - 3, area.y() - 3, area.width() + 6, area.height() + 6,
                style.radius() + 15, accent, 0);
        if (view.width() <= 0 || view.height() <= 0) return;
        var fades = state.fades();
        // Mask actual RGBA content, including icons and the moving ring. No background-colored overlay.
        // The central unfaded band renders once. Edge bands use one GUI-unit scissor strips.
        int row = 0;
        while (row < view.height()) {
            double alpha = prefs.highContrast() ? 1 : fades.opacity(row + .5, view.height());
            int end = row + 1;
            if (alpha >= .99999) {
                while (end < view.height() && (prefs.highContrast() || fades.opacity(end + .5, view.height()) >= .99999)) end++;
            }
            c.clip(view.x(), view.y() + row, view.x() + view.width(), view.y() + end);
            try {
                for (int i = 0; i < items.size(); i++) {
                    var pose = state.pose(i, now);
                    double top = state.top(i) - state.scroll().offset() + pose.y();
                    double size = state.height(i);
                    double margin = size * Math.abs(pose.scale() - 1) / 2 + 4;
                    if (pose.opacity() <= 0 || top + size + margin < row || top - margin > end) continue;
                    float opacity = (float) (alpha * pose.opacity() * (enabled ? 1 : .55));
                    c.opacity(opacity);
                    c.push(view.x() + view.width() / 2f, (float) (view.y() + top + size / 2), (float) pose.scale());
                    try {
                        int x = -view.width() / 2, y = -(int) size / 2;
                        int topFill = theme.surface(light ? 0xFFFFFFFF : 0xFF3A3644);
                        int bottomFill = theme.surface(light ? 0xFFF6F6F8 : 0xFF27242F);
                        float brightness = i == pressed ? (light ? .92f : .85f) : i == state.selected() && !light ? 1.16f
                                : i == hovered ? (light ? .97f : 1.12f) : 1;
                        topFill = brightness(topFill, brightness);
                        bottomFill = brightness(bottomFill, brightness);
                        if (!prefs.highContrast()) c.box(x, y + 3, view.width(), (float) size, style.radius(), 0, light ? 0x1027272A : 0x40000000);
                        c.box(x, y, view.width(), (float) size, style.radius(), edge, bottomFill);
                        gradient(c, x + 1, y + 1, view.width() - 2, (int) size - 2, style.radius() - 1, topFill, bottomFill);
                        c.box(x, y, view.width(), (float) size, style.radius(), edge, 0);
                        if (!prefs.highContrast()) highlight(c, x, y, view.width(), (int) size, style.radius(),
                                light ? 0xFFFFFFFF : 0x1FFFFFFF);
                        if (renderer != null) renderer.draw(c, font, new Bounds(x, y, view.width(), (int) size), items.get(i),
                                i, i == state.selected(), ink, muted);
                        else content(c, font, new Bounds(x, y, view.width(), (int) size), items.get(i), ink, muted, prefs);
                    } finally { c.pop(); }
                }
                if (state.indicator().visible()) {
                    int selected = state.selected();
                    var pose = state.pose(selected, now);
                    c.opacity((float) (alpha * pose.opacity()));
                    float height = (float) state.indicator().height();
                    c.push(view.x() + view.width() / 2f,
                            (float) (view.y() + state.indicator().y() - state.scroll().offset() + pose.y() + height / 2), (float) pose.scale());
                    try {
                        c.box(-view.width() / 2f, -height / 2, view.width(), height, style.radius(),
                                prefs.highContrast() || style.accent() != null ? accent : alpha(accent, light ? .28 : .3),
                                prefs.highContrast() ? 0 : style.accent() != null ? alpha(accent, .08) : light ? 0 : alpha(accent, .05));
                    } finally { c.pop(); }
                }
            } finally { c.unclip(); c.opacity(1); }
            row = end;
        }
        if (style.scrollbar() && state.scroll().maxOffset() > 0) {
            int y = view.y() + state.scroll().thumbTop(view.height());
            c.box(view.x() + view.width() + 2, y, 6, state.scroll().thumbHeight(view.height()), 3,
                    0, prefs.highContrast() || state.scroll().dragging() ? ink : alpha(ink, .16));
        }
    }

    private static <T> void content(UiAnimatedList.Canvas c, Font font, Bounds row, UiAnimatedList.Item<T> item,
                                   int ink, int muted, UiPreferences prefs) {
        int x = row.x() + 10, y = row.y();
        if (item.lead() != null && row.width() >= 68) {
            int size = Math.min(36, row.height() - 12);
            c.box(x, y + (row.height() - size) / 2f, size, size, 8, 0, alpha(ink, .08));
            if (item.image()) {
                c.push(x + size / 2f, y + row.height() / 2f, size / 16f);
                try { c.icon(item.lead(), -8, -8, 0xFFFFFFFF, prefs); }
                finally { c.pop(); }
            } else c.icon(item.lead(), x + (size - 16) / 2, y + (row.height() - 16) / 2, ink, prefs);
            x += size + 12;
        }
        int available = Math.max(0, row.x() + row.width() - 14 - x);
        int metaWidth = Math.min(font.width(item.meta()), available / 3);
        if (metaWidth > 0) {
            text(c, font, item.meta(), row.x() + row.width() - 14 - metaWidth,
                    y + (row.height() - font.lineHeight) / 2, metaWidth, muted);
            available -= metaWidth + 12;
        }
        boolean description = !item.description().getString().isEmpty() && row.height() >= font.lineHeight * 2 + 8;
        int titleY = y + (row.height() - (description ? font.lineHeight * 2 + 3 : font.lineHeight)) / 2;
        text(c, font, item.title(), x, titleY, available, ink);
        if (description) text(c, font, item.description(), x, titleY + font.lineHeight + 3, available, muted);
    }

    private static void text(UiAnimatedList.Canvas c, Font font, Component text, int x, int y, int width, int ink) {
        if (width <= 0) return;
        FormattedText fitted = text;
        if (font.width(text) > width) {
            int ellipsis = font.width(CommonComponents.ELLIPSIS);
            if (width < ellipsis) return;
            fitted = FormattedText.composite(font.substrByWidth(text, width - ellipsis), CommonComponents.ELLIPSIS);
        }
        c.text(font, Language.getInstance().getVisualOrder(fitted), x, y, ink);
    }

    static int alpha(int color, double alpha) { return (color & 0xFFFFFF) | (int) Math.round((color >>> 24) * alpha) << 24; }
    private static void highlight(UiAnimatedList.Canvas c, int x, int y, int width, int height, float radius, int color) {
        int inset = (int) Math.ceil(Math.min(radius, Math.min(width, height) / 2f));
        if (width > inset * 2) c.fill(x + inset, y, x + width - inset, y + 1, color);
    }
    private static void gradient(UiAnimatedList.Canvas c, int x, int y, int width, int height, float radius, int top, int bottom) {
        double r = Math.max(0, Math.min(radius, Math.min(width, height) / 2.0));
        for (int row = 0; row < height && width > 0; row++) {
            double distance = Math.max(0, r - Math.min(row + .5, height - row - .5));
            int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - distance * distance)));
            c.fill(x + inset, y + row, x + width - inset, y + row + 1,
                    GuiMotion.lerpColor(top, bottom, (row + .5f) / height));
        }
    }
    private static int brightness(int color, float factor) {
        int r = Math.min(255, Math.round((color >> 16 & 255) * factor));
        int g = Math.min(255, Math.round((color >> 8 & 255) * factor));
        int b = Math.min(255, Math.round((color & 255) * factor));
        return color & 0xFF000000 | r << 16 | g << 8 | b;
    }
}
