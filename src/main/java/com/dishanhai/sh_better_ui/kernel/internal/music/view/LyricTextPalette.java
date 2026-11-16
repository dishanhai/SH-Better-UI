// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

/** Opaque lyric ink with contrast checked against the actual local protection, not transparent RGB. */
final class LyricTextPalette {
    record Paint(int text, int active, int protection, boolean shadow) {}
    private LyricTextPalette() {}

    static Paint sample(int text, int accent, int background, float distance, boolean highContrast) {
        int protection = 0;
        int low = background | 0xff000000, high = low;
        boolean transparent = (background >>> 24) != 255;
        if (transparent || highContrast && Math.max(contrast(0xff000000, low), contrast(0xffffffff, low)) < 7) {
            // Unknown world/video pixels cannot be assigned the theme's nominal RGB luminance.
            // A local, borderless scrim protects just the lyric label while leaving the page clear.
            int rgb = luminance(text) > .35 ? 0x000000 : 0xffffff;
            protection = (highContrast ? 0xff000000 : 0xe0000000) | rgb;
            low = over(protection, 0xff000000);
            high = over(protection, 0xffffffff);
        }
        float amount = highContrast ? 0f : Math.min(.55f, .2f + Math.max(0f, Float.isFinite(distance) ? distance : 0f) * .09f);
        int muted = mix(text, protection != 0 ? protection : background, amount);
        double minimum = highContrast ? 7d : 4.5d;
        return new Paint(legible(muted, low, high, minimum), legible(accent, low, high, minimum), protection, transparent || protection != 0);
    }
    private static int legible(int ink, int low, int high, double minimum) {
        ink |= 0xff000000;
        if (contrast(ink, low) >= minimum && contrast(ink, high) >= minimum) return ink;
        int edge = Math.min(contrast(0xff000000, low), contrast(0xff000000, high))
                >= Math.min(contrast(0xffffffff, low), contrast(0xffffffff, high)) ? 0xff000000 : 0xffffffff;
        for (int step = 1; step <= 64; step++) {
            int candidate = mix(ink, edge, step / 64f);
            if (contrast(candidate, low) >= minimum && contrast(candidate, high) >= minimum) return candidate;
        }
        return edge;
    }
    private static int mix(int from, int to, float amount) {
        int result = 0xff000000;
        for (int shift : new int[]{0, 8, 16}) {
            int a = from >>> shift & 255, b = to >>> shift & 255;
            result |= Math.round(a + (b - a) * amount) << shift;
        }
        return result;
    }
    static int over(int foreground, int background) {
        return mix(background, foreground, (foreground >>> 24) / 255f);
    }
    static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);
    }
    private static double luminance(int color) {
        return channel(color >>> 16 & 255) * .2126 + channel(color >>> 8 & 255) * .7152 + channel(color & 255) * .0722;
    }
    private static double channel(int value) {
        double v = value / 255d;
        return v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4);
    }
}
