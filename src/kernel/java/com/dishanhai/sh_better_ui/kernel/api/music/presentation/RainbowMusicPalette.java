// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.presentation;

/** Original looping gradient shared by the native and embedded music renderers. */
public final class RainbowMusicPalette {
    public static ArtworkPalette sample(long nowMs, boolean still, boolean darkInk) {
        return MusicGradient.RAINBOW.sample(nowMs, still, darkInk);
    }

    private RainbowMusicPalette() {}
}
