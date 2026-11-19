// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import net.minecraft.network.chat.Component;

/** UI-owned, bounded acknowledgement. Never implies remote success or performs playback work. */
public final class OnlineMusicFeedback {
    private Component text = Component.empty();
    private long expires;
    private boolean error;
    public void show(Component text, long nowMs) { show(text, nowMs, false); }
    public void show(Component text, long nowMs, boolean error) {
        this.error = error;
        this.text = text.copy();
        expires = nowMs > Long.MAX_VALUE - 4500 ? Long.MAX_VALUE : nowMs + 4500;
    }
    public Component current(long nowMs) { return nowMs < expires ? text : Component.empty(); }
    public boolean isError(long nowMs) { return nowMs < expires && error; }
    public void clear() { text = Component.empty(); expires = 0; }
}
