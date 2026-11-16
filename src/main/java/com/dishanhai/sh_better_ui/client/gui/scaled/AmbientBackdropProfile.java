package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Original ambient backgrounds. Colors are ARGB; speed is radians per second. */
public record AmbientBackdropProfile(float speed, float scale, float intensity, float opacity,
                                     int primaryColor, int secondaryColor, int backgroundColor) {
    public static final AmbientBackdropProfile AURORA = new AmbientBackdropProfile(
            .25f, 1f, .65f, 1f, 0xFF70E8C4, 0xFF8575D9, 0xFF101B2B);
    public static final AmbientBackdropProfile SILK = new AmbientBackdropProfile(
            .25f, 1f, .65f, 1f, 0xFFC0A8D8, 0xFF687EB2, 0xFF191824);
    public static final AmbientBackdropProfile IRIDESCENT = new AmbientBackdropProfile(
            .25f, 1f, .65f, 1f, 0xFF78CAD0, 0xFFE3A2CA, 0xFF171E2C);

    public static final AmbientBackdropProfile CONTOUR = new AmbientBackdropProfile(
            .25f, 1f, .65f, 1f, 0xFF83C6C0, 0xFF7F9CBC, 0xFF131E27);
    public static final AmbientBackdropProfile MIST = new AmbientBackdropProfile(
            .25f, 1f, .65f, 1f, 0xFFA1B6D9, 0xFFD0AFC6, 0xFF1B1E2B);

    public AmbientBackdropProfile {
        speed = range(speed, 0f, 2f);
        scale = range(scale, .25f, 4f);
        intensity = range(intensity, 0f, 1f);
        opacity = range(opacity, 0f, 1f);
    }

    private static float range(float value, float min, float max) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite ambient backdrop parameter");
        return Math.max(min, Math.min(max, value));
    }
}
