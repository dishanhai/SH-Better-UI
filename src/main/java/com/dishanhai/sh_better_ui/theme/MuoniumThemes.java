/*
 * Palette values adapted from MuoniumPlayer NCMTheme.ThemePreset.
 * Copyright 2026 MuoniumPlayer Team. Licensed under the Apache License, Version 2.0.
 * Source: https://github.com/zihe114514/MuoniumPlayer-multiversion
 * Revision: 9b4cc7e7bd143315da716075a6d024a0d4c0c81b
 * Modified for SHBUI: semantic UUK tokens, opaque surfaces, explicit contrast-safe
 * foreground/focus colors and native theme registration; no Skija or external assets.
 * See META-INF/licenses/muoniumplayer/{LICENSE,NOTICE,CHANGES.txt}.
 */
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import static com.dishanhai.sh_better_ui.theme.token.ThemeTokens.*;

/** Music-player palettes expressed using the shared, renderer-independent theme system. */
public final class MuoniumThemes {
    public static final String VELVET_ROSE = "sh_better_ui:muonium_velvet_rose";
    public static final String MIDNIGHT_GRAPHITE = "sh_better_ui:muonium_midnight_graphite";
    public static final String PORCELAIN_INK = "sh_better_ui:muonium_porcelain_ink";

    private MuoniumThemes() {}

    static ThemeRegistry registerInto(ThemeRegistry registry) {
        return registry.with(palette(VELVET_ROSE, false, 0x211821, 0x30212C, 0x493342,
                        0xFFF7FB, 0xC2AAB7, 0xE48AAB, 0xF4A9C4, 0x2A1D27, 0x604252))
                .with(palette(MIDNIGHT_GRAPHITE, false, 0x111419, 0x1B2028, 0x323B49,
                        0xF5F7FA, 0xA3ACBA, 0x8FA8C7, 0xB4C8E5, 0x202731, 0x4C5869))
                .with(palette(PORCELAIN_INK, true, 0xF4F0EA, 0xFFFCF7, 0xE7DDD1,
                        0x2A2723, 0x695F55, 0xA84E2B, 0x934323, 0xEEE7DF, 0x8A7767));
    }

    private static Theme palette(String id, boolean light, int background, int surface, int raised,
                                 int text, int secondary, int accent, int focus, int input, int border) {
        return Theme.builder(id).parent(light ? BuiltinThemes.LIGHT_ID : BuiltinThemes.DARK)
                .nameKey("sh_better_ui.theme.dynamic." + id.substring(id.indexOf(':') + 1))
                .source("MuoniumPlayer / SHBUI adaptation (Apache-2.0)")
                .token(LIGHT, light).token(BACKGROUND, color(background))
                .token(SURFACE, color(surface)).token(SURFACE_SECONDARY, color(input))
                .token(SURFACE_ELEVATED, color(raised)).token(SURFACE_OVERLAY, color(surface))
                .token(TEXT, color(text)).token(TEXT_SECONDARY, color(secondary))
                .token(TEXT_DISABLED, color(secondary)).token(BORDER, color(border))
                .token(BORDER_FOCUSED, color(focus)).token(PRIMARY, color(accent))
                .token(ACCENT, color(accent)).token(SECONDARY, color(focus))
                .token(ON_PRIMARY, color(light ? 0xFFFFFF : background))
                .token(HOVER, ColorValue.argb(0x18000000 | accent))
                .token(PRESSED, ColorValue.argb(0x30000000 | accent))
                .token(SELECTED, ColorValue.argb(0x38000000 | accent))
                .token(RADIUS_SMALL, 4f).token(RADIUS_MEDIUM, 8f).token(RADIUS_LARGE, 12f)
                .token(BLUR, 0f).token(GLOW, 0f).build();
    }

    private static ColorValue color(int rgb) { return ColorValue.argb(0xff000000 | rgb); }
}
