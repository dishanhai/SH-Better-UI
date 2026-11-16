package com.dishanhai.sh_better_ui.config;

import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
import com.dishanhai.sh_better_ui.theme.ThemeId;

import java.util.Objects;

/** 山海：全库客户端主题。自定义颜色采用六位 RGB，空字符串继承基底。 */
public record ThemeSettings(Mode mode, Base customBase, String background, String surface,
                            String border, String text, String accent, ThemeOverrides effects, String backgroundEffect,
                            String hudBackgroundEffect, Integer hudBlurRadius, String registeredThemeId, ThemePaletteArchive paletteArchive) {
    public enum Mode {
        DARK, LIGHT, CUSTOM, LIQUID_GLASS, HUD_GLASS;

        public String fixedThemeId() {
            return switch (this) {
                case LIQUID_GLASS -> BuiltinThemes.LIQUID_GLASS;
                case HUD_GLASS -> BuiltinThemes.HUD_GLASS;
                default -> null;
            };
        }
    }
    public enum Base { DARK, LIGHT }

    public static final String DEFAULT_BACKGROUND_EFFECT = "dotMatrix";
    public static final int MAX_HUD_BLUR_RADIUS = 12;
    public static final ThemeSettings DEFAULT = new ThemeSettings(Mode.DARK, Base.DARK, "", "", "", "", "");

    public ThemeSettings {
        if (registeredThemeId == null) registeredThemeId = "";
        if (!validRegisteredThemeId(registeredThemeId)) throw new IllegalArgumentException("Invalid registered theme ID");
        if (paletteArchive == null) paletteArchive = ThemePaletteArchive.EMPTY;
        Objects.requireNonNull(effects);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(customBase);
        // Kernel settings saved before this field was added deserialize it as null.
        if (backgroundEffect == null) backgroundEffect = DEFAULT_BACKGROUND_EFFECT;
        if (!validBackgroundEffect(backgroundEffect)) throw new IllegalArgumentException(backgroundEffect);
        if (hudBackgroundEffect == null) hudBackgroundEffect = "";
        if (!validHudBackgroundEffect(hudBackgroundEffect)) throw new IllegalArgumentException(hudBackgroundEffect);
        if (hudBlurRadius == null) hudBlurRadius = BuiltinThemes.DEFAULT_HUD_BLUR_RADIUS;
        if (hudBlurRadius < 0 || hudBlurRadius > MAX_HUD_BLUR_RADIUS) {
            throw new IllegalArgumentException("HUD glass blur radius out of range: " + hudBlurRadius);
        }
        background = normalize(background);
        surface = normalize(surface);
        border = normalize(border);
        text = normalize(text);
        accent = normalize(accent);
    }

    public ThemeSettings(Mode mode, Base customBase, String background, String surface,
                         String border, String text, String accent) {
        this(mode, customBase, background, surface, border, text, accent, ThemeOverrides.EMPTY);
    }

    public ThemeSettings(Mode mode, Base customBase, String background, String surface,
                         String border, String text, String accent, ThemeOverrides effects) {
        this(mode, customBase, background, surface, border, text, accent, effects, DEFAULT_BACKGROUND_EFFECT);
    }

    public ThemeSettings(Mode mode, Base customBase, String background, String surface, String border,
                         String text, String accent, ThemeOverrides effects, String backgroundEffect) {
        this(mode, customBase, background, surface, border, text, accent, effects, backgroundEffect, "");
    }

    public ThemeSettings(Mode mode, Base customBase, String background, String surface, String border,
                         String text, String accent, ThemeOverrides effects, String backgroundEffect,
                         String hudBackgroundEffect) {
        this(mode, customBase, background, surface, border, text, accent, effects, backgroundEffect,
                hudBackgroundEffect, BuiltinThemes.DEFAULT_HUD_BLUR_RADIUS);
    }

    /** Compatibility constructor for configurations saved before registered palette selection. */
    public ThemeSettings(Mode mode, Base customBase, String background, String surface, String border,
                         String text, String accent, ThemeOverrides effects, String backgroundEffect,
                         String hudBackgroundEffect, Integer hudBlurRadius) {
        this(mode, customBase, background, surface, border, text, accent, effects, backgroundEffect,
                hudBackgroundEffect, hudBlurRadius, "", ThemePaletteArchive.EMPTY);
    }

    public static boolean validRegisteredThemeId(Object value) {
        return DynamicThemeSettings.validFixedTheme(value);
    }
    public String selectedThemeId() { return mode == Mode.CUSTOM && !registeredThemeId.isEmpty() ? registeredThemeId : mode.fixedThemeId(); }
    public ThemeSettings withRegisteredTheme(String id) {
        if (!validRegisteredThemeId(id)) throw new IllegalArgumentException("Invalid registered theme ID");
        if (registeredThemeId.equals(id)) return withMode(Mode.CUSTOM);
        var saved = paletteArchive.with(registeredThemeId,
                new ThemePaletteArchive.Palette(customBase, background, surface, border, text, accent));
        var palette = saved.get(id);
        return new ThemeSettings(Mode.CUSTOM, palette.base(), palette.background(), palette.surface(), palette.border(),
                palette.text(), palette.accent(), effects, backgroundEffect, hudBackgroundEffect, hudBlurRadius, id, saved);
    }

    public ThemeSettings withEffects(ThemeOverrides next) {
        return new ThemeSettings(mode, customBase, background, surface, border, text, accent, next,
                backgroundEffect, hudBackgroundEffect, hudBlurRadius, registeredThemeId, paletteArchive);
    }

    public ThemeSettings withMode(Mode next) {
        return new ThemeSettings(next, customBase, background, surface, border, text, accent, effects,
                backgroundEffect, hudBackgroundEffect, hudBlurRadius, registeredThemeId, paletteArchive);
    }

    /** Changes the custom palette without dropping background, HUD or effect overrides. */
    public ThemeSettings withPalette(Base base, String background, String surface, String border, String text, String accent) {
        return new ThemeSettings(mode, base, background, surface, border, text, accent, effects,
                backgroundEffect, hudBackgroundEffect, hudBlurRadius, registeredThemeId, paletteArchive);
    }

    public ThemeSettings withBackgroundEffect(String next) {
        return new ThemeSettings(mode, customBase, background, surface, border, text, accent, effects,
                next, hudBackgroundEffect, hudBlurRadius, registeredThemeId, paletteArchive);
    }

    public ThemeSettings withHudBackgroundEffect(String next) {
        return new ThemeSettings(mode, customBase, background, surface, border, text, accent, effects,
                backgroundEffect, next, hudBlurRadius, registeredThemeId, paletteArchive);
    }

    public ThemeSettings withHudBlurRadius(int next) {
        return new ThemeSettings(mode, customBase, background, surface, border, text, accent, effects,
                backgroundEffect, hudBackgroundEffect, next, registeredThemeId, paletteArchive);
    }

    public static boolean validHudBackgroundEffect(Object value) {
        return "".equals(value) || validBackgroundEffect(value);
    }

    public static boolean validBackgroundEffect(Object value) {
        if (!(value instanceof String id)) return false;
        if (!id.contains(":")) return !id.equals("none") && id.matches("[a-z][a-zA-Z0-9]{0,63}");
        try {
            ThemeId.require(id);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /** 为局部界面叠加效果，继承当前实际基底且不激活已休眠的自定义配置。 */
    public ThemeSettings overlayEffects(ThemeOverrides local) {
        Objects.requireNonNull(local);
        if (local.equals(ThemeOverrides.EMPTY)) return this;
        ThemeSettings base = mode == Mode.CUSTOM ? this
                : new ThemeSettings(Mode.CUSTOM, light() ? Base.LIGHT : Base.DARK, "", "", "", "", "");
        return base.withEffects(base.effects.overlay(local));
    }

    public boolean light() {
        return mode == Mode.LIGHT || mode == Mode.CUSTOM && customBase == Base.LIGHT;
    }

    public static boolean validColor(Object value) {
        return value instanceof String color && (color.isEmpty() || color.matches("#?[0-9a-fA-F]{6}"));
    }

    static String normalize(String value) {
        if (!validColor(value)) throw new IllegalArgumentException("Expected an empty value or a six-digit RGB color");
        if (value.isEmpty()) return value;
        return "#" + (value.startsWith("#") ? value.substring(1) : value).toUpperCase(java.util.Locale.ROOT);
    }
}
