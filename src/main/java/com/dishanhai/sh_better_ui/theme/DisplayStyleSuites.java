// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.transition.Easing;
import com.dishanhai.sh_better_ui.theme.transition.VisualProperties.Shadow;
import java.time.Duration;
import java.util.List;

import static com.dishanhai.sh_better_ui.theme.token.ThemeTokens.*;

/**
 * Original SHBUI display suites, shared by every platform renderer. Each suite specifies
 * the surfaces, interaction feedback, geometry and motion as one consistent collection.
 * Font and layout choices deliberately remain the player's independent preferences.
 */
public final class DisplayStyleSuites {
    public static final String PORCELAIN = "sh_better_ui:suite_porcelain";
    public static final String MIDNIGHT = "sh_better_ui:suite_midnight";
    public static final String JADE = "sh_better_ui:suite_jade";
    public static final String WARM_PAPER = "sh_better_ui:suite_warm_paper";
    /** Stable presentation order; callers must not infer identity from a translated label. */
    public static final List<String> IDS = List.of(PORCELAIN, MIDNIGHT, JADE, WARM_PAPER);

    private DisplayStyleSuites() {}

    public static boolean matches(String id) { return id != null && IDS.contains(id); }

    static ThemeRegistry registerInto(ThemeRegistry registry) {
        return registry
                .with(suite(PORCELAIN, true, 0xF0F4FA, 0xFFFFFF, 0xE6EDF6, 0xF8FAFF,
                        0x17243A, 0x45536B, 0x526179, 0x245AC1, 0x193F8F,
                        0x60738D, 6f, 10f, 15f, 120, 220))
                .with(suite(MIDNIGHT, false, 0x10121E, 0x1C2032, 0x151928, 0x282D43,
                        0xF2F3FF, 0xBCC3E0, 0xA8B1CF, 0xA5B4FF, 0xCED5FF,
                        0x7A84AB, 5f, 9f, 13f, 100, 200))
                .with(suite(JADE, false, 0x0D1B18, 0x142D26, 0x10231E, 0x1C3C32,
                        0xEFFDF6, 0xB6D9C7, 0xA1C7B3, 0x75DDB0, 0xADF5D2,
                        0x6DAB8E, 9f, 13f, 18f, 130, 260))
                .with(suite(WARM_PAPER, true, 0xF4EEDF, 0xFFFDF7, 0xEDE4D2, 0xF9F4E8,
                        0x312B22, 0x605343, 0x6C5D4A, 0x8D482A, 0x6B351F,
                        0x897052, 2f, 4f, 7f, 90, 180));
    }

    private static Theme suite(String id, boolean light, int background, int surface, int inset,
                               int raised, int text, int secondary, int disabled, int accent,
                               int focus, int border, float small, float medium, float large,
                               long fast, long normal) {
        return Theme.builder(id).parent(light ? BuiltinThemes.LIGHT_ID : BuiltinThemes.DARK)
                .nameKey("sh_better_ui.theme.dynamic." + id.substring(id.indexOf(':') + 1))
                .source("SHBUI original display suite")
                .metadata("category", "display-suite")
                .token(LIGHT, light).token(LIQUID_GLASS, false).token(HUD_GLASS, false)
                .token(GLASS_ROOT_TINT, false).token(BACKDROP, false)
                .token(BACKGROUND, rgb(background)).token(SURFACE, rgb(surface))
                .token(SURFACE_SECONDARY, rgb(inset)).token(SURFACE_ELEVATED, rgb(raised))
                .token(SURFACE_OVERLAY, rgb(surface)).token(TEXT, rgb(text))
                .token(TEXT_SECONDARY, rgb(secondary)).token(TEXT_DISABLED, rgb(disabled))
                .token(PRIMARY, rgb(accent)).token(ACCENT, rgb(accent)).token(SECONDARY, rgb(focus))
                .token(ON_PRIMARY, rgb(light ? 0xFFFFFF : 0x101820))
                .token(BORDER, rgb(border)).token(BORDER_FOCUSED, rgb(focus)).token(BORDER_WIDTH, 1f)
                .token(BORDER_ERROR, rgb(light ? 0xAA2338 : 0xFFABB6))
                .token(HOVER, overlay(0x14, accent)).token(PRESSED, overlay(0x28, accent))
                .token(SELECTED, overlay(0x30, accent)).token(DISABLED, overlay(0x10, background))
                .token(SUCCESS, rgb(light ? 0x176743 : 0x89E5B4))
                .token(WARNING, rgb(light ? 0x795000 : 0xF2D188))
                .token(ERROR, rgb(light ? 0xAA2338 : 0xFFABB6)).token(INFO, rgb(accent))
                .token(RADIUS_SMALL, small).token(RADIUS_MEDIUM, medium).token(RADIUS_LARGE, large)
                .token(FAST, Duration.ofMillis(fast)).token(NORMAL, Duration.ofMillis(normal))
                .token(SLOW, Duration.ofMillis(normal + 120)).token(STANDARD, Easing.STANDARD)
                .token(EMPHASIZED, Easing.EMPHASIZED)
                .token(BLUR, 0f).token(GLOW, 0f).token(PULSE, 0f).token(OPACITY, 1f)
                .token(SHADOW, new Shadow(0, light ? 2 : 1, light ? 6 : 4,
                        ColorValue.argb(light ? 0x18000000 : 0x30000000))).build();
    }

    private static ColorValue rgb(int value) { return ColorValue.argb(0xFF000000 | value); }
    private static ColorValue overlay(int alpha, int value) { return ColorValue.argb(alpha << 24 | value); }
}
