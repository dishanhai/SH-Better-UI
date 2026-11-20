// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

/** Keeps manual browsing independent of a temporary transport scrub. */
final class LyricBrowseViewport {
    private boolean preview;
    private double bookmark = Double.NaN;

    /** Saves the drawn manual offset on entry and restores it on either release or cancellation. */
    double playback(boolean nextPreview, boolean manual, double offset) {
        if (nextPreview == preview) return offset;
        preview = nextPreview;
        if (preview) {
            bookmark = manual ? offset : Double.NaN;
            return offset;
        }
        double restored = manual && !Double.isNaN(bookmark) ? bookmark : offset;
        bookmark = Double.NaN;
        return restored;
    }

    boolean steering(boolean manual) { return preview || !manual; }
    void clear() { preview = false; bookmark = Double.NaN; }
}
