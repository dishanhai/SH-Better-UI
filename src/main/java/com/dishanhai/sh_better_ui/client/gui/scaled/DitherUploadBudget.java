package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Limits CPU native-image writes and glTexSubImage traffic to 256 KiB per draw. */
final class DitherUploadBudget {
    static final int PIXELS_PER_DRAW = 64 * 1024;
    private DitherUploadBudget() {}
    static int rows(int width, int height, int nextRow) {
        if (width < 1 || width > 2048 || height < 1 || height > 2048 || nextRow < 0 || nextRow >= height)
            throw new IllegalArgumentException();
        return Math.min(height - nextRow, PIXELS_PER_DRAW / width);
    }
}
