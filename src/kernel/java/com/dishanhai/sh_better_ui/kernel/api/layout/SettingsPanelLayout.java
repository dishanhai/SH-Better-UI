// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

/** Responsive settings chrome, leaving native widgets and data ownership to the host. */
public record SettingsPanelLayout(Rect panel, Rect navigation, Rect content, Rect footer,
                                  boolean sidebar, int rowHeight, int capacity) {
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean inside(int width, int height) {
            return x >= 0 && y >= 0 && this.width >= 0 && this.height >= 0 && right() <= width && bottom() <= height;
        }
        public boolean overlaps(Rect other) {
            return width > 0 && height > 0 && other.width > 0 && other.height > 0
                    && x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }
    }

    public static SettingsPanelLayout of(int width, int height, int groups) {
        if (width < 180 || height < 180 || groups < 1 || groups > 12)
            throw new IllegalArgumentException("Settings viewport must be at least 180 x 180 with 1..12 groups");
        int w = Math.min(600, width - 16), h = Math.min(404, height - 16);
        int x = (width - w) / 2, y = (height - h) / 2;
        boolean sidebar = w >= 420 && h >= 76 + groups * 24;
        var panel = new Rect(x, y, w, h);
        var footer = new Rect(x + 12, y + h - 30, w - 24, 22);
        var navigation = sidebar ? new Rect(x + 12, y + 44, 102, groups * 24)
                : new Rect(x + 12, y + 40, w - 24, 22);
        int contentX = sidebar ? x + 130 : x + 12;
        int contentY = y + (sidebar ? 44 : 70);
        var content = new Rect(contentX, contentY, x + w - 12 - contentX, footer.y - 8 - contentY);
        int row = h >= 260 ? 44 : 36;
        int capacity = Math.max(1, (content.height - 22) / row);
        if (content.height < 22 + row) row = Math.max(14, content.height - 22);
        return new SettingsPanelLayout(panel, navigation, content, footer, sidebar, row, capacity);
    }

    public Rect row(int index) {
        if (index < 0 || index >= capacity) throw new IndexOutOfBoundsException(index);
        return new Rect(content.x, content.y + 22 + index * rowHeight, content.width, rowHeight);
    }
}
