// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.kernel.api.controls.FuseButtonState.Phase;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.function.Function;

final class FuseButtonPaint {
    interface Surface {
        void shape(float x, float y, float width, float height, float radius, float stroke, int edge, int fill);
        void pushClip(int x, int y, int right, int bottom);
        void popClip();
        void text(Font font, FormattedCharSequence line, int x, int y, int color);
        void icon(GraphicIcon icon, int x, int y, int color);
    }

    private FuseButtonPaint() {}

    static boolean hit(int width, int height, float radius, double x, double y) {
        double r = RoundedCorners.fit(width, height, radius);
        double dx = Math.max(0, Math.max(r - x, x - (width - r)));
        double dy = Math.max(0, Math.max(r - y, y - (height - r)));
        return dx * dx + dy * dy <= r * r;
    }

    static void draw(Surface surface, Bounds area, FuseButtonStyle style, UiPreferences preferences,
                     Phase phase, Phase previous, Function<Phase, Component> label, GraphicIcon idleIcon,
                     boolean active, boolean focused, boolean pressed, boolean hovered,
                     float alpha, float mix, float remaining) {
        boolean contrast = preferences.highContrast();
        float hover = active && hovered && preferences.feedback().hoverAnimation() ? 0.07f : 0f;
        int background = GuiMotion.lerpColor(style.background(), style.text(), pressed ? 0.18f : hover);
        if (contrast) background |= 0xFF000000;
        int text = contrast ? ContrastPolicy.readable(style.text(), background, background,
                ContrastPolicy.HIGH_CONTRAST_RATIO) : style.text();
        int fuseColor = contrast ? ContrastPolicy.readable(style.fuseColor(), background, background, 3)
                : style.fuseColor();
        float opacity = alpha * (phase == Phase.IDLE && !active && !contrast ? 0.55f : 1f);
        int x = area.x(), y = area.y(), w = area.width(), h = area.height();
        if (focused) {
            surface.shape(x - 2, y - 2, w + 4, h + 4, RoundedCorners.outsetRadius(w, h, style.radius(), 2),
                    1, fade(text, alpha), 0);
        }
        surface.shape(x, y, w, h, style.radius(), contrast ? 1 : 0,
                contrast ? fade(text, alpha) : 0, fade(background, opacity));
        if (phase == Phase.ARMED) fuse(surface, area, style, remaining, fade(fuseColor, alpha));
        if (mix < 1 && previous != phase) {
            drawFace(surface, area, label.apply(previous), icon(previous, idleIcon), fade(text, opacity * (1 - mix)));
        }
        drawFace(surface, area, label.apply(phase), icon(phase, idleIcon), fade(text, opacity * mix));
    }

    private static GraphicIcon icon(Phase phase, GraphicIcon idleIcon) {
        return switch (phase) {
            case IDLE -> idleIcon;
            case ARMED -> GraphicIcon.UNDO;
            case SETTLED -> GraphicIcon.SUCCESS;
        };
    }

    private static void drawFace(Surface surface, Bounds area, Component label, GraphicIcon icon, int color) {
        if ((color >>> 24) < 4) return;
        var font = Minecraft.getInstance().font;
        int w = area.width(), h = area.height();
        int inset = w >= 52 && h >= GraphicIcon.PIXELS + 2 ? GraphicIcon.PIXELS + 4 : 0;
        var lines = font.split(label, Math.max(1, w - 12 - inset));
        if (lines.isEmpty()) return;
        int count = Math.min(lines.size(), Math.max(1, (h - 4) / font.lineHeight));
        int textWidth = 0;
        for (int i = 0; i < count; i++) textWidth = Math.max(textWidth, font.width(lines.get(i)));
        int x = area.x() + (w - inset - textWidth) / 2;
        int y = area.y() + (h - count * font.lineHeight) / 2;
        surface.pushClip(area.x(), area.y(), area.right(), area.bottom());
        try {
            if (inset > 0) surface.icon(icon, x, area.y() + (h - GraphicIcon.PIXELS) / 2, color);
            for (int i = 0; i < count; i++) {
                var line = lines.get(i);
                surface.text(font, line, x + inset + (textWidth - font.width(line)) / 2,
                        y + i * font.lineHeight, color);
            }
        } finally {
            surface.popClip();
        }
    }

    private static int fade(int color, float opacity) {
        return (color & 0xFFFFFF) | Math.round((color >>> 24) * GuiMotion.clamp01(opacity)) << 24;
    }

    private static void fuse(Surface surface, Bounds area, FuseButtonStyle style, float fraction, int color) {
        if (fraction <= 0 || (color >>> 24) == 0) return;
        int x = area.x(), y = area.y(), w = area.width(), h = area.height();
        float stroke = Math.min(style.fuseThickness(), Math.min(w, h) / 2f);
        if (style.fuse() != FuseButtonStyle.Fuse.OUTLINE) {
            boolean top = style.fuse() == FuseButtonStyle.Fuse.TOP;
            int band = (int) Math.ceil(stroke);
            int start = top ? y : y + h - band;
            int right = x + Math.round(w * fraction);
            if (right <= x) return;
            surface.pushClip(x, start, right, start + band);
            try {
                surface.shape(x, y, w, h, style.radius(), stroke, color, 0);
            } finally {
                surface.popClip();
            }
            return;
        }
        if (fraction >= 1f) {
            rim(surface, area, style, stroke, color);
            return;
        }
        // Disjoint clips reveal the existing rounded stroke clockwise without drawing overlapping joins.
        int band = Math.min(h / 2, Math.max(1, (int) Math.ceil(Math.max(stroke, h / 4f))));
        int side = h - 2 * band;
        float burnt = (1f - fraction) * (2f * (w + side));
        int topCut = cut(burnt, w);
        int rightCut = cut(burnt - w, side);
        int bottomCut = cut(burnt - w - side, w);
        int leftCut = cut(burnt - 2f * w - side, side);
        clippedRim(surface, area, style, stroke, color, x + topCut, y, x + w, y + band);
        clippedRim(surface, area, style, stroke, color, x + w / 2, y + band + rightCut, x + w, y + h - band);
        clippedRim(surface, area, style, stroke, color, x, y + h - band, x + w - bottomCut, y + h);
        clippedRim(surface, area, style, stroke, color, x, y + band, x + w / 2, y + h - band - leftCut);
    }

    private static int cut(float burnt, int length) {
        return Math.min(length, Math.max(0, Math.round(burnt)));
    }

    private static void clippedRim(Surface surface, Bounds area, FuseButtonStyle style, float stroke, int color,
                                   int x, int y, int right, int bottom) {
        if (x >= right || y >= bottom) return;
        surface.pushClip(x, y, right, bottom);
        try {
            rim(surface, area, style, stroke, color);
        } finally {
            surface.popClip();
        }
    }

    private static void rim(Surface surface, Bounds area, FuseButtonStyle style, float stroke, int color) {
        surface.shape(area.x(), area.y(), area.width(), area.height(), style.radius(), stroke, color, 0);
    }
}
