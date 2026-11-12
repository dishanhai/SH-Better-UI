package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassQuality;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import net.minecraft.client.gui.GuiGraphics;

/** 山海：原生界面的液态玻璃绘制。调用方在材质之后绘制内容并管理原生输入。 */
public final class LiquidGlassTheme {
    public static final int TEXT = 0xFFF5F7FA;
    public static final int TEXT_MUTED = 0xFFB2BBC7;
    public static final int ACCENT = 0xFF9ACDFF;
    public static final float DEFAULT_BUTTON_BLUR_RADIUS = 12f;

    private static final int EDGE = 0xA6CBD4E3;
    private static final int CONTRAST_EDGE = 0xFFF1F5F9;
    private static final int FOCUS_EDGE = 0xFF8AAFCB;
    private static final int CONTRAST_FOCUS_EDGE = 0xFFBCDFFF;
    static final int POPOVER_RADIUS = 5;
    private static final float POPOVER_BLUR_RADIUS = 10f;
    static final int POPOVER_FILL = 0xE6252B34;
    private static final int LAYERED_POPOVER_FILL = 0xA6252B34;
    private static final float REVEAL_FADE_END = 0.28f;
    // Keep the refracting rim without regenerating backdrop mipmaps for clear panels.
    private static final GlassMaterial CLEAR_LIGHT_TEXT = new GlassMaterial(GlassVariant.CLEAR,
            GlassMaterial.preset(GlassVariant.CLEAR).optics(), GlassQuality.PERFORMANCE, GlassMaterial.Foreground.LIGHT);
    private static final GlassMaterial CLEAR_DARK_TEXT = new GlassMaterial(GlassVariant.CLEAR,
            CLEAR_LIGHT_TEXT.optics(), CLEAR_LIGHT_TEXT.quality(), GlassMaterial.Foreground.DARK);

    private LiquidGlassTheme() {}

    /** 自绘控件的内层底板；背景由宿主根板取样，避免每个内层重复采样。 */
    public static void plate(GuiGraphics g, int x, int y, int width, int height, int edge, int fill,
                             UiPreferences preferences) {
        surface(g, x, y, width, height, 5f, preferences, false, GlassStyle.LIQUID_REGULAR,
                POPOVER_BLUR_RADIUS, 1f, fill, edge, false, 1f, false);
    }

    public static int textColor() { return ThemeEffects.material("liquidGlass").text(UiTheme.current().text(TEXT)); }
    public static int mutedTextColor() { return UiTheme.current().secondaryText(TEXT_MUTED, 0.34f); }
    public static int accentColor() { return ThemeEffects.material("liquidGlass").accent(UiTheme.current().accent(ACCENT)); }

    private static int edgeColor() { return UiTheme.current().border(EDGE); }
    static int popoverFill() { return UiTheme.current().controlFill(POPOVER_FILL, 0f); }

    /** 浮在内容上的工具列或导航底板。先绘制其后方的内容。 */
    public static void toolbar(GuiGraphics g, int x, int y, int width, int height, UiPreferences preferences) {
        surface(g, x, y, width, height, 12f, preferences, true,
                GlassStyle.LIQUID_CLEAR, 7f, 1f, UiTheme.current().controlFill(0xB8252B34, 0.0f),
                edgeColor(), false, 1f, false);
    }

    /** 独立浮层。较厚的底色用于保证正文与操作项的对比度。 */
    public static void popover(GuiGraphics g, int x, int y, int width, int height, UiPreferences preferences) {
        popover(g, x, y, width, height, POPOVER_RADIUS, preferences, 1f);
    }

    static void popover(GuiGraphics g, FluidMorph frame, UiPreferences preferences, float progress) {
        var rect = frame.rect();
        popover(g, rect.getX(), rect.getY(), rect.getW(), rect.getH(), frame.radius(), preferences, progress);
    }

