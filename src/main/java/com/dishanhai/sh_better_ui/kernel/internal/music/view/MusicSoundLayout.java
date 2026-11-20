// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

final class MusicSoundLayout {
    private MusicSoundLayout() {}

    static Bounds slider(Bounds panel) {
        return new Bounds(panel.x()+8, panel.y()+19, panel.width()-16, 22);
    }

    static Bounds rows(Bounds panel, int rowExtent, boolean volume) {
        // Volume feedback replaces the value above the slider. It has no footer.
        int top = volume ? slider(panel).bottom() + 4 : panel.y();
        int bottom = panel.bottom() - (volume ? 0 : 22);
        return new Bounds(panel.x(), top, panel.width(), Math.min(rowExtent, Math.max(0, bottom - top)));
    }
}
