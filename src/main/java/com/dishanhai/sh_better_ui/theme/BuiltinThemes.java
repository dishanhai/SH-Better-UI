// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.dishanhai.sh_better_ui.theme.token.TokenRegistry;

import java.util.List;
import java.util.Set;

import static com.dishanhai.sh_better_ui.theme.token.ThemeTokens.*;

/** Built-in, resource-independent themes used even when a pack fails to load. */
public final class BuiltinThemes {
    public static final String DARK = "sh_better_ui:dark";
    public static final String LIGHT_ID = "sh_better_ui:light";
    public static final String NETHER = "sh_better_ui:nether";
    public static final String END = "sh_better_ui:end";
    public static final String LIQUID_GLASS = "sh_better_ui:liquid_glass";
    public static final String HUD_GLASS = "sh_better_ui:hud_glass";
    public static final int DEFAULT_HUD_BLUR_RADIUS = 4;
    static final Set<String> IDS = Set.of(DARK, LIGHT_ID, NETHER, END, LIQUID_GLASS, HUD_GLASS);

    private BuiltinThemes() {}

    /** Resolves the built-in preset for previews and clients without a running theme manager. */
    public static ThemeSnapshot liquidGlassSnapshot() { return GlassPresets.LIQUID; }

    /** Resolves the clear HUD-style preset for client previews. */
    public static ThemeSnapshot hudGlassSnapshot() { return GlassPresets.HUD; }

    private static final class GlassPresets {
        private static final ThemeSnapshot LIQUID = resolve(LIQUID_GLASS);
        private static final ThemeSnapshot HUD = resolve(HUD_GLASS);

        private static ThemeSnapshot resolve(String id) {
            var tokens = new TokenRegistry();
            ThemeTokens.all().forEach(tokens::register);
            return new ThemeResolver().resolve(registry(), tokens.snapshot(), List.of(id), null);
        }
    }

    static ThemeRegistry registry() {
        return new ThemeRegistry()
                .with(theme(DARK).build())
                .with(theme(LIGHT_ID).parent(DARK).token(LIGHT, true)
                        .token(BACKGROUND, color(0xffe8eef5)).token(SURFACE, color(0xfff8fafc))
                        .token(SURFACE_SECONDARY, color(0xffe0e8f3)).token(SURFACE_ELEVATED, color(0xffffffff))
                        .token(SURFACE_OVERLAY, color(0xfff8fafc)).token(TEXT, color(0xff172334))
                        .token(TEXT_SECONDARY, color(0xff38495e)).token(TEXT_DISABLED, color(0xff52637a))
                        .token(BORDER, color(0xff64748b)).token(BORDER_FOCUSED, color(0xff1755aa))
                        .token(PRIMARY, color(0xff1755aa)).token(ON_PRIMARY, color(0xffffffff))
                        .token(ACCENT, color(0xff1755aa)).token(HOVER, color(0x181755aa))
                        .token(PRESSED, color(0x321755aa)).build())
                .with(theme(LIQUID_GLASS).parent(DARK).token(ThemeTokens.LIQUID_GLASS, true)
                        .token(BACKGROUND, color(0xff151c26)).token(SURFACE, color(0xcc252b34))
                        .token(SURFACE_SECONDARY, color(0xd0303946)).token(SURFACE_ELEVATED, color(0xe63a4655))
                        .token(SURFACE_OVERLAY, color(0xe6252b34)).token(TEXT, color(0xfff5f7fa))
                        .token(TEXT_SECONDARY, color(0xffc7d1df)).token(TEXT_DISABLED, color(0xffa3adbc))
                        .token(BORDER, color(0xa6cbd4e3)).token(BORDER_FOCUSED, color(0xffbcdfff))
                        .token(PRIMARY, color(0xff9acdff)).token(ACCENT, color(0xff9acdff))
                        .token(SELECTED, color(0x609acdff)).token(RADIUS_LARGE, 12f).token(BLUR, 12f).build())
                .with(theme(HUD_GLASS).parent(LIQUID_GLASS).token(ThemeTokens.HUD_GLASS, true)
                        .token(SURFACE, color(0x88252b34)).token(SURFACE_SECONDARY, color(0xa0303946))
                        .token(BORDER, color(0x60cbd4e3)).token(BLUR, (float) DEFAULT_HUD_BLUR_RADIUS).build())
                .with(theme(NETHER).parent(DARK).token(BACKGROUND, color(0xff211419))
                        .token(SURFACE, color(0xff38252c)).token(SURFACE_ELEVATED, color(0xff483036))
                        .token(PRIMARY, color(0xffff9f87)).token(ACCENT, color(0xfffa846e)).build())
                .with(theme(END).parent(DARK).token(BACKGROUND, color(0xff171329))
                        .token(SURFACE, color(0xff29243c)).token(SURFACE_ELEVATED, color(0xff3b3154))
                        .token(PRIMARY, color(0xffd7baf8)).token(ACCENT, color(0xffc09df0)).build());
    }

    private static Theme.Builder theme(String id) {
        return Theme.builder(id).nameKey("sh_better_ui.theme.dynamic." + id.substring(id.indexOf(':') + 1))
                .source("built-in");
    }

    private static ColorValue color(int value) { return ColorValue.argb(value); }
}
