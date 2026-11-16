// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry for a setting card. Title is never itself an activation target. */
public record SettingCardLayout(Rect text, Rect primary, Rect disclosure, List<Rect> details,
                                int width, int height, boolean stacked) {
    public static final int MIN_WIDTH = 180, WIDE_WIDTH = 360, PADDING = 10, GAP = 8, ARROW = 24;
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
    }
    public SettingCardLayout { details = List.copyOf(details); }
    public static int textWidth(int width) {
        if (width < MIN_WIDTH) throw new IllegalArgumentException("Setting card requires width >= 180");
        return width < WIDE_WIDTH ? width - 2 * PADDING
                : width - 2 * PADDING - controlWidth(width) - ARROW - 2 * GAP;
    }
    private static int controlWidth(int width) { return Math.min(180, Math.max(100, width / 3)); }
    public static SettingCardLayout measure(int width, int textHeight, int primaryHeight, List<Integer> detailHeights) {
        int textWidth = textWidth(width);
        if (textHeight < 0 || primaryHeight < 1) throw new IllegalArgumentException("Invalid card content dimensions");
        boolean stacked = width < WIDE_WIDTH;
        int rowHeight = Math.max(ARROW, primaryHeight);
        int rowY = PADDING + (stacked ? textHeight + GAP : Math.max(0, (textHeight - rowHeight) / 2));
        int pw = stacked ? width - 2 * PADDING - ARROW - GAP : controlWidth(width);
        int px = width - PADDING - ARROW - GAP - pw;
        Rect primary = new Rect(px, rowY + (rowHeight - primaryHeight) / 2, pw, primaryHeight);
        Rect arrow = new Rect(width - PADDING - ARROW, rowY + (rowHeight - ARROW) / 2, ARROW, ARROW);
        int headerBottom = Math.max(PADDING + textHeight, rowY + rowHeight);
        int cursor = headerBottom;
        var details = new ArrayList<Rect>();
        for (int height : detailHeights) {
            if (height < 1) throw new IllegalArgumentException("Invalid detail height");
            cursor += GAP;
            details.add(new Rect(PADDING, cursor, width - 2 * PADDING, height));
            cursor += height;
        }
        return new SettingCardLayout(new Rect(PADDING, PADDING, textWidth, textHeight), primary, arrow,
                details, width, cursor + PADDING, stacked);
    }
}
