// Author: dishanhai
// Original native implementation inspired by MuoniumPlayer's layered fluid lyric backgrounds.
// Reference revision: 9b4cc7e7bd143315da716075a6d024a0d4c0c81b.
// No upstream QPlayer/AMLL/Folia shader, Skija code, artwork or font is copied.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.BackgroundSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** One texture-free native pass; transparent compositing preserves the world behind the host. */
public final class MuoniumFluidBackdrop implements BackgroundSpec.Renderer {
    public static final String ID = "sh_better_ui:muonium_fluid";
    private final AmbientBackdropClock clock = new AmbientBackdropClock();

    @Override public void close() { clock.suspend(); }

    @Override public void draw(GuiGraphics g, Font font, Bounds area, UiMouseState mouse, double x, double y,
                               long now, UiPreferences preferences) {
        var settings = ThemeEffects.profile(ID, MuoniumFluidProfile.DEFAULT);
        float phase = clock.advance(now, preferences.reducedMotion() || preferences.highContrast(), settings.speed());
        if (preferences.highContrast() || area.width() <= 0 || area.height() <= 0 || settings.opacity() <= 0) return;
        var pose = g.pose().last().pose();
        float determinant = pose.determinant();
        if (!pose.isFinite() || !Float.isFinite(determinant) || Math.abs(determinant) < 1e-8f) return;
        var profile = settings.resolve(DarkModeTheme.accentColor(), DarkModeTheme.secondaryTextColor(),
                DarkModeTheme.backgroundColor());
        if (!AmbientBackdropRenderer.draw(g, area, AmbientBackdrop.Style.FLUID, profile, phase)) {
            // Missing/failed shader: predictable low-cost translucent tint, no repeated compilation.
            int color = profile.backgroundColor();
            int alpha = Math.round((color >>> 24) * profile.opacity());
            g.fill(area.x(), area.y(), area.x() + area.width(), area.y() + area.height(),
                    (alpha << 24) | (color & 0xffffff));
        }
    }
}
