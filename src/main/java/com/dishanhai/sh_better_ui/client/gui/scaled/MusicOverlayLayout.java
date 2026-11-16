// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

/** Shared native/editor geometry. Size changes reflow content rather than scaling glyphs. */
public final class MusicOverlayLayout {
    private MusicOverlayLayout() {}

    public static Bounds panel(int viewportWidth, int viewportHeight, boolean island,
                               float scale, float x, float y) {
        return MusicHudLayout.anchored(viewportWidth, viewportHeight,
                Math.round((island ? 220 : 230) * scale), Math.round(112 * scale), x, y);
    }

    public static Bounds compact(Bounds full, float scale) {
        // LocalMusicIsland needs 29 GUI units for its compact content at every supported size.
        return new Bounds(full.x(), full.y(), full.width(),
                Math.min(full.height(), Math.max(29, Math.round(36 * scale))));
    }
}
