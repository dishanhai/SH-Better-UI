package com.dishanhai.sh_better_ui.client.glass;

import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 山海：中性浅色玻璃底。先画背景模糊，再画半透明板与细亮边；调用方随后绘制文字和图标。
 * 不持有界面状态，原生 Screen 和 LDLib 都可以复用。模糊不可用时底板仍可见。
 */
public final class GlassSurface {
    private static final float CLEAR_BLUR_RADIUS = 14f;
    private static final float CONTROL_BLUR_RADIUS = 8f;
    static final float SMOKE_BLUR_RADIUS = 16f;

    private GlassSurface() {}

    /** 面板和卡片；坐标为当前 pose 下的 GUI 单位。 */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        if (w < 3 || h < 3) return;
        GlassBlur.backdrop(g, new GuiRect(x, y, w, h), 1f, GlassStyle.CLEAR, CLEAR_BLUR_RADIUS);
        GlassColors colors = GlassColors.CLEAR;
        var material = ThemeEffects.material("clearGlass");
        plate(g, x, y, w, h, material.fill(colors.PANEL_FILL), material.edge(colors.ROOT_EDGE),
                material.edge(colors.DISPLAY_EDGE), material.accent(colors.LIT));
    }

    /** 按钮与页签；hover 为 0..1，可直接接入现有弹簧动画。 */
    public static void control(GuiGraphics g, int x, int y, int w, int h, float hover) {
        control(g, x, y, w, h, hover, true);
    }

    /** 已位于毛玻璃面板内时可关闭重复背景采样。 */
    public static void control(GuiGraphics g, int x, int y, int w, int h, float hover, boolean sampleBackground) {
        if (w < 3 || h < 3) return;
        GlassColors colors = GlassColors.CLEAR;
        var material = ThemeEffects.material("clearGlass");
        float t = GuiMotion.clamp01(hover);
        if (sampleBackground) GlassBlur.backdrop(g, new GuiRect(x, y, w, h), 1f,
                GlassStyle.CLEAR, CONTROL_BLUR_RADIUS);
        plate(g, x, y, w, h,
                material.fill(clearControlFill(t, sampleBackground)),
                material.edge(GuiMotion.lerpColor(colors.BUTTON_EDGE, colors.ROOT_EDGE, t)),
                material.edge(colors.DISPLAY_EDGE), material.accent(colors.LIT));
    }

    /** 山海：烟灰面板采用密采样毛玻璃，保留微量提亮与柔和边缘光。 */
    public static void smokePanel(GuiGraphics g, int x, int y, int w, int h) {
        if (w < 3 || h < 3) return;
        GlassBlur.backdrop(g, new GuiRect(x, y, w, h), 1f, GlassStyle.FROSTED, SMOKE_BLUR_RADIUS);
        GlassColors colors = GlassColors.SMOKE;
        var material = ThemeEffects.material("smokeGlass");
        plate(g, x, y, w, h, material.fill(colors.PANEL_FILL), material.edge(colors.ROOT_EDGE),
                material.edge(colors.DISPLAY_EDGE), material.accent(colors.LIT));
    }

    /** 烟灰根板内的控件不重复模糊；沿用按钮弹簧的 hover 值。 */
    public static void smokeControl(GuiGraphics g, int x, int y, int w, int h, float hover) {
        if (w < 3 || h < 3) return;
        GlassColors colors = GlassColors.SMOKE;
        var material = ThemeEffects.material("smokeGlass");
        float t = GuiMotion.clamp01(hover);
        plate(g, x, y, w, h,
                material.fill(GuiMotion.lerpColor(colors.BUTTON_FILL, colors.BUTTON_FILL_HOVER, t)),
                material.edge(colors.BUTTON_EDGE), material.edge(colors.DISPLAY_EDGE), material.accent(colors.LIT));
    }

    /** 山海：先画低强度字形柔光，再画原文字；在玻璃底板之后调用，不对文字做背景模糊。 */
    public static void drawString(GuiGraphics g, Font font, String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) return;
        drawString(g, font, Component.literal(text).getVisualOrderText(), x, y, color);
    }

    /** 同上，保留消费端已经排版的富文本与双向文字。 */
    public static void drawString(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color) {
        g.flush();
        drawTextHaloInBatch(g, font, text, x, y, color);
        g.flush();
        g.drawString(font, text, x, y, color, false);
        g.flush();
    }

    /** Adds softly edged glyphs to the current text batch. The caller owns flushing and draw order. */
    public static void drawStringInBatch(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color) {
        drawTextHaloInBatch(g, font, text, x, y, color);
        g.drawString(font, text, x, y, color, false);
    }

    /** Queues only the decoration so a group of labels can paint every fill after the halo batch. */
    public static void drawTextHaloInBatch(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color) {
        int glow = textGlowColor(color);
        if (glow == 0) return;
        FormattedCharSequence halo = sink -> text.accept((index, style, glyph) ->
                sink.accept(index, style.withColor(glow & 0xffffff), glyph));
        var matrix = g.pose().last().pose();
        var buffers = g.bufferSource();
        // Use the same GUI text layer as the fill. The entity outline path uses a polygon-offset
        // render type for the fill, which can be rejected by depth already written by glass.
        font.drawInBatch(halo, x - .5f, y, glow, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(halo, x + .5f, y, glow, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(halo, x, y - .5f, glow, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(halo, x, y + .5f, glow, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }

    static int textGlowColor(int color) {
        // Font.adjustColor 将 alpha 0..3 当成不透明；柔光淡出到这个范围时必须停止绘制。
        int alpha = (color & 0xFC000000) == 0 ? 255 : color >>> 24;
        int glowAlpha = Math.round(12f * alpha / 255f);
        return glowAlpha < 4 ? 0 : glowAlpha << 24 | 0xFFFFFF;
    }

    static int clearControlFill(float hover, boolean standalone) {
        GlassColors colors = GlassColors.CLEAR;
        int fill = GuiMotion.lerpColor(colors.BUTTON_FILL, colors.BUTTON_FILL_HOVER, hover);
        return standalone ? (colors.PANEL_FILL & 0xFF000000) | (fill & 0xFFFFFF) : fill;
    }

    private static void plate(GuiGraphics g, int x, int y, int w, int h,
                              int fill, int edge, int shade, int highlight) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + 1, x + 1, y + h, edge);
        g.fill(x + 1, y + h - 1, x + w, y + h, shade);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, shade);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, highlight);
    }
}
