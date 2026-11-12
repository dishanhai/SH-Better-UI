// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.glass;

import java.util.Objects;

import static com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics.mix;

/**
 * A surface and optional local highlight. Light coordinates run from -1 to 1, with Y increasing downwards.
 * Snell applies foreground contrast to the supplied bounds. High contrast applies to the whole surface.
 */
public record GlassDraw(GlassRegion region, GlassMaterial material, float lightX, float lightY, float lightStrength,
                        Profile profile, ForegroundBounds foregroundBounds) {
    public GlassDraw {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(profile, "profile");
        if (!Float.isFinite(lightX) || Math.abs(lightX) > 1 || !Float.isFinite(lightY) || Math.abs(lightY) > 1
                || !Float.isFinite(lightStrength) || lightStrength < 0 || lightStrength > 1) {
            throw new IllegalArgumentException("Invalid Liquid Glass highlight");
        }
    }

    public GlassDraw(GlassRegion region, GlassMaterial material, float lightX, float lightY, float lightStrength) {
        this(region, material, lightX, lightY, lightStrength, Profile.LEGACY, null);
    }

    public GlassDraw(GlassRegion region, GlassMaterial material) {
        this(region, material, 0, 0, 0);
    }

    public GlassDraw(GlassRegion region, GlassMaterial material, Profile profile, ForegroundBounds foregroundBounds) {
        this(region, material, 0, 0, 0, profile, foregroundBounds);
    }

    public enum Profile { LEGACY, SNELL }

    /** Optional text footprint in the same GUI coordinate space as the surface, including any glyph padding. */
    public record ForegroundBounds(float x, float y, float width, float height) {
        public ForegroundBounds {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !(width > 0) || !(height > 0)
                    || !Float.isFinite(x + width) || !Float.isFinite(y + height)) {
                throw new IllegalArgumentException("Invalid Liquid Glass foreground bounds");
            }
        }

        private ForegroundBounds interpolate(ForegroundBounds target, float fraction) {
            return new ForegroundBounds(mix(x, target.x, fraction), mix(y, target.y, fraction),
                    mix(width, target.width, fraction), mix(height, target.height, fraction));
        }
    }

    /** Uses one progress value for the visible region, optics, local highlight and foreground bounds. */
    public GlassDraw interpolate(GlassDraw target, float fraction) {
        Objects.requireNonNull(target, "target");
        if (material.variant() != target.material.variant() || material.quality() != target.material.quality()
                || material.foreground() != target.material.foreground() || profile != target.profile
                || (foregroundBounds == null) != (target.foregroundBounds == null)) {
            throw new IllegalArgumentException("Liquid Glass interpolation requires the same material semantics");
        }
        var geometry = region.interpolate(target.region, fraction);
        if (fraction == 0) return this;
        if (fraction == 1) return target;
        var optics = material.optics().interpolate(target.material.optics(), fraction);
        var foreground = foregroundBounds == null ? null : foregroundBounds.interpolate(target.foregroundBounds, fraction);
        return new GlassDraw(geometry, new GlassMaterial(material.variant(), optics, material.quality(), material.foreground()),
                mix(lightX, target.lightX, fraction), mix(lightY, target.lightY, fraction),
                mix(lightStrength, target.lightStrength, fraction), profile, foreground);
    }
}
