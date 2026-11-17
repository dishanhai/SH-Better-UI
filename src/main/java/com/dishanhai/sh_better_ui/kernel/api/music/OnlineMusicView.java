// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;

/** Mountable player page with navigation, search, lyrics, accounts and a persistent transport bar. */
public interface OnlineMusicView extends PreviewScene {
    /** Session exposed by this view. Independent views own it; background views borrow the kernel-owned player. */
    OnlineMusicPlayer player();
}
