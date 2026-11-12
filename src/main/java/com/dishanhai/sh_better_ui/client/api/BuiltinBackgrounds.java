// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.ElectricLogo;
import com.dishanhai.sh_better_ui.client.gui.display.ElectricLogoProfile;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

final class BuiltinBackgrounds {
    private static final int ACCENT = 0xFF9ACDFF;

    private BuiltinBackgrounds() {}

    static void registerInto(BackgroundRegistry registry) {
        register(registry, "dotMatrix", logo -> new BackgroundSpec.Renderer() {
            private final UiDotMatrix dots = new UiDotMatrix();
            private final UiPointerTracker pointer = new UiPointerTracker();

            @Override
            public void draw(GuiGraphics g, Font font, Bounds area, UiMouseState mouse, double x, double y,
                             long now, UiPreferences prefs) {
                if (mouse == null) dots.clearPulse();
                dots.render(g, area, pointer.update(area, g.pose().last().pose(), mouse,
                        mouse != null && mouse.available(), now), now, prefs);
            }

            @Override
            public void click(Bounds area, double x, double y, long now) { dots.pulse(area, x, y, now); }
        });
        register(registry, "antigravity", logo -> new BackgroundSpec.Renderer() {
            private final Antigravity particles = new Antigravity();

            @Override
            public void draw(GuiGraphics g, Font font, Bounds area, UiMouseState mouse, double x, double y,
                             long now, UiPreferences prefs) { particles.draw(g, area, mouse, prefs); }

            @Override
            public void close() { particles.suspend(); }
        });
        register(registry, "lightRays", logo -> new BackgroundSpec.Renderer() {
            private final LightRays rays = new LightRays();

            @Override
            public void draw(GuiGraphics g, Font font, Bounds area, UiMouseState mouse, double x, double y,
                             long now, UiPreferences prefs) { rays.draw(g, area, mouse, prefs); }

            @Override
            public void click(Bounds area, double x, double y, long now) { rays.pulse(); }

            @Override
            public void close() { rays.suspend(); }
        });
        register(registry, "laserFlow", logo -> new BackgroundSpec.Renderer() {
            private final LaserFlow laser = new LaserFlow();

            @Override
            public void draw(GuiGraphics g, Font font, Bounds area, UiMouseState mouse, double x, double y,
                             long now, UiPreferences prefs) { laser.draw(g, area, null, null, mouse, prefs); }

            @Override
            public void close() { laser.suspend(); }
        });
        register(registry, "codeRain", logo -> rain(new CodeRain()));
        register(registry, "ascii", logo -> rain(CodeRain.ascii()));
        register(registry, "patternWaves", logo -> {
            var pattern = new PatternWaves();
            return (g, font, area, mouse, x, y, now, prefs) -> pattern.draw(g, area, mouse, prefs);
        });
        register(registry, "shapeWaves", logo -> {
            var shape = new ShapeWaves();
            return (g, font, area, mouse, x, y, now, prefs) -> shape.draw(g, area, mouse, prefs);
        });
        register(registry, "electricLogo", texture -> new BackgroundSpec.Renderer() {
            private final ElectricLogo logo = new ElectricLogo(texture);
            private final GraphicIcon icon = texture == null ? GraphicIcon.SHOP : new GraphicIcon(texture);

            @Override
            public void draw(GuiGraphics g, Font font, Bounds area, UiMouseState mouse, double x, double y,
                             long now, UiPreferences prefs) {
                var profile = ThemeEffects.profile("electricLogo", ElectricLogoProfile.DEFAULT);
                if (!logo.draw(g, area.x(), area.y(), area.width(), area.height(),
                        mouse == null ? Double.NaN : x, mouse == null ? Double.NaN : y, profile, prefs)) {
                    fallback(g, area, icon, prefs);
                }
            }

            @Override
            public void click(Bounds area, double x, double y, long now) { logo.pulse(x, y); }

            @Override
            public void close() { logo.close(); }
        });
        register(registry, "reveal", texture -> {
            var icon = texture == null ? GraphicIcon.SHOP : new GraphicIcon(texture);
            return (g, font, area, mouse, x, y, now, prefs) -> {
                int w = area.width(), h = area.height();
                VisualReveal.drawRadialGradient(g, area.x(), area.y(), w, h, 0.9f, 0.8f, 0xE03E5175, 0xE018232A);
                int size = Math.min(w, h) * 2 / 3;
                if (prefs.useBackdropBlur() && size > 0) {
                    VisualReveal.drawTexture(g, icon.texture(), area.x() + (w - size) / 2,
                            area.y() + (h - size) / 2, size, size, ThemeEffects.profile("reveal", RevealProfile.DEFAULT));
                } else {
                    fallback(g, area, icon, prefs);
                }
            };
        });
    }

    private static BackgroundSpec.Renderer rain(CodeRain rain) {
        return new BackgroundSpec.Renderer() {
            @Override
            public void draw(GuiGraphics g, Font font, Bounds area, UiMouseState mouse, double x, double y,
                             long now, UiPreferences prefs) { rain.draw(g, font, area, mouse, prefs); }

            @Override
            public void close() { rain.suspend(); }
        };
    }

    private static void fallback(GuiGraphics g, Bounds area, GraphicIcon icon, UiPreferences prefs) {
        icon.draw(g, area.x() + area.width() / 2 - 8, area.y() + area.height() / 2 - 8, 1, ACCENT, prefs);
    }

    private static void register(BackgroundRegistry registry, String id, BackgroundSpec.Factory factory) {
        registry.register(new BackgroundSpec(id, "sh_better_ui.panel.theme.group." + id, id, null, factory));
    }
}