    private static void popover(GuiGraphics g, float x, float y, float width, float height, float radius,
                                UiPreferences preferences, float progress) {
        var settings = ThemeEffects.settings();
        if (UiTheme.current().hudGlass() && width > 0 && height > 0 && useBackdrop(preferences)
                && settings.backdrop().enabled()) {
            float inset = Math.min(5f, Math.min(width, height) * 0.25f);
            var foreground = new GlassDraw.ForegroundBounds(x + inset, y + inset,
                    width - 2 * inset, height - 2 * inset);
            if (clearSurface(g, GlassRegion.rounded(x, y, width, height, radius), foreground,
                    textColor(), settings.theme().hudBlurRadius())) return;
        }
        float opacity = revealCoverage(progress);
        int edge = GuiMotion.lerpColor(accentColor(), edgeColor(), progress);
        surface(g, x, y, width, height, radius, preferences, true, GlassStyle.LIQUID_REGULAR,
                POPOVER_BLUR_RADIUS, opacity, revealFill(progress), edge, false, opacity, false);
    }

    /** Shared clear optical surface for HUD and screen panels. Draw the foreground after the glass. */
    static boolean clearSurface(GuiGraphics graphics, GlassRegion region,
                                GlassDraw.ForegroundBounds foreground, int textColor) {
        return clearSurface(graphics, region, foreground, textColor, 0);
    }

    private static boolean clearSurface(GuiGraphics graphics, GlassRegion region,
                                        GlassDraw.ForegroundBounds foreground, int textColor, int blurRadius) {
        int luma = ((textColor >> 16 & 255) * 54 + (textColor >> 8 & 255) * 183 + (textColor & 255) * 19) >> 8;
        var material = clearMaterial(luma >= 128, blurRadius);
        return ClientUiKernel.paintHudGlass(graphics, new GlassDraw(region, material, GlassDraw.Profile.SNELL, foreground));
    }

    static GlassMaterial clearMaterial(boolean lightText, int blurRadius) {
        var clear = lightText ? CLEAR_LIGHT_TEXT : CLEAR_DARK_TEXT;
        if (blurRadius == 0) return clear;
        var optics = clear.optics();
        // Performance mode samples the refracting rim without filtering the face of the glass.
        var filtered = new GlassOptics(optics.transmission(), optics.refraction(), optics.lensing(), 0.70f,
                optics.edgeHighlight(), optics.thickness(), blurRadius);
        return new GlassMaterial(clear.variant(), filtered, GlassQuality.BALANCED, clear.foreground());
    }

    static int revealFill(float progress) {
        // 取样稳定且正文开始显现后再透出背景，避免底色在切入正文时跳变。
        float fadeStart = UiCapsuleReveal.CONTENT_FADE_START;
        float tint = GuiMotion.clamp01((progress - fadeStart) / (1f - fadeStart));
        return GuiMotion.lerpColor(popoverFill() | 0xFF000000, popoverFill(), tint);
    }

    static float revealCoverage(float progress) {
        float t = GuiMotion.clamp01(progress / REVEAL_FADE_END);
        return t * t * (3f - 2f * t);
    }

    /** 山海：在当前图层位置绘制带柔影的浮层底板。调用方随后在同一位置绘制前景。 */
    public static void layeredPopover(GuiGraphics g, int x, int y, int width, int height,
                                      UiPreferences preferences, float blurStrength) {
        if (width <= 0 || height <= 0) return;
        popoverShadow(g, x, y, width, height, POPOVER_RADIUS, preferences, 1f);
        surface(g, x, y, width, height, POPOVER_RADIUS, preferences, true,
                GlassStyle.LIQUID_REGULAR, POPOVER_BLUR_RADIUS, blurStrength,
                UiTheme.current().controlFill(LAYERED_POPOVER_FILL, 0f), edgeColor(), false, 1f, false);
    }

