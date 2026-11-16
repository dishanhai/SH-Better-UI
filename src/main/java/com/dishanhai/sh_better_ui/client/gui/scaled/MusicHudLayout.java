// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

/** Pure geometry for the independent music information/lyrics HUD. All sizes are GUI units. */
public record MusicHudLayout(Bounds panel, Bounds cover, Bounds metadata, Bounds progress,
                             Bounds lyrics, boolean visible) {
    public static MusicHudLayout arrange(Bounds panel, int lineHeight) {
        int line = Math.max(1, lineHeight);
        Bounds empty = new Bounds(panel.x(), panel.y(), 0, 0);
        if (panel.width() < 96 || panel.height() < 2 * line + 16)
            return new MusicHudLayout(panel, empty, empty, empty, empty, false);
        int inset = 7;
        int coverSize = Math.min(42, Math.min(panel.height() - inset * 2, panel.width() / 4));
        Bounds cover = new Bounds(panel.x() + inset, panel.y() + inset, coverSize, coverSize);
        int textX = cover.right() + 7;
        int textWidth = Math.max(0, panel.right() - inset - textX);
        int headerHeight = Math.max(coverSize, 2 * line + 2);
        Bounds metadata = new Bounds(textX, panel.y() + inset, textWidth, headerHeight);
        int progressY = panel.y() + inset + headerHeight + 4;
        boolean timeline = progressY + line + 5 <= panel.bottom() - inset;
        Bounds progress = timeline ? new Bounds(panel.x() + inset, progressY, panel.width() - 2 * inset, 2) : empty;
        int lyricY = timeline ? progressY + line + 7 : panel.y() + inset + headerHeight + 4;
        int lyricHeight = Math.max(0, panel.bottom() - inset - lyricY);
        Bounds lyrics = lyricHeight >= line + 4
                ? new Bounds(panel.x() + inset, lyricY, panel.width() - 2 * inset, lyricHeight) : empty;
        return new MusicHudLayout(panel, cover, metadata, progress, lyrics, true);
    }

    /** Same normalization as MP HUD providers: fractions refer to the remaining travel distance. */
    public static Bounds anchored(int viewportWidth, int viewportHeight, int width, int height,
                                  double xFraction, double yFraction) {
        int w = Math.min(Math.max(0, width), Math.max(0, viewportWidth));
        int h = Math.min(Math.max(0, height), Math.max(0, viewportHeight));
        return new Bounds((int) Math.round((Math.max(0, viewportWidth) - w) * finiteFraction(xFraction)),
                (int) Math.round((Math.max(0, viewportHeight) - h) * finiteFraction(yFraction)), w, h);
    }

    private static double finiteFraction(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : .5;
    }

    public static double progress(long position, long duration) {
        return position < 0 || duration <= 0 ? 0 : Math.min(1, position / (double) duration);
    }
}
