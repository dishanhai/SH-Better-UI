// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.muoniumplayer.core.rendering.RGBA;
import com.muoniumplayer.core.screens.ncm.NCMTheme;
import com.muoniumplayer.core.screens.ncm.effects.EffectsLayout.Box;
import com.muoniumplayer.render.skija.SkijaGfx;
import com.muoniumplayer.render.skija.SkijaShapes;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.RRect;

/** The palette and geometry of the player's lyric settings popover. */
public final class PlayerMenuStyle {
    public static final int WIDTH = 178;
    public static final int PADDING = 10;
    public static final int RADIUS = 10;
    public static final int ROW_RADIUS = 6;
    public static final int ROW_HEIGHT = 22;
    public static final int ROW_GAP = 4;
    public static final int ROW_PITCH = ROW_HEIGHT + ROW_GAP;
    public static final int ROW_INSET = 6;

    private PlayerMenuStyle() {}

    public static int surface(float alpha, boolean highContrast) {
        return RGBA.color(0x0f1217, alpha * (highContrast ? 1f : .88f));
    }

    public static int border(float alpha, boolean highContrast) {
        return RGBA.white(alpha * (highContrast ? .8f : .16f));
    }

    public static float borderWidth(boolean highContrast) {
        return highContrast ? 1.5f : .8f;
    }

    public static float rowOpacity(boolean hover, boolean pressed) {
        return pressed ? .24f : hover ? .16f : .08f;
    }

    public static int rowColor(float opacity) {
        return RGBA.white(opacity);
    }

    public static int text(float alpha, boolean highContrast) {
        return RGBA.white(alpha * (highContrast ? 1f : .82f));
    }

    public static int accent(float alpha, boolean highContrast) {
        return highContrast ? RGBA.white(alpha) : RGBA.color(NCMTheme.getAccentColor(), alpha);
    }

    public static int themedAccent(int rgb, boolean highContrast) {
        return highContrast ? 0xffffffff : RGBA.opaque(rgb);
    }

    public static void panel(Box box, double scale, float alpha, boolean highContrast) {
        Canvas canvas = SkijaGfx.canvas();
        if (canvas == null || box.width() <= 0 || box.height() <= 0 || alpha <= .002f) return;
        double radius = RADIUS * scale;
        if (!highContrast) shadow(canvas, box, scale, alpha);
        SkijaShapes.roundedRect(box.x(), box.y(), box.width(), box.height(), radius, surface(alpha, highContrast));
        SkijaShapes.roundedOutline(box.x(), box.y(), box.width(), box.height(), radius,
                borderWidth(highContrast) * scale, border(alpha, highContrast));
    }

    public static void shadow(Canvas canvas, Box box, double scale, float alpha) {
        float radius = (float) Math.min(RADIUS * scale, Math.min(box.width(), box.height()) * .5);
        canvas.drawRRect(RRect.makeXYWH((float) box.x(), (float) (box.y() + 2 * scale),
                        (float) box.width(), (float) box.height(), radius),
                SkijaGfx.fillBlurred(RGBA.black(alpha * .22f), (float) (3 * scale)));
    }

    public static void row(Box box, float opacity) {
        SkijaShapes.roundedRect(box.x(), box.y(), box.width(), box.height(), ROW_RADIUS,
                rowColor(opacity));
    }

    public static void focus(Box box, float alpha, boolean highContrast) {
        SkijaShapes.roundedOutline(box.x(), box.y(), box.width(), box.height(), ROW_RADIUS,
                highContrast ? 1.5 : 1, accent(alpha, highContrast));
    }
}
