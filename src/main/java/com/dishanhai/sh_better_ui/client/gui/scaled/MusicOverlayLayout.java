// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

/** Shared native/editor geometry. Size changes reflow content rather than scaling glyphs. */
public final class MusicOverlayLayout {
    private MusicOverlayLayout() {}

    public static Bounds panel(int viewportWidth, int viewportHeight, boolean island,
                               float scale, float x, float y) {
        return MusicHudLayout.anchored(viewportWidth, viewportHeight,
                island ? Math.max(168, Math.round(208 * scale)) : Math.round(230 * scale),
                island ? Math.max(92, Math.round(92 * scale)) : Math.round(112 * scale), x, y);
    }

    public static Bounds compact(Bounds full, float scale) {
        // Keep native-size artwork legible at small scales; expansion shares the same center.
        int width = Math.min(full.width(), Math.max(94, Math.round(126 * scale)));
        return new Bounds(full.x() + (full.width() - width) / 2, full.y(), width,
                Math.min(full.height(), Math.max(28, Math.round(28 * scale))));
    }
}
