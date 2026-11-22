// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.presentation;

/** A viewport of square covers and two caption lines. Scrolling advances by grid row. */
public record MusicArtworkGrid(int columns, int side, int visibleRows) {
    public static final int GAP = 12;
    public static final int TOP = 6;
    static final int CAPTION = 32;

    public static MusicArtworkGrid fit(int width, int height) {
        int available = width - 12; // Keep the scrollbar and its hit area clear.
        if (available < 12 || height < TOP + CAPTION + 12) return new MusicArtworkGrid(1, 12, 0);
        int columns = Math.max(1, Math.min(4, (available + GAP) / (88 + GAP)));
        int side = Math.min(192, Math.min((available - GAP * (columns - 1)) / columns, height - TOP - CAPTION));
        return new MusicArtworkGrid(columns, side, (height - TOP + GAP) / (side + CAPTION + GAP));
    }

    public int cardHeight() { return side + CAPTION; }
    public int pitch() { return cardHeight() + GAP; }
    public int rowCount(int cards) { return (cards + columns - 1) / columns; }
    public int contentWidth() { return columns * side + (columns - 1) * GAP; }
}
