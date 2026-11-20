// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

/** The solid plate follows the ink of the consuming music view, not the global background. */
final class MusicGlassFallback {
    private MusicGlassFallback() {}
    static int fill(boolean highContrast, boolean darkInk) {
        if (highContrast) return darkInk ? 0xffffffff : 0xff000000;
        return darkInk ? 0xffe4e8ee : 0xff262b36;
    }
    static int edge(boolean highContrast, boolean darkInk) {
        if (highContrast) return darkInk ? 0xff000000 : 0xffffffff;
        return darkInk ? 0x66617491 : 0x66c6d8ef;
    }
}
