package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Original shard-field settings; all colors are ARGB. */
public record CrystalDriftProfile(int count, float size, float speed, float spread, float opacity,
                                  float glow, float parallax, Placement placement, Material material,
                                  int color, int accentColor, int backgroundColor,
                                  boolean mouseInteraction, boolean pulseOnClick, boolean holdToGather) {
    public enum Placement { FULL, LEFT, RIGHT, CENTER }
    public enum Material { GLASS, CHROME }

    public static final CrystalDriftProfile DEFAULT = new CrystalDriftProfile(96, 1f, .4f, 1f, 1f,
            .35f, .35f, Placement.FULL, Material.GLASS, 0xFF91C9EB, 0xFFC5A4EC, 0xFF101825,
            true, true, true);

    public CrystalDriftProfile {
        count = Math.max(16, Math.min(320, count));
        size = range(size, .25f, 3f);
        speed = range(speed, 0f, 2f);
        spread = range(spread, .2f, 2f);
        opacity = range(opacity, 0f, 1f);
        glow = range(glow, 0f, 1f);
        parallax = range(parallax, 0f, 1f);
        java.util.Objects.requireNonNull(placement, "placement");
        java.util.Objects.requireNonNull(material, "material");
    }

    private static float range(float value, float min, float max) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite crystal drift parameter");
        return Math.max(min, Math.min(max, value));
    }
}
