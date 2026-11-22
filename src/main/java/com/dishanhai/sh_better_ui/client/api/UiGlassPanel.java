// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial.Foreground;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;

import java.util.Objects;

/** Portable regular/clear backdrop panel; the host retains its native optical implementation. */
public record UiGlassPanel(Bounds bounds, float radius, GlassVariant variant, Foreground foreground) {
    public UiGlassPanel {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(foreground, "foreground");
        if (!Float.isFinite(radius) || radius < 0) throw new IllegalArgumentException("Invalid panel radius");
        radius = Math.min(radius, Math.min(bounds.width(), bounds.height()) * 0.5f);
    }

    public boolean empty() { return bounds.width() == 0 || bounds.height() == 0; }

    public int fallbackFill(UiPreferences preferences) {
        if (!preferences.highContrast()) return foreground.fallbackArgb();
        return foreground == Foreground.LIGHT ? 0xff000000 : 0xffffffff;
    }

    public int edge(UiPreferences preferences) {
        return preferences.highContrast() ? foreground.argb()
                : foreground == Foreground.LIGHT ? 0x88c0cad8 : 0x88414b5b;
    }

    /** Optical draw submitted, solid fallback emitted, or no area to paint. */
    public enum Paint { OPTICAL, SOLID, EMPTY }
}
