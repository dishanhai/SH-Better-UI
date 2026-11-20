// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

/** Keeps manual browsing independent of a temporary transport scrub. */
final class LyricBrowseViewport {
    private static final long IDLE_MILLIS = 3600;
    private boolean preview;
    private long lastInput = -1;
    void input(long now) { lastInput = now; }
    boolean shouldFollow(long now, boolean manual, boolean synchronizedLyrics) {
        return manual && synchronizedLyrics && !preview && lastInput >= 0 && now - lastInput >= IDLE_MILLIS;
    }
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
    void clear() { preview = false; bookmark = Double.NaN; lastInput = -1; }
}
