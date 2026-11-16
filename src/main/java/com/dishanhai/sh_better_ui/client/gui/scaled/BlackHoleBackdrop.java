package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.BackgroundSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Schwarzschild optical background, without pointer input or interaction capture. */
public final class BlackHoleBackdrop implements BackgroundSpec.Renderer {
    public static final String ID = "sh_better_ui:black_hole";
    private final BlackHoleAnimationClock clock = new BlackHoleAnimationClock();

    @Override
    public void close() { clock.suspend(); }

    @Override
    public void draw(GuiGraphics g, Font font, Bounds area, UiMouseState mouse, double x, double y,
                     long now, UiPreferences prefs) {
        var profile = ThemeEffects.profile(ID, BlackHoleBackdropProfile.DEFAULT);
        float phase = clock.advance(now, prefs.reducedMotion() || prefs.highContrast(), profile.speed());
        if (area.width() <= 0 || area.height() <= 0 || profile.opacity() <= 0 || prefs.highContrast()) return;
        var pose = g.pose().last().pose();
        float determinant = pose.determinant();
        if (!pose.isFinite() || !Float.isFinite(determinant) || Math.abs(determinant) < 1e-8f) return;
        if (!BlackHoleBackdropRenderer.draw(g, area, profile, phase, this)) {
            int color = profile.backgroundColor();
            int alpha = Math.round((color >>> 24) * profile.opacity());
            g.fill(area.x(), area.y(), area.x() + area.width(), area.y() + area.height(),
                    (alpha << 24) | (color & 0xFFFFFF));
        }
    }
}
