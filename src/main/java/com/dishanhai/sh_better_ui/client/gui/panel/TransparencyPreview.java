// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphics;

/** Bounded contrasting backdrop for inspecting actual alpha, not a replacement background asset. */
final class TransparencyPreview {
    private TransparencyPreview() {}
    static void draw(GuiGraphics g, Bounds area) {
        if (area.width() <= 0 || area.height() <= 0) return;
        for (int row = 0; row < 6; row++) for (int column = 0; column < 12; column++) {
            int x = area.x() + (int)((long) area.width() * column / 12);
            int y = area.y() + (int)((long) area.height() * row / 6);
            int right = area.x() + (int)((long) area.width() * (column + 1) / 12);
            int bottom = area.y() + (int)((long) area.height() * (row + 1) / 6);
            if (right > x && bottom > y)
                g.fill(x, y, right, bottom, (row + column) % 2 == 0 ? 0xff263d56 : 0xff89705d);
        }
    }
}
