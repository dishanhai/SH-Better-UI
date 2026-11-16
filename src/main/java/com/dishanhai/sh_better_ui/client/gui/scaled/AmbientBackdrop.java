package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.BackgroundSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Original SHBUI procedural backgrounds; no third-party component or shader source. */
public final class AmbientBackdrop implements BackgroundSpec.Renderer {
    public enum Style { AURORA, SILK, IRIDESCENT }
    private final String group;
    private final Style style;
    private final AmbientBackdropProfile defaults;
    private final AmbientBackdropClock clock = new AmbientBackdropClock();

    public AmbientBackdrop(String group, Style style, AmbientBackdropProfile defaults) {
        this.group = java.util.Objects.requireNonNull(group);
        this.style = java.util.Objects.requireNonNull(style);
        this.defaults = java.util.Objects.requireNonNull(defaults);
    }

    @Override
    public void close() { clock.suspend(); }

    @Override
    public void draw(GuiGraphics g, Font font, Bounds area, UiMouseState mouse, double x, double y,
                     long now, UiPreferences prefs) {
        var profile = ThemeEffects.profile(group, defaults);
        float phase = clock.advance(now, prefs.reducedMotion() || prefs.highContrast(), profile.speed());
        if (area.width() <= 0 || area.height() <= 0 || profile.opacity() <= 0) return;
        if (prefs.highContrast()) return;
        var pose = g.pose().last().pose();
        float determinant = pose.determinant();
        // Invalid host transforms cannot be made safe by drawing a fallback with the same pose.
        if (!pose.isFinite() || !Float.isFinite(determinant) || Math.abs(determinant) < 1e-8f) return;
        if (!AmbientBackdropRenderer.draw(g, area, style, profile, phase)) {
            int color = profile.backgroundColor();
            int alpha = Math.round((color >>> 24) * profile.opacity());
            g.fill(area.x(), area.y(), area.x() + area.width(), area.y() + area.height(),
                    (alpha << 24) | (color & 0xFFFFFF));
        }
    }
}
