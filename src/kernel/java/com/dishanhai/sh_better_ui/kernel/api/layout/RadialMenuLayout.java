// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

import java.util.ArrayList;
import java.util.List;
import com.dishanhai.sh_better_ui.kernel.api.layout.SettingsPanelLayout.Rect;

/** Six stable positions around a clear center; very small viewports use two columns. */
public record RadialMenuLayout(List<Rect> slots, Rect center, boolean compact) {
    public RadialMenuLayout { slots = List.copyOf(slots); }

    public static RadialMenuLayout of(int width, int height, int count) {
        if (width < 80 || height < 64 || count < 0 || count > 6)
            throw new IllegalArgumentException("Invalid radial menu viewport or action count");
        boolean compact = width < 240 || height < 132;
        var slots = new ArrayList<Rect>();
        if (compact) {
            int rowHeight = Math.min(40, (height - 8) / 3);
            int cellWidth = (width - 18) / 2;
            for (int i = 0; i < count; i++)
                slots.add(new Rect(6 + i % 2 * (cellWidth + 6), 4 + i / 2 * rowHeight, cellWidth, rowHeight - 4));
            return new RadialMenuLayout(slots, new Rect(width / 2, height / 2, 0, 0), true);
        }
        int tileHeight = height >= 230 ? 42 : height >= 168 ? 34 : 28;
        int outerY = height / 2 - 8 - tileHeight / 2;
        int diameter = Math.min(112, Math.min(width / 3, 2 * (outerY - tileHeight / 2 - 8)));
        int tileWidth = Math.min(146, (width - diameter - 40) / 2);
        int sideY = tileHeight / 2 + 5;
        if (outerY < sideY + tileHeight + 4) tileWidth = Math.min(tileWidth, diameter + 20);
        int sideX = (tileWidth + diameter) / 2 + 12;
        int cx = width / 2, cy = height / 2;
        int[][] positions = {{0, -outerY}, {sideX, -sideY}, {sideX, sideY},
                {0, outerY}, {-sideX, sideY}, {-sideX, -sideY}};
        for (int i = 0; i < count; i++) slots.add(new Rect(cx + positions[i][0] - tileWidth / 2,
                cy + positions[i][1] - tileHeight / 2, tileWidth, tileHeight));
        return new RadialMenuLayout(slots, new Rect(cx - diameter / 2, cy - diameter / 2, diameter, diameter), false);
    }
}
