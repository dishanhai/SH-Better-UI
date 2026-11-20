// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

/** Default-player layout only; does not change the public page geometry contract. */
final class DefaultMusicChromeLayout {
    private DefaultMusicChromeLayout() {}

    static Bounds sidebarRow(Bounds rail, int slot) {
        int pitch = Math.min(30, (rail.height() - 40) / 6);
        int height = Math.min(28, pitch - 2);
        int top = slot < 4 ? rail.y() + 34 + slot * pitch
                : rail.bottom() - 6 - height - (5 - slot) * pitch;
        return new Bounds(rail.x()+6,top,rail.width()-12,height);
    }

    static Bounds elapsed(Bounds transport,Bounds seek) {
        return new Bounds(transport.x()+8,seek.y()+4,seek.x()-transport.x()-12,12);
    }

    static Bounds remaining(Bounds transport,Bounds seek,int labelWidth) {
        int right=transport.right()-8;
        int width=Math.min(right-seek.right()-4,labelWidth);
        return new Bounds(right-width,seek.y()+4,width,12);
    }
}
