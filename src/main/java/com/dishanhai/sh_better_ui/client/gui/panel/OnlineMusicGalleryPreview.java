// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.*;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.music.*;

/** The gallery consumes the same complete UUK page available to other mods. */
public final class OnlineMusicGalleryPreview implements PreviewScene {
    public static final int HEIGHT = 360;
    private OnlineMusicView view;
    @Override public void layout(PreviewContext context) {
        if (view == null) view = UnifiedUI.get().require(OnlineMusicService.KEY).view();
        view.layout(context);
    }
    @Override public void render(UiFrame frame) { if (view != null) view.render(frame); }
    @Override public void suspend() { if (view != null) view.suspend(); }
    @Override public void close() { if (view != null) { view.close(); view = null; } }
}