    static void morphPopover(GuiGraphics g, FluidMorph frame, UiPreferences preferences, DarkModeTheme.State state,
                             boolean primary, boolean selected, float emphasis, boolean sampleBackground,
                             float buttonBlurRadius, float progress, ThemeButtonStyle.Paint paint) {
        GuiRect rect = frame.rect();
        float x = rect.getX(), y = rect.getY(), width = rect.getW(), height = rect.getH();
        float corner = frame.radius();
        popoverShadow(g, x, y, width, height, corner, preferences, progress);

        int fromFill = paint == null ? buttonFill(state, primary, selected, emphasis) : paint.fill();
        if (buttonBlurRadius == 0f) fromFill |= 0xFF000000;
        int fill = GuiMotion.lerpColor(fromFill, UiTheme.current().controlFill(LAYERED_POPOVER_FILL, 0f), progress);
        boolean buttonSamples = sampleBackground && buttonBlurRadius > 0f;
        float radius = GuiMotion.lerp(buttonSamples ? buttonBlurRadius : 0f, POPOVER_BLUR_RADIUS, progress);
        float strength = buttonSamples ? 1f : progress;
        if (!useBackdrop(preferences)) {
            fill |= 0xFF000000;
        } else if (radius > 0f && !GlassBlur.tryBackdrop(g, rect, strength,
                progress == 0f ? GlassStyle.LIQUID_CLEAR : GlassStyle.LIQUID_REGULAR, radius, corner)) {
            fill = GuiMotion.lerpColor(fill, fill | 0xFF000000, buttonSamples ? 1f : progress);
        }
        boolean highContrast = preferences.highContrast();
        boolean focused = state.enabled() && state.focused();
        int baseEdge = edgeColor();
        int fromEdge = paint == null ? outlineColor(baseEdge, focused, highContrast)
                : focused ? paint.focus() : paint.border();
        float fromStroke = paint == null ? outlineWidth(true, focused, highContrast)
                : themedStroke(paint, focused, highContrast);
        int edge = GuiMotion.lerpColor(fromEdge,
                outlineColor(baseEdge, false, highContrast), progress);
        float stroke = GuiMotion.lerp(fromStroke, 1f, progress);
        RoundedCorners.draw(g, x, y, width, height, corner, stroke, edge, fill, progress > 0f);
        popoverHighlight(g, x, y, width, height, corner, preferences, progress);
    }

    private static void popoverShadow(GuiGraphics g, float x, float y, float width, float height, float radius,
                                      UiPreferences preferences, float opacity) {
        if (preferences.highContrast() || opacity <= 0f) return;
        int shadow = GuiMotion.lerpColor(0x00060912, 0x18060912, opacity);
        for (int spread = 6; spread >= 2; spread -= 2) {
            float corner = RoundedCorners.outsetRadius(width, height, radius, spread);
            RoundedCorners.draw(g, x - spread, y + 2 - spread, width + 2 * spread, height + 2 * spread,
                    corner, 0f, 0, shadow, false);
        }
    }

    /** 输入框底板。输入、光标、选取及旁白由原生控件管理。 */
    public static void input(GuiGraphics g, int x, int y, int width, int height,
                             UiPreferences preferences, boolean sampleBackground, boolean focused) {
        surface(g, x, y, width, height, 5, preferences, sampleBackground,
                GlassStyle.LIQUID_REGULAR, 8f, 1f, UiTheme.current().controlFill(0xE61D232C, 0f),
                edgeColor(), false, 1f, focused);
    }

    /** 候选项标记与文字分开绘制，保留键盘选择与鼠标悬停的区别。 */
    public static void suggestionRow(GuiGraphics g, int x, int y, int width, int height,
                                     boolean selected, boolean hovered, UiPreferences preferences) {
        if (width <= 2 || height <= 2 || (!selected && !hovered)) return;
        var theme = UiTheme.current();
        int fill = theme.controlFill(0xFF3D4653, 0.1f);
        if (selected) fill = hovered ? theme.controlFill(0xFF30465C, 0.22f) : theme.controlFill(0xFF26384B, 0.16f);
        DarkModeTheme.rounded(g, x, y, width, height, 4, 0, 0, fill);
        if (selected && width >= 4 && height > 4) {
            int marker = theme.accent(preferences.highContrast() ? CONTRAST_FOCUS_EDGE : ACCENT);
            g.fill(x + 2, y + 2, x + 3, y + height - 2, marker);
        }
    }

