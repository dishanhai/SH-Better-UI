package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Original stylized black-hole background. Tilt is radians; colors use ARGB. */
public record BlackHoleBackdropProfile(float speed, float size, float tilt, float diskWidth, float lensing,
                                       float brightness, float glow, float opacity, int color, int backgroundColor) {
    public static final BlackHoleBackdropProfile DEFAULT = new BlackHoleBackdropProfile(
            .18f, 1f, .08f, .16f, 1f, 1f, .55f, 1f, 0xFFF5F5F5, 0xFF080808);

    public BlackHoleBackdropProfile {
        speed = range(speed, 0f, 2f);
        size = range(size, .5f, 1.8f);
        tilt = range(tilt, -.7f, .7f);
        diskWidth = range(diskWidth, .05f, .35f);
        lensing = range(lensing, 0f, 1.5f);
        brightness = range(brightness, 0f, 2f);
        glow = range(glow, 0f, 1f);
        opacity = range(opacity, 0f, 1f);
    }

    private static float range(float value, float min, float max) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite black-hole parameter");
        return Math.max(min, Math.min(max, value));
    }
}
