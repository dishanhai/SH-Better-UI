// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.components.AbstractWidget;
import java.util.function.Supplier;

/** Uses the screen's shared visual and hit-test clip for every moving row. */
final class MusicScrollRows {
    private MusicScrollRows() {}
    static int spill(PreviewContext host, int capacity) {
        return host.screen() instanceof UiScreen && capacity > 0 ? 1 : 0;
    }
    static <T extends AbstractWidget> T add(PreviewContext host, T row, Supplier<Bounds> bounds) {
        return host.screen() instanceof UiScreen screen
                ? screen.addClipped(row, bounds, null) : host.add(row);
    }
    static boolean intersects(int y, int height, Bounds bounds) {
        return y < bounds.bottom() && y + height > bounds.y();
    }
}