    /**
     * 胶囊按钮。可复用现有的 Interaction 采样，已在玻璃底板上时可跳过重复取样。
     * content 在相同 pose 下绘制，按钮的原生命中范围保持固定。
     */
    public static void button(GuiGraphics g, int x, int y, int width, int height,
                              DarkModeTheme.State state, UiPreferences preferences, boolean sampleBackground,
                              boolean primary, boolean selected, float emphasis, Runnable content) {
        button(g, x, y, width, height, state, preferences, sampleBackground, DEFAULT_BUTTON_BLUR_RADIUS,
                primary, selected, emphasis, content);
    }

    /** 自选按钮背景模糊半径（GUI 单位），0 关闭，超过 32 按 32 处理。先绘制背景，再调用本方法。 */
    public static void button(GuiGraphics g, int x, int y, int width, int height,
                              DarkModeTheme.State state, UiPreferences preferences, boolean sampleBackground,
                              float blurRadius, boolean primary, boolean selected, float emphasis, Runnable content) {
        button(g, x, y, width, height, state, preferences, sampleBackground, blurRadius,
                primary, selected, emphasis, true, null, content);
    }

    static void button(GuiGraphics g, int x, int y, int width, int height, DarkModeTheme.State state,
                       UiPreferences preferences, boolean sampleBackground, float blurRadius, boolean primary,
                       boolean selected, float emphasis, boolean selectionMarker, Runnable content) {
        button(g, x, y, width, height, state, preferences, sampleBackground, blurRadius,
                primary, selected, emphasis, selectionMarker, null, content);
    }

    static void button(GuiGraphics g, int x, int y, int width, int height, DarkModeTheme.State state,
                       UiPreferences preferences, boolean sampleBackground, float blurRadius, boolean primary,
                       boolean selected, float emphasis, boolean selectionMarker, ThemeButtonStyle.Paint paint,
                       Runnable content) {
        float radiusVirtual = normalizeBlurRadius(blurRadius);
        if (width <= 0 || height <= 0) return;
        float radius = paint == null ? Math.min(width, height) * 0.5f : paint.radius();
        int fill = paint == null ? buttonFill(state, primary, selected, emphasis) : paint.fill();
        boolean focused = state.enabled() && state.focused();
        GuiRenderUtil.withButtonMotion(g, x, y, width, height, state.buttonScale(), 0f, () -> {
            surface(g, x, y, width, height, radius, preferences, sampleBackground,
                    GlassStyle.LIQUID_CLEAR, radiusVirtual, 1f, fill, edgeColor(), true, 1f, focused, paint);
            if (selected && selectionMarker) {
                int marker = paint == null ? (state.enabled() ? accentColor() : mutedTextColor())
                        : (state.enabled() ? paint.accent() : paint.text());
                drawSelectionMarker(g, x, y, width, height, marker);
            }
            content.run();
        });
    }

    public static int controlText(boolean enabled) {
        return enabled ? textColor() : mutedTextColor();
    }

    static void drawSelectionMarker(GuiGraphics g, int x, int y, int width, int height, int color) {
        if (width <= 10 || height <= 5) return;
        int marker = Math.min(4, width / 4);
        g.fill(x + width / 2 - marker, y + height - 3, x + width / 2 + marker, y + height - 2,
                color);
    }

    static float normalizeBlurRadius(float radius) {
        if (!Float.isFinite(radius) || radius < 0f) throw new IllegalArgumentException();
        return Math.min(radius, 32f);
    }

    static int buttonFill(DarkModeTheme.State state, boolean primary, boolean selected, float emphasis) {
        if (!state.enabled()) return UiTheme.current().controlFill(0x96303640, 0.0f);
        int rest = primary ? UiTheme.current().controlFill(0x86244B78, 0.14f) : UiTheme.current().controlFill(0x522B333E, 0.0f);
        int hover = primary ? UiTheme.current().controlFill(0x9C2B598A, 0.23f) : UiTheme.current().controlFill(0x723B4A5B, 0.1f);
        int fill = GuiMotion.lerpColor(rest, hover, state.hover());
        if (selected) fill = GuiMotion.lerpColor(fill, UiTheme.current().controlFill(0xA0315E88, 0.24f), 0.45f + 0.20f * GuiMotion.clamp01(emphasis));
        return GuiMotion.lerpColor(fill, UiTheme.current().controlFill(0xAC182434, 0.27f), state.press() * 0.72f);
    }

