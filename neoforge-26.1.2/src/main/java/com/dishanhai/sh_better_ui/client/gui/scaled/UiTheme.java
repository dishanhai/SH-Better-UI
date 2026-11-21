// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.dishanhai.sh_better_ui.theme.token.TokenLookup;

/** Semantic colors for native controls, with an optional screen-local preview. */
public record UiTheme(boolean light, int background, int surface, int border, int text, int accent, int overrides) {
    private static final int BACKGROUND = 1, SURFACE = 2, BORDER = 4, TEXT = 8, ACCENT = 16, LIQUID_GLASS = 32, HUD_GLASS = 64;
    private static final ThreadLocal<UiTheme> PREVIEW = new ThreadLocal<>();
    public static final UiTheme EDITOR_WORKBENCH = new UiTheme(false, 0xFF0D1624, 0xFF162B40,
            0xFF37576B, 0xFFE4F0FC, 0xFF68E3FA, BACKGROUND | SURFACE | BORDER | TEXT | ACCENT);
    private static final UiTheme DARK = new UiTheme(false, ThemeTokens.BACKGROUND.defaultValue().argb(),
            ThemeTokens.SURFACE.defaultValue().argb(), ThemeTokens.BORDER.defaultValue().argb(),
            ThemeTokens.TEXT.defaultValue().argb(), ThemeTokens.ACCENT.defaultValue().argb(), 0);
    private static final UiTheme LIGHT = new UiTheme(true, 0xFFE8EEF5, 0xFFF8FAFC,
            0xFF64748B, 0xFF172334, 0xFF1755AA, 0);
    private static final UiTheme GLASS = fromTokens(BuiltinThemes.liquidGlassSnapshot());
    private static final UiTheme CLEAR_GLASS = fromTokens(BuiltinThemes.hudGlassSnapshot());

    private static final java.util.Map<String, UiTheme> REGISTERED = registered();
    private static java.util.Map<String,UiTheme> registered() {
        var catalog=new java.util.LinkedHashMap<String, com.dishanhai.sh_better_ui.theme.token.ThemeToken<?>>();
        for(var token:ThemeTokens.all()) catalog.put(token.id(),token);
        var registry=BuiltinThemes.registry();
        var result=new java.util.LinkedHashMap<String,UiTheme>();
        var resolver=new com.dishanhai.sh_better_ui.theme.ThemeResolver();
        for(String id:registry.themes().keySet()) result.put(id,fromTokens(resolver.resolve(registry,catalog,java.util.List.of(id),null)));
        return java.util.Map.copyOf(result);
    }

    static boolean isPreviewActive() { return PREVIEW.get() != null; }

    public static UiTheme current() {
        var preview = PREVIEW.get();
        return preview == null ? resolve(ControlPanelSettings.current().theme()) : preview;
    }

    public static UiTheme resolve(ThemeSettings settings) {
        if (settings.mode() == ThemeSettings.Mode.LIQUID_GLASS) return GLASS;
        if (settings.mode() == ThemeSettings.Mode.HUD_GLASS) return CLEAR_GLASS;
        UiTheme base = settings.light() ? LIGHT : DARK;
        if (settings.mode() != ThemeSettings.Mode.CUSTOM) return base;
        base=REGISTERED.getOrDefault(settings.registeredThemeId(),base);
        int mask = (settings.background().isEmpty() ? 0 : BACKGROUND)
                | (settings.surface().isEmpty() ? 0 : SURFACE)
                | (settings.border().isEmpty() ? 0 : BORDER)
                | (settings.text().isEmpty() ? 0 : TEXT)
                | (settings.accent().isEmpty() ? 0 : ACCENT);
        return new UiTheme(base.light, rgb(settings.background(), base.background), rgb(settings.surface(), base.surface),
                rgb(settings.border(), base.border), rgb(settings.text(), base.text), rgb(settings.accent(), base.accent),
                mask | base.overrides);
    }

    public static void preview(UiTheme theme, Runnable extract) {
        UiTheme previous = PREVIEW.get();
        PREVIEW.set(theme);
        try {
            extract.run();
        } finally {
            if (previous == null) PREVIEW.remove();
            else PREVIEW.set(previous);
        }
    }

    public static UiTheme fromScope(ThemeScope scope) { return fromTokens(scope); }

    public static UiTheme fromTokens(TokenLookup tokens) {
        int mask = BACKGROUND | SURFACE | BORDER | TEXT | ACCENT;
        if (tokens.resolve(ThemeTokens.LIQUID_GLASS)) mask |= LIQUID_GLASS;
        if (tokens.resolve(ThemeTokens.HUD_GLASS)) mask |= HUD_GLASS;
        return new UiTheme(tokens.resolve(ThemeTokens.LIGHT), tokens.resolve(ThemeTokens.BACKGROUND).argb(),
                tokens.resolve(ThemeTokens.SURFACE).argb(), tokens.resolve(ThemeTokens.BORDER).argb(),
                tokens.resolve(ThemeTokens.TEXT).argb(), tokens.resolve(ThemeTokens.ACCENT).argb(), mask);
    }

    public static GraphicIcon icon(ThemeSettings.Mode mode) {
        return switch (mode) {
            case DARK -> GraphicIcon.DARK_MODE;
            case LIGHT -> GraphicIcon.LIGHT_MODE;
            case CUSTOM -> GraphicIcon.CUSTOM_THEME;
            case LIQUID_GLASS, HUD_GLASS -> GraphicIcon.BLUR;
        };
    }

    ThemeButtonStyle.Paint editorButton(boolean enabled, boolean contrast, boolean selected, float hover, float press) {
        int focus = contrast ? text : accent;
        int fill = enabled ? GuiMotion.lerpColor(surface, accent, (selected ? 0.15f : 0f) + 0.12f * hover) : background;
        fill = GuiMotion.lerpColor(fill, background, press * 0.7f);
        int edge = contrast || selected ? focus : GuiMotion.lerpColor(border, accent, Math.max(hover * 0.4f, press));
        int label = enabled || contrast ? text : secondaryText(text, 0.48f);
        return new ThemeButtonStyle.Paint(fill, label, edge, focus, accent, background, 0, 1, false);
    }

    public boolean liquidGlass() { return (overrides & LIQUID_GLASS) != 0; }
    public boolean hudGlass() { return (overrides & HUD_GLASS) != 0; }

    public int background(int dark) { return tint(dark, background, BACKGROUND); }
    public int surface(int dark) { return tint(dark, surface, SURFACE); }
    public int border(int dark) { return tint(dark, border, BORDER); }
    public int text(int dark) { return tint(dark, text, TEXT); }
    public int accent(int dark) { return tint(dark, accent, ACCENT); }

    public int secondaryText(int dark, float muted) {
        return light || (overrides & (TEXT | SURFACE)) != 0
                ? GuiMotion.lerpColor(text, surface, muted) : dark;
    }

    public int controlFill(int dark, float emphasis) {
        if (!light && (overrides & (SURFACE | ACCENT)) == 0) return dark;
        int tint = GuiMotion.lerpColor(surface, accent, emphasis);
        int alpha = light ? Math.max(dark >>> 24, 0xCC) : dark >>> 24;
        return alpha << 24 | (tint & 0xFFFFFF);
    }

    private int tint(int dark, int color, int role) {
        if (!light && (overrides & role) == 0) return dark;
        return (dark & 0xFF000000) | (color & 0xFFFFFF);
    }

    private static int rgb(String value, int fallback) {
        return value.isEmpty() ? fallback : 0xFF000000 | Integer.parseInt(value.substring(1), 16);
    }
}
