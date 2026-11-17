// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.hud;

/** Geometry for a bounded horizontal HUD progress indicator. */
public final class TimelineBarModel {
    private TimelineBarModel() {}

    /** Returns a width in [0, width]; non-finite fractions and non-positive widths are empty. */
    public static int fillWidth(int width, double fraction) {
        if (width <= 0 || !Double.isFinite(fraction)) return 0;
        return (int) Math.round(width * Math.max(0.0, Math.min(1.0, fraction)));
    }

    /** The fill starts at the right edge when reversed. */
    public static int fillStart(int x, int width, double fraction, boolean reversed) {
        int safeWidth = Math.max(0, width);
        return reversed ? x + safeWidth - fillWidth(safeWidth, fraction) : x;
    }
}
