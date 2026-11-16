// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Editable native fluid material. ARGB alpha is retained even when RGB follows the active theme. */
public record MuoniumFluidProfile(float speed, float scale, float intensity, float opacity,
                                  int primaryColor, int secondaryColor, int backgroundColor,
                                  boolean followTheme) {
    public static final MuoniumFluidProfile DEFAULT = new MuoniumFluidProfile(
            .28f, 1f, .8f, .8f, 0xB064A8FF, 0x907B9CFF, 0x40182331, true);

    public MuoniumFluidProfile {
        var normalized = new AmbientBackdropProfile(speed, scale, intensity, opacity,
                primaryColor, secondaryColor, backgroundColor);
        speed = normalized.speed(); scale = normalized.scale();
        intensity = normalized.intensity(); opacity = normalized.opacity();
    }

    /** Keep editor opacity meaningful when theme colors are opaque. No texture or cover is sampled. */
    AmbientBackdropProfile resolve(int accent, int secondary, int background) {
        return new AmbientBackdropProfile(speed, scale, intensity, opacity,
                followTheme ? tint(primaryColor, accent) : primaryColor,
                followTheme ? tint(secondaryColor, secondary) : secondaryColor,
                followTheme ? tint(backgroundColor, background) : backgroundColor);
    }
    private static int tint(int original, int rgb) { return (original & 0xff000000) | (rgb & 0xffffff); }
}
