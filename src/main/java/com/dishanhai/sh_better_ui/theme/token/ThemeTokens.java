// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.token;

import com.dishanhai.sh_better_ui.theme.transition.Easing;
import com.dishanhai.sh_better_ui.theme.transition.VisualProperties.Shadow;
import com.dishanhai.sh_better_ui.theme.transition.VisualProperties.Transform;

import java.time.Duration;
import java.util.List;

/** Built-in semantic token keys. Unqualified names in resource themes use the sh_better_ui namespace. */
public final class ThemeTokens {
    public static final TokenType<Easing> EASING = TokenType.discrete(Easing.class, value -> true);
    public static final TokenType<Shadow> SHADOW_TYPE = TokenType.of(Shadow.class, value -> true, Shadow::interpolate);
    public static final TokenType<Transform> TRANSFORM_TYPE =
            TokenType.of(Transform.class, value -> true, Transform::interpolate);

    public static final ThemeToken<ColorValue> PRIMARY = color("color.primary", 0xff3b82f6);
    public static final ThemeToken<ColorValue> ON_PRIMARY = color("color.onPrimary", 0xff061323);
    public static final ThemeToken<ColorValue> SECONDARY = color("color.secondary", 0xffa5b4fc);
    public static final ThemeToken<ColorValue> ACCENT = color("color.accent", 0xff3b82f6);
    public static final ThemeToken<ColorValue> BACKGROUND = color("surface.background", 0xff0f172a);
    public static final ThemeToken<ColorValue> SURFACE = color("surface.primary", 0xff1e293b);
    public static final ThemeToken<ColorValue> SURFACE_SECONDARY = color("surface.secondary", 0xff25364e);
    public static final ThemeToken<ColorValue> SURFACE_ELEVATED = color("surface.elevated", 0xff293750);
    public static final ThemeToken<ColorValue> SURFACE_OVERLAY = color("surface.overlay", 0xf5293750);
    public static final ThemeToken<ColorValue> TEXT = color("text.primary", 0xfff1f5f9);
    public static final ThemeToken<ColorValue> TEXT_SECONDARY = color("text.secondary", 0xffcbd5e1);
    public static final ThemeToken<ColorValue> TEXT_DISABLED = color("text.disabled", 0xff94a3b8);
    public static final ThemeToken<Boolean> TEXT_ADAPTIVE_COLOR = key("text.adaptiveColor", TokenType.BOOLEAN, false);
    public static final ThemeToken<Boolean> TEXT_FOREGROUND_PROTECTION =
            key("text.foregroundProtection", TokenType.BOOLEAN, false);
    public static final ThemeToken<ColorValue> BORDER = color("border.default", 0xff334155);
    public static final ThemeToken<ColorValue> BORDER_FOCUSED = color("border.focused", 0xff9cc9ff);
    public static final ThemeToken<ColorValue> BORDER_ERROR = color("border.error", 0xfffca5a5);
    public static final ThemeToken<Float> BORDER_WIDTH = number("border.width", 1);
    public static final ThemeToken<ColorValue> HOVER = color("state.hover", 0x20ffffff);
    public static final ThemeToken<ColorValue> PRESSED = color("state.pressed", 0x38ffffff);
    public static final ThemeToken<ColorValue> SELECTED = color("state.selected", 0x603b82f6);
    public static final ThemeToken<ColorValue> DISABLED = color("state.disabled", 0x30000000);
    public static final ThemeToken<ColorValue> SUCCESS = color("status.success", 0xff4ade80);
    public static final ThemeToken<ColorValue> WARNING = color("status.warning", 0xfffacc15);
    public static final ThemeToken<ColorValue> ERROR = color("status.error", 0xfffca5a5);
    public static final ThemeToken<ColorValue> INFO = color("status.info", 0xff93c5fd);
    public static final ThemeToken<Float> RADIUS_NONE = number("shape.radius.none", 0);
    public static final ThemeToken<Float> RADIUS_SMALL = number("shape.radius.small", 3);
    public static final ThemeToken<Float> RADIUS_MEDIUM = number("shape.radius.medium", 6);
    public static final ThemeToken<Float> RADIUS_LARGE = number("shape.radius.large", 12);
    public static final ThemeToken<Float> RADIUS_FULL = number("shape.radius.full", 10_000);
    public static final ThemeToken<Float> SPACING_XS = number("spacing.xs", 2);
    public static final ThemeToken<Float> SPACING_SM = number("spacing.sm", 4);
    public static final ThemeToken<Float> SPACING_MD = number("spacing.md", 8);
    public static final ThemeToken<Float> SPACING_LG = number("spacing.lg", 12);
    public static final ThemeToken<Float> SPACING_XL = number("spacing.xl", 16);
    public static final ThemeToken<String> FONT = key("typography.font", TokenType.STRING, "minecraft:default");
    public static final ThemeToken<Float> FONT_SIZE = number("typography.size", 9);
    public static final ThemeToken<Integer> FONT_WEIGHT = key("typography.weight", TokenType.INTEGER, 400);
    public static final ThemeToken<Float> LINE_HEIGHT = number("typography.lineHeight", 9);
    public static final ThemeToken<Duration> FAST = duration("motion.duration.fast", 120);
    public static final ThemeToken<Duration> NORMAL = duration("motion.duration.normal", 260);
    public static final ThemeToken<Duration> SLOW = duration("motion.duration.slow", 450);
    public static final ThemeToken<Easing> STANDARD = key("motion.easing.standard", EASING, Easing.STANDARD);
    public static final ThemeToken<Easing> EMPHASIZED = key("motion.easing.emphasized", EASING, Easing.EMPHASIZED);
    public static final ThemeToken<Easing> SPRING = key("motion.easing.spring", EASING, Easing.SPRING);
    public static final ThemeToken<Float> OPACITY = key("effect.opacity", TokenType.OPACITY, 1f);
    public static final ThemeToken<Float> BLUR = number("effect.blur", 0);
    public static final ThemeToken<Float> GLOW = key("effect.glow", TokenType.OPACITY, 0f);
    public static final ThemeToken<Float> PULSE = key("effect.pulse", TokenType.OPACITY, 0f);
    public static final ThemeToken<Shadow> SHADOW = key("effect.shadow", SHADOW_TYPE,
            new Shadow(0, 2, 4, ColorValue.argb(0x38000000)));
    public static final ThemeToken<Boolean> BACKDROP = key("effect.backdrop", TokenType.BOOLEAN, true);
    public static final ThemeToken<Transform> TRANSFORM = key("effect.transform", TRANSFORM_TYPE, Transform.IDENTITY);
    public static final ThemeToken<Boolean> LIGHT = key("appearance.light", TokenType.BOOLEAN, false);
    public static final ThemeToken<Boolean> LIQUID_GLASS = key("appearance.liquidGlass", TokenType.BOOLEAN, false);
    public static final ThemeToken<Boolean> HUD_GLASS = key("appearance.hudGlass", TokenType.BOOLEAN, false);

