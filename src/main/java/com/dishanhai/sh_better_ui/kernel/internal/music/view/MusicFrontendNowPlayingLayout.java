// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

/** Keep artwork, title and secondary actions separate even when GUI scale leaves a short body. */
record MusicFrontendNowPlayingLayout(Bounds cover, Bounds title, Bounds subtitle, Bounds lyrics, Bounds queue) {
    static MusicFrontendNowPlayingLayout fit(Bounds body) {
        var empty = new Bounds(body.x(), body.y(), 0, 0);
        if (body.width() < 96 || body.height() < 58) {
            return new MusicFrontendNowPlayingLayout(empty, empty, empty, empty, empty);
        }
        int actionY = body.bottom() - 26;
        int width = body.width();
        boolean stacked = width < 360 && body.height() >= 178;
        int size = stacked
            ? Math.min(192, Math.min(width - 24, body.height() - 77))
            : Math.min(252, Math.min(width * 45 / 100, body.height() - 34));
        int artX = stacked ? body.x() + (width - size) / 2 : body.x() + 8;
        int artTop = stacked ? body.y() + 2 : body.y() + Math.max(2, (actionY - body.y() - size) / 2);
        var cover = new Bounds(artX, artTop, size, size);
        int textX = stacked ? body.x() + 8 : cover.right() + 12;
        int textY = stacked ? cover.bottom() + 6 : body.y() + Math.max(4, (actionY - body.y() - 38) / 2);
        int textWidth = body.right() - textX - 8;
        var title = new Bounds(textX, textY, textWidth, 18);
        var subtitle = new Bounds(textX, textY + 19, textWidth, Math.min(14, Math.max(0, actionY - textY - 19)));
        var lyrics = new Bounds(body.right() - 58, actionY, 24, 24);
        var queue = new Bounds(body.right() - 28, actionY, 24, 24);
        return new MusicFrontendNowPlayingLayout(cover, title, subtitle, lyrics, queue);
    }

    boolean usable() { return cover.width() > 0; }
}
