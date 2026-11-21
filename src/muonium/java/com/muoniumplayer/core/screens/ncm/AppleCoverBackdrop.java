package com.muoniumplayer.core.screens.ncm;

import com.muoniumplayer.core.ncm.music.dto.Music;
import com.muoniumplayer.render.skija.SkijaGfx;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.Rect;

/** A soft, dark, cover-derived now-playing background, without copying or reading back the art. */
final class AppleCoverBackdrop {
    private static final float[] STOPS = {0f, .46f, 1f};
    private final int[] previous = new int[3];
    private final int[] target = new int[3];
    private long lastFrame;
    private double fade = 1;
    private double time;

    void reset() {
        lastFrame = 0;
        fade = 1;
        time = 0;
    }

    boolean render(double x, double y, double width, double height, float alpha,
                   Music track, boolean staticMode) {
        Canvas canvas = SkijaGfx.canvas();
        if (canvas == null || width <= 0 || height <= 0) return false;
        long now = System.nanoTime();
        boolean first = lastFrame == 0;
        double dt = first ? 0 : Math.min(.05, Math.max(0, (now - lastFrame) / 1e9));
        lastFrame = now;
        int[] palette = track == null ? null : track.getRepresentativeColors();
        if (track != null && (palette == null || palette.length == 0)) palette = track.getCoverPalette();
        if (first) {
            for (int i = 0; i < target.length; i++) {
                previous[i] = target[i] = AppleLyricEffects.backdropColor(palette, i);
            }
        }
        boolean changed = false;
        for (int i = 0; i < target.length; i++) {
            if (target[i] != AppleLyricEffects.backdropColor(palette, i)) changed = true;
        }
        if (changed) {
            for (int i = 0; i < target.length; i++) {
                previous[i] = tint(i);
                target[i] = AppleLyricEffects.backdropColor(palette, i);
            }
            fade = 0;
        }
        fade = staticMode ? 1 : Math.min(1, fade + dt / .72);
        if (!staticMode) time += dt;
        double t = staticMode ? 0 : time;
        Rect bounds = Rect.makeXYWH((float) x, (float) y, (float) width, (float) height);
        Rect unit = Rect.makeWH(1f, 1f);
        try (Paint paint = new Paint()) {
            int saved = canvas.save();
            try {
                if (alpha < .999f) {
                    paint.setAlphaf(alpha);
                    canvas.saveLayer(bounds, paint);
                }
                canvas.translate((float) x, (float) y);
                canvas.scale((float) width, (float) height);
                paint.setColor(0xff171618);
                canvas.drawRect(unit, paint);

                drawField(canvas, unit, paint, (float) (.17 + .12 * Math.sin(t * .26)),
                        (float) (.16 + .10 * Math.sin(t * .19)), .90f, tint(0));
                drawField(canvas, unit, paint, (float) (.80 + .10 * Math.sin(t * .20 + 1.2)),
                        (float) (.51 + .13 * Math.sin(t * .24 + 2.1)), .84f, tint(1));
                drawField(canvas, unit, paint, (float) (.29 + .14 * Math.sin(t * .18 + 2.3)),
                        (float) (.94 + .11 * Math.sin(t * .21 + .8)), .77f, tint(2));

                try (Shader shade = Shader.makeLinearGradient(0f, 0f, 0f, 1f,
                        new int[] {0x04000000, 0x10000000, 0x96000000}, STOPS)) {
                    paint.setColor(0xffffffff);
                    paint.setShader(shade);
                    canvas.drawRect(unit, paint);
                    paint.setShader(null);
                }
            } finally {
                canvas.restoreToCount(saved);
            }
        }
        return true;
    }

    private double smoothFade() {
        return fade * fade * (3 - 2 * fade);
    }

    private int tint(int index) {
        return AppleLyricEffects.mix(previous[index], target[index], smoothFade());
    }

    private static void drawField(Canvas canvas, Rect area, Paint paint,
                                  float x, float y, float radius, int color) {
        int rgb = color & 0x00ffffff;
        try (Shader field = Shader.makeRadialGradient(x, y, radius,
                new int[] {0xeb000000 | rgb, 0xa7000000 | rgb, rgb}, STOPS)) {
            paint.setColor(0xffffffff);
            paint.setShader(field);
            canvas.drawRect(area, paint);
            paint.setShader(null);
        }
    }
}
