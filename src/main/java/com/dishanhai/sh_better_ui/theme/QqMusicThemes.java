// Author: dishanhai. Original palettes inspired by user-provided QQ Music references.
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import static com.dishanhai.sh_better_ui.theme.token.ThemeTokens.*;

/** Optional presets, never replacing the user's existing Apple-style or classic layout. */
public final class QqMusicThemes {
    public static final String AURORA = "sh_better_ui:qq_music_aurora";
    public static final String VINYL = "sh_better_ui:qq_music_vinyl";
    private QqMusicThemes() {}
    public static boolean matches(String id) { return AURORA.equals(id) || VINYL.equals(id); }
    public static ThemeRegistry registerInto(ThemeRegistry registry) {
        return registry.with(preset(AURORA,0xff142c32,0xc0283941))
                .with(preset(VINYL,0xff302317,0xd03b2c20));
    }
    private static Theme preset(String id,int background,int surface) {
        return Theme.builder(id).parent(BuiltinThemes.DARK).source("built-in")
                .nameKey("sh_better_ui.theme.dynamic."+id.substring(id.indexOf(':')+1))
                .token(BACKGROUND,ColorValue.argb(background)).token(SURFACE,ColorValue.argb(surface))
                .token(TEXT,ColorValue.argb(0xfff5f7f7)).token(TEXT_SECONDARY,ColorValue.argb(0xffd2dcda))
                .token(ACCENT,ColorValue.argb(0xff35d89a)).token(PRIMARY,ColorValue.argb(0xff35d89a))
                .token(ON_PRIMARY,ColorValue.argb(0xff09241b)).token(SELECTED,ColorValue.argb(0x4035d89a))
                .token(BORDER,ColorValue.argb(0x4079a194)).build();
    }
}
