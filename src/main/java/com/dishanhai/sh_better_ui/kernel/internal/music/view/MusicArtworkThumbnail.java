// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import java.util.function.IntBinaryOperator;

/** Center-crops artwork and supplies the same alpha mask to each native texture adapter. */
final class MusicArtworkThumbnail {
    static int size(int width, int height) {
        return Math.min(256, Math.min(width, height));
    }

    static int[] pixels(int width, int height, float cornerFraction, IntBinaryOperator read) {
        int crop = Math.min(width, height);
        int size = size(width, height);
        int[] pixels = new int[size * size];
        double radius = size * cornerFraction;
        for (int y = 0; y < size; y++) {
            double dy = Math.max(0, radius - Math.min(y + .5, size - y - .5));
            int sourceY = (height - crop) / 2 + (int) ((y + .5) * crop / size);
            for (int x = 0; x < size; x++) {
                double dx = Math.max(0, radius - Math.min(x + .5, size - x - .5));
                int sourceX = (width - crop) / 2 + (int) ((x + .5) * crop / size);
                int color = read.applyAsInt(sourceX, sourceY);
                double coverage = Math.max(0, Math.min(1, radius + .5 - Math.hypot(dx, dy)));
                pixels[y * size + x] = (color & 0xffffff) | ((int) Math.round((color >>> 24) * coverage) << 24);
            }
        }
        return pixels;
    }

    private MusicArtworkThumbnail() {}
}
