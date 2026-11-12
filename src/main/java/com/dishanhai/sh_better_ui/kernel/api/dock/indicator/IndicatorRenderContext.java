// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock.indicator;

import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** Drawing commands for a single callback. Coordinates are local GUI pixels and remain clipped to the allocation. */
public final class IndicatorRenderContext {
    private final RenderService.Context canvas;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final float opacity;
    private final IndicatorContext frame;
    private boolean live = true;

    IndicatorRenderContext(RenderService.Context canvas, int x, int y, int width, int height, float opacity,
                           IndicatorContext frame) {
        this.canvas = canvas;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.opacity = opacity;
        this.frame = frame;
    }

    /** Current drawable width. */
    public int width() { return width; }

    /** Current drawable height. */
    public int height() { return height; }

    /** Frame data shared by the window transition and all its indicator layers. */
    public IndicatorContext frame() { return frame; }

    /** Fills a local rectangle, multiplying its ARGB alpha by the accumulated layer opacity. */
    public void fill(int left, int top, int right, int bottom, int argb) {
        requireLive();
        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.min(width, right);
        bottom = Math.min(height, bottom);
        int color = applyOpacity(argb);
        if (left < right && top < bottom && color >>> 24 != 0) {
            canvas.fill(x + left, y + top, x + right, y + bottom, color);
        }
    }

    /** Draws localized text with the current layer opacity and native clipping. */
    public void text(Component label, int left, int top, int argb) {
        requireLive();
        Objects.requireNonNull(label, "label");
        int color = applyOpacity(argb);
        // Minecraft's text renderer treats alpha values below four as unspecified.
        if (color >>> 24 < 4 || left >= width || top >= height) return;
        long screenX = (long) x + left;
        long screenY = (long) y + top;
        if (screenX >= Integer.MIN_VALUE && screenY >= Integer.MIN_VALUE) {
            canvas.text(label, (int) screenX, (int) screenY, color);
        }
    }

    void layer(IndicatorRenderer renderer, int left, int top, IndicatorSize size, float alpha) {
        requireLive();
        renderer.render(canvas, x + left, y + top, size.width(), size.height(), opacity * alpha, frame);
    }

    void close() {
        live = false;
    }

    private int applyOpacity(int argb) {
        return (argb & 0x00ffffff) | Math.round((argb >>> 24) * opacity) << 24;
    }

    private void requireLive() {
        if (!live) throw new IllegalStateException("Indicator render context has expired");
    }
}