    private static final List<ThemeToken<?>> ALL = List.of(PRIMARY, ON_PRIMARY, SECONDARY, ACCENT, BACKGROUND, SURFACE,
            SURFACE_SECONDARY, SURFACE_ELEVATED, SURFACE_OVERLAY, TEXT, TEXT_SECONDARY, TEXT_DISABLED,
            TEXT_ADAPTIVE_COLOR, TEXT_FOREGROUND_PROTECTION,
            BORDER, BORDER_FOCUSED, BORDER_ERROR, BORDER_WIDTH, HOVER, PRESSED, SELECTED, DISABLED,
            SUCCESS, WARNING, ERROR, INFO, RADIUS_NONE, RADIUS_SMALL, RADIUS_MEDIUM, RADIUS_LARGE,
            RADIUS_FULL, SPACING_XS, SPACING_SM, SPACING_MD, SPACING_LG, SPACING_XL, FONT, FONT_SIZE,
            FONT_WEIGHT, LINE_HEIGHT, FAST, NORMAL, SLOW, STANDARD, EMPHASIZED, SPRING, OPACITY, BLUR,
            GLOW, PULSE, SHADOW, BACKDROP, TRANSFORM, LIGHT, LIQUID_GLASS, HUD_GLASS);

    private ThemeTokens() {}

    public static List<ThemeToken<?>> all() { return ALL; }

    public static ThemeToken<ColorValue> color(String id, int argb) { return key(id, TokenType.COLOR, ColorValue.argb(argb)); }
    public static ThemeToken<Float> floatToken(String id, float value) { return key(id, TokenType.FLOAT, value); }
    public static ThemeToken<Duration> duration(String id, long millis) {
        return key(id, TokenType.DURATION, Duration.ofMillis(millis));
    }

    private static ThemeToken<Float> number(String id, float value) { return key(id, TokenType.NON_NEGATIVE, value); }

    private static <T> ThemeToken<T> key(String id, TokenType<T> type, T value) {
        return new ThemeToken<>(id.contains(":") ? id : "sh_better_ui:" + id, type, value);
    }
}
