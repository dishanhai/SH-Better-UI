// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.api.UiFrame;

/** A soft, downward reveal drawn over the destination only. No resampling, blur or input transform. */
final class MusicRouteReveal {
    private MusicRouteReveal() {}

    record Edge(int top, int bottom) {}

    static Edge edge(Bounds box, float remaining) {
        int feather = Math.min(24, box.height() / 3);
        int bottom = box.bottom();
        int top = Math.round(bottom - (box.height() + feather) * remaining);
        return new Edge(Math.max(box.y(), top), Math.min(bottom, Math.max(box.y(), top + feather)));
    }

    static void paint(UiFrame ui, Bounds box, int color, int opacity, float remaining) {
        if (opacity <= 0 || remaining <= 0f || box.width() <= 0 || box.height() <= 0) return;
        var edge = edge(box, remaining);
        int rgb = color & 0x00ffffff;
        int tint = rgb | (opacity << 24);
        if (edge.top() < edge.bottom()) {
            ui.graphics().fillGradient(box.x(), edge.top(), box.right(), edge.bottom(), rgb, tint);
        }
        if (edge.bottom() < box.bottom()) {
            ui.graphics().fill(box.x(), edge.bottom(), box.right(), box.bottom(), tint);
        }
    }
}
