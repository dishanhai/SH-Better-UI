package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Schwarzschild background settings. Inclination and tilt are radians; radii are in mass units (M).
 * Legacy diskWidth/lensing remain readable for existing configurations but no longer alter physics. */
public record BlackHoleBackdropProfile(float speed, float size, float tilt, float diskWidth, float lensing,
                                       float brightness, float glow, float opacity, int color, int backgroundColor,
                                       float inclination, float outline, Quality quality, float diskOuterRadius) {
    public enum Quality {
        LOW(192, .04f, 384), BALANCED(320, .025f, 1536), HIGH(512, .016f, 2048);
        private final int steps, resolution;
        private final float stepSize;
        Quality(int steps, float stepSize, int resolution) {
            this.steps = steps; this.stepSize = stepSize; this.resolution = resolution;
        }
        public int steps() { return steps; }
        public float stepSize() { return stepSize; }
        public int resolution() { return resolution; }
    }

    /** Source-compatible constructor for the earlier decorative implementation. */
    public BlackHoleBackdropProfile(float speed, float size, float tilt, float diskWidth, float lensing,
                                    float brightness, float glow, float opacity, int color, int backgroundColor) {
        this(speed, size, tilt, diskWidth, lensing, brightness, glow, opacity, color, backgroundColor,
                .25f, 0f, Quality.BALANCED, 18f);
    }

    /** @deprecated Retained for old configuration compatibility; ignored by the physical renderer. */
    @Deprecated public float diskWidth() { return diskWidth; }
    /** @deprecated Retained for old configuration compatibility; ignored by the physical renderer. */
    @Deprecated public float lensing() { return lensing; }

    public static final BlackHoleBackdropProfile DEFAULT = new BlackHoleBackdropProfile(
            .18f, 1f, .08f, .16f, 1f, 1f, 1.2f, 1f, 0xFFF5F5F5, 0xFF080808);

    public BlackHoleBackdropProfile {
        speed = range(speed, 0f, 2f);
        size = range(size, .5f, 1.8f);
        tilt = range(tilt, -.7f, .7f);
        diskWidth = range(diskWidth, .05f, .35f);
        lensing = range(lensing, 0f, 1.5f);
        brightness = range(brightness, 0f, 2f);
        glow = range(glow, 0f, 3f);
        opacity = range(opacity, 0f, 1f);
        inclination = range(inclination, .1f, 1.4f);
        outline = range(outline, 0f, 1f);
        quality = java.util.Objects.requireNonNull(quality, "quality");
        diskOuterRadius = range(diskOuterRadius, 6.5f, 24f);
    }

    private static float range(float value, float min, float max) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite black-hole parameter");
        return Math.max(min, Math.min(max, value));
    }
}
