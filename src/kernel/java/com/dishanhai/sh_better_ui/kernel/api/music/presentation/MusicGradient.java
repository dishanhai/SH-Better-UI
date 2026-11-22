// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.presentation;

/** Looping backdrop palettes for the music player. The caller supplies the frame clock. */
public enum MusicGradient {
    RAINBOW(36_000, 0xff194e37, 0xff124c57, 0xff182851, 0xff42295d, 0xff622c4c, 0xff674623),
    AURORA(32_000, 0xff125248, 0xff16434f, 0xff192e5b, 0xff3b2b63, 0xff1d3f58),
    AFTERGLOW(44_000, 0xff723c26, 0xff71323f, 0xff442346, 0xff342649, 0xff5f384d),
    MOONLIT_TIDE(52_000, 0xff215767, 0xff1d486e, 0xff202e5c, 0xff343c61, 0xff265064);

    private final long periodMs;
    private final int[] colors;

    MusicGradient(long periodMs, int... colors) {
        this.periodMs = periodMs;
        this.colors = colors;
    }

    public ArtworkPalette sample(long nowMs, boolean still, boolean darkInk) {
        double phase = still ? 0 : Math.floorMod(nowMs, periodMs) / (double) periodMs;
        var palette = new ArtworkPalette(color(phase), color(phase + .64),
                color(phase + .86), color(phase + .45));
        return darkInk ? palette.interpolate(ArtworkPalette.solid(0xffffffff), .78) : palette;
    }

    private int color(double phase) {
        double position = (phase - Math.floor(phase)) * colors.length;
        int left = (int) position;
        double progress = position - left;
        double eased = progress * progress * (3 - 2 * progress);
        int a = colors[left], b = colors[(left + 1) % colors.length];
        int r = blend(a >> 16, b >> 16, eased);
        int g = blend(a >> 8, b >> 8, eased);
        return 0xff000000 | (r << 16) | (g << 8) | blend(a, b, eased);
    }

    private static int blend(int a, int b, double weight) {
        return (int) Math.round((a & 255) * (1 - weight) + (b & 255) * weight);
    }
}
