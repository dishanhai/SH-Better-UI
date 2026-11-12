// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock.indicator;

import java.util.Objects;

/** Displays a small, immutable row-major ARGB pixel icon centered within its allocation. */
public final class IconIndicator implements CompactIndicator {
    private static final int MAX_SIDE = 64;

    private final int width;
    private final int height;
    private final int[] pixels;

    /** Copies the source pixel array. The icon may be at most 64 by 64 pixels. */
    public IconIndicator(int width, int height, int[] pixels) {
        Objects.requireNonNull(pixels, "pixels");
        if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE || pixels.length != width * height) {
            throw new IllegalArgumentException("Invalid indicator icon dimensions");
        }
        this.width = width;
        this.height = height;
        this.pixels = pixels.clone();
    }

    @Override public IndicatorSize measure(IndicatorConstraints constraints) {
        if (constraints.maxWidth() == 0 || constraints.maxHeight() == 0) return IndicatorSize.EMPTY;
        double scale = Math.min(1, Math.min((double) constraints.maxWidth() / width,
                (double) constraints.maxHeight() / height));
        return new IndicatorSize(Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)));
    }

    @Override public void render(IndicatorRenderContext context) {
        IndicatorSize size = measure(new IndicatorConstraints(context.width(), context.height()));
        int left = (context.width() - size.width()) / 2;
        int top = (context.height() - size.height()) / 2;
        for (int row = 0; row < size.height(); row++) {
            int sourceRow = row * height / size.height() * width;
            int runStart = 0;
            int runColor = pixels[sourceRow];
            for (int column = 1; column < size.width(); column++) {
                int color = pixels[sourceRow + column * width / size.width()];
                if (runColor == color) continue;
                context.fill(left + runStart, top + row, left + column, top + row + 1, runColor);
                runStart = column;
                runColor = color;
            }
            context.fill(left + runStart, top + row, left + size.width(), top + row + 1, runColor);
        }
    }
}