    private static int outlineColor(int base, boolean focused, boolean highContrast) {
        if (focused) return UiTheme.current().accent(highContrast ? CONTRAST_FOCUS_EDGE : FOCUS_EDGE);
        return highContrast ? UiTheme.current().text(CONTRAST_EDGE) : base;
    }

    private static float outlineWidth(boolean capsule, boolean focused, boolean highContrast) {
        if (focused) return highContrast ? 2f : 1f;
        return capsule && !highContrast ? 0.65f : 1f;
    }

    private static float themedStroke(ThemeButtonStyle.Paint paint, boolean focused, boolean highContrast) {
        return Math.max(paint.borderWidth(), focused ? (highContrast ? 2 : 1) : 0);
    }

    private static void surface(GuiGraphics g, float x, float y, float width, float height, float radius,
                                UiPreferences preferences, boolean sampleBackground, GlassStyle style,
                                float blurRadius, float blurStrength, int fill, int edge, boolean capsule,
                                float opacity, boolean focused) {
        surface(g, x, y, width, height, radius, preferences, sampleBackground, style, blurRadius, blurStrength,
                fill, edge, capsule, opacity, focused, null);
    }

    private static void surface(GuiGraphics g, float x, float y, float width, float height, float radius,
                                UiPreferences preferences, boolean sampleBackground, GlassStyle style,
                                float blurRadius, float blurStrength, int fill, int edge, boolean capsule,
                                float opacity, boolean focused, ThemeButtonStyle.Paint paint) {
        if (width <= 0 || height <= 0 || opacity <= 0f) return;
        if (paint == null) {
            var material = ThemeEffects.material("liquidGlass");
            fill = material.fill(fill);
            edge = material.edge(edge);
        }
        float corner = RoundedCorners.fit(width, height, radius);
        boolean backdrop = useBackdrop(preferences);
        if (backdrop && sampleBackground && blurRadius > 0f) {
            boolean sampled = GlassBlur.tryBackdrop(g, new GuiRect(x, y, width, height), blurStrength,
                    style, blurRadius, corner);
            if (!sampled) fill |= 0xFF000000;
        } else if (!backdrop || blurRadius == 0f) {
            fill |= 0xFF000000;
        }
        boolean highContrast = preferences.highContrast();
        edge = paint == null ? outlineColor(edge, focused, highContrast) : focused ? paint.focus() : paint.border();
        if (opacity < 1f) {
            fill = GuiMotion.lerpColor(fill & 0xFFFFFF, fill, opacity);
            edge = GuiMotion.lerpColor(edge & 0xFFFFFF, edge, opacity);
        }
        float stroke = paint == null ? outlineWidth(capsule, focused, highContrast)
                : themedStroke(paint, focused, highContrast);
        RoundedCorners.draw(g, x, y, width, height, corner, stroke, edge, fill, !capsule);
        if (!capsule) popoverHighlight(g, x, y, width, height, corner, preferences, opacity);
    }

    private static boolean useBackdrop(UiPreferences preferences) {
        return preferences.useBackdropBlur() && !ThemeEffects.settings().dynamicTheme().lowPerformance();
    }

    private static void popoverHighlight(GuiGraphics g, float x, float y, float width, float height, float corner,
                                         UiPreferences preferences, float opacity) {
        if (preferences.highContrast() || opacity <= 0f || width <= 2 * corner + 2 || height <= 3) return;
        int left = (int) Math.ceil(x + corner);
        int right = (int) Math.floor(x + width - corner);
        int top = Math.round(y) + 1;
        if (right > left) g.fill(left, top, right, top + 1, GuiMotion.lerpColor(0x00FFFFFF, 0x45FFFFFF, opacity));
    }
}
