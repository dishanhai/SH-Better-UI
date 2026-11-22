// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.config.ThemeSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;

/** Bounded native screen backgrounds. No remote textures, shaders, workers or GPU lifetime are owned. */
public final class NativeAppearanceBackground {
    public static final String BACKDROP = "nativeBackdrop";
    private static final String LEGACY_PLAIN = "sh_better_ui:plain";
    public static final String SOLID = "nativeSolid", GRADIENT = "nativeGradient", DOTS = "dotMatrix";
    private static final List<String> IDS = List.of(BACKDROP, SOLID, GRADIENT, DOTS);
    private NativeAppearanceBackground() {}
    public static List<String> supportedIds() { return IDS; }
    public static boolean supported(String id) { return id != null && (LEGACY_PLAIN.equals(id) || IDS.contains(id)); }
    public static boolean isBackdrop(String id) { return BACKDROP.equals(id) || LEGACY_PLAIN.equals(id); }
    public record Sample(String effect, int base, int secondary, int accent, double phase, int spacing, int countLimit) {}
    /** The sampling contract is also used by tests: reduced motion/high contrast never depend on wall time. */
    public static Sample sample(int width, int height, ThemeSettings settings, UiPreferences preferences, long nowMs) {
        var theme = UiTheme.resolve(settings);
        if (isBackdrop(settings.backgroundEffect())) return new Sample(BACKDROP, theme.background(), theme.background(), theme.accent(), 0, 32, 0);
        if (preferences.highContrast()) return new Sample(SOLID, theme.light() ? 0xffffffff : 0xff000000,
                theme.light() ? 0xffffffff : 0xff000000, theme.light() ? 0xff000000 : 0xffffffff, 0, 32, 0);
        String effect = supported(settings.backgroundEffect()) ? settings.backgroundEffect() : SOLID;
        int areaSpacing = (int) Math.ceil(Math.sqrt(Math.max(0d, width) * Math.max(0d, height) / 800d));
        int spacing = Math.max(28, areaSpacing);
        double phase = preferences.reducedMotion() ? 0 : Math.floorMod(nowMs, 12000L) / 12000d * Math.PI * 2;
        return new Sample(effect, theme.background() | 0xff000000,
                GuiMotion.lerpColor(theme.background(), theme.accent(), theme.light() ? .12f : .18f) | 0xff000000,
                theme.accent(), phase, spacing, 1024);
    }
    public static void draw(GuiGraphicsExtractor graphics, int width, int height, ThemeSettings settings,
                            UiPreferences preferences, long nowMs) {
        if (width <= 0 || height <= 0) return;
        var state = sample(width,height,settings,preferences,nowMs);
        if (state.effect().equals(BACKDROP)) return;
        if (state.effect().equals(GRADIENT)) graphics.fillGradient(0,0,width,height,state.base(),state.secondary());
        else graphics.fill(0,0,width,height,state.base());
        if (!state.effect().equals(DOTS)) return;
        int drawn = 0;
        for (int y = state.spacing()/2; y < height; y += state.spacing()) {
            for (int x = state.spacing()/2; x < width && drawn < state.countLimit(); x += state.spacing()) {
                double wave = .5 + .5 * Math.sin(state.phase() + x*.012 + y*.009);
                int color = GuiMotion.lerpColor(state.base(), state.accent(), (float)(.12 + wave*.16));
                RoundedCorners.draw(graphics,x-1,y-1,2,2,1,0,0,color | 0xff000000); drawn++;
            }
            if (drawn >= state.countLimit()) break;
        }
    }
}
