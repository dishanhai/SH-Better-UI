// Author: dishanhai
// Original native implementation inspired by MuoniumPlayer's layered fluid lyric backgrounds.
// Reference revision: 9b4cc7e7bd143315da716075a6d024a0d4c0c81b.
// No upstream QPlayer/AMLL/Folia shader, Skija code, artwork or font is copied.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.BackgroundSpec;
import com.dishanhai.sh_better_ui.client.music.ClientMusic;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** One texture-free native pass; transparent compositing preserves the world behind the host. */
public final class MuoniumFluidBackdrop implements BackgroundSpec.Renderer {
    public static final String ID = "sh_better_ui:muonium_fluid";
    private final AmbientBackdropClock clock = new AmbientBackdropClock();

    private final MuoniumPaletteTransition colors = new MuoniumPaletteTransition();
    private final MuoniumCoverPalette artwork = new MuoniumCoverPalette();

    @Override public void close() { clock.suspend(); colors.reset(); artwork.close(); }

    @Override public void draw(GuiGraphics g, Font font, Bounds area, UiMouseState mouse, double x, double y,
                               long now, UiPreferences preferences) {
        var settings = ThemeEffects.profile(ID, MuoniumFluidProfile.DEFAULT);
        float phase = clock.advance(now, preferences.reducedMotion() || preferences.highContrast(), settings.speed());
        if (preferences.highContrast() || area.width() <= 0 || area.height() <= 0 || settings.opacity() <= 0) {
            artwork.close(); colors.reset(); return;
        }
        var pose = g.pose().last().pose();
        float determinant = pose.determinant();
        if (!pose.isFinite() || !Float.isFinite(determinant) || Math.abs(determinant) < 1e-8f) return;
        var profile = settings.resolve(DarkModeTheme.accentColor(), DarkModeTheme.secondaryTextColor(),
                DarkModeTheme.backgroundColor());
        if (settings.followArtwork()) {
            var session = ClientMusic.frame().snapshot().session();
            var palette = artwork.sample(session == null ? null : session.artwork());
            if (palette != null) profile = new AmbientBackdropProfile(profile.speed(), profile.scale(),
                    profile.intensity(), profile.opacity(),
                    (profile.primaryColor() & 0xff000000) | (palette.primary().argb() & 0xffffff),
                    (profile.secondaryColor() & 0xff000000) | (palette.secondary().argb() & 0xffffff),
                    (profile.backgroundColor() & 0xff000000) | (palette.background().argb() & 0xffffff));
        } else artwork.close();
        profile = colors.sample(profile, now, preferences.reducedMotion());
        if (!AmbientBackdropRenderer.draw(g, area, AmbientBackdrop.Style.FLUID, profile, phase)) {
            // Missing/failed shader: predictable low-cost translucent tint, no repeated compilation.
            int color = profile.backgroundColor();
            int alpha = Math.round((color >>> 24) * profile.opacity());
            g.fill(area.x(), area.y(), area.x() + area.width(), area.y() + area.height(),
                    (alpha << 24) | (color & 0xffffff));
        }
    }
}
