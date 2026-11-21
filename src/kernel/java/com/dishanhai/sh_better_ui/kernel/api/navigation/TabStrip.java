// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.navigation;

import java.util.Objects;

/** Horizontal tab geometry. Coordinates and scroll offsets are in the caller's UI units. */
public final class TabStrip {
    private final int[] starts;
    private final int[] widths;
    private final int viewportWidth;
    private final int maxScroll;

    public TabStrip(int[] widths, int gap, int viewportWidth) {
        Objects.requireNonNull(widths, "widths");
        if (gap < 0 || viewportWidth < 0) throw new IllegalArgumentException("Negative geometry");
        this.widths = widths.clone();
        this.starts = new int[widths.length];
        this.viewportWidth = viewportWidth;
        long position = 0;
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] < 0) throw new IllegalArgumentException("Negative tab width");
            if (position > Integer.MAX_VALUE) throw new IllegalArgumentException("Tab strip too wide");
            starts[i] = (int) position;
            position += widths[i];
            if (i + 1 < widths.length) position += gap;
        }
        if (position > Integer.MAX_VALUE) throw new IllegalArgumentException("Tab strip too wide");
        this.maxScroll = Math.max(0, (int) position - viewportWidth);
    }

    public int size() { return widths.length; }
    public int maxScroll() { return maxScroll; }
    public int clampScroll(int scroll) { return Math.max(0, Math.min(maxScroll, scroll)); }

    /** Unclipped position, for drawing text under the caller's viewport clip. */
    public int start(int index, int scroll) { return starts[index] - clampScroll(scroll); }
    public int end(int index, int scroll) { return start(index, scroll) + widths[index]; }
    /** Visible half-open interval, both values relative to the viewport. */
    public int left(int index, int scroll) { return clip(start(index, scroll)); }
    public int right(int index, int scroll) { return clip(end(index, scroll)); }

    /** Scroll just enough to show a tab; oversized tabs align their leading edge. */
    public int reveal(int index, int scroll) {
        int current = clampScroll(scroll);
        int tabStart = starts[index];
        int tabEnd = tabStart + widths[index];
        if (widths[index] > viewportWidth || tabStart < current) return clampScroll(tabStart);
        if (tabEnd > (long) current + viewportWidth) return clampScroll(tabEnd - viewportWidth);
        return current;
    }

    /** Hit only a visible tab, never the gap or an area outside the viewport. */
    public int hitTest(int viewportX, int scroll) {
        if (viewportX < 0 || viewportX >= viewportWidth) return -1;
        for (int i = 0; i < starts.length; i++) {
            if (viewportX >= left(i, scroll) && viewportX < right(i, scroll)) return i;
        }
        return -1;
    }

    /** Keyboard movement is clamped; callers decide whether a key is a navigation key. */
    public int move(int index, int delta) {
        if (widths.length == 0) return -1;
        long target = (long) index + delta;
        return (int) Math.max(0, Math.min(widths.length - 1L, target));
    }

    private int clip(int coordinate) { return Math.max(0, Math.min(viewportWidth, coordinate)); }
}
