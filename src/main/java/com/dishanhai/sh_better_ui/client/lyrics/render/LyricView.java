// Adapted by dishanhai from Link-Lyric (beipuo).
// Source: https://github.com/beipuo/Link-Lyric/tree/1a7cd274c3e7afbb33c08c66414ec0def944643b
// See META-INF/third-party/link-lyric for licenses and upstream notices.
package com.dishanhai.sh_better_ui.client.lyrics.render;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.FloatingLyricRenderer;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.LyricFeed;
import com.dishanhai.sh_better_ui.client.lyrics.common.style.GlyphColors;
import com.dishanhai.sh_better_ui.client.lyrics.common.style.GlyphSpan;
import com.dishanhai.sh_better_ui.client.lyrics.common.style.LyricStyleConfig;
import com.dishanhai.sh_better_ui.client.lyrics.render.sdf.SdfGlyphProvider;
import com.dishanhai.sh_better_ui.client.lyrics.render.sdf.SdfRenderType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.joml.Matrix4f;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** 山海：把 Link-Lyric 绘制接口适配为项目共用的 GUI 坐标和偏好。 */
public final class LyricView {
    private LyricView() {}

    public static boolean draw(FloatingLyricRenderer renderer, GuiGraphics graphics, Font font,
                               Bounds area, UiPreferences preferences) {
        return draw(renderer, graphics, font, area, preferences, null);
    }

    public static boolean draw(FloatingLyricRenderer renderer, GuiGraphics graphics, Font font,
                               Bounds area, UiPreferences preferences, LyricFeed.Frame preview) {
        return drawScoped(renderer,graphics,font,area,preferences,preview,null,font.lineHeight+2);
    }

    /** Optional per-surface font. Existing renderers and their preferences are never mutated globally. */
    public static boolean drawScoped(FloatingLyricRenderer renderer, GuiGraphics graphics, Font font,
            Bounds area, UiPreferences preferences, LyricFeed.Frame preview, ResourceLocation fontId, int lineHeight) {
        if (area != null && (area.width() <= 0 || area.height() <= 0)) return false;
        var previousArea = renderer.previewArea();
        var previousStyle = renderer.style();
        renderer.setPreviewArea(area == null ? null
                : new FloatingLyricRenderer.Bounds(area.x(), area.y(), area.width(), area.height()));
        var style = preferences.highContrast()
                ? previousStyle.withSungColor(0xFFFFEB78).withUnsungColor(0xFFFFFFFF)
                        .withTranslationColor(0xFFFFFFFF).withOpacity(1f).withShadow(true)
                : previousStyle;
        renderer.setStyle(style);
        if (area != null) graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        try {
            SdfRenderType.setWeightPercent(style.weightPercent());
            SdfRenderType.setSoftShadow(style.softShadow());
            Painter painter = new Painter(graphics, font, fontId, Math.max(1,lineHeight));
            return preview == null ? renderer.render(painter, preferences.reducedMotion())
                    : renderer.render(painter, preview, preferences.reducedMotion());
        } finally {
            renderer.setPreviewArea(previousArea);
            renderer.setStyle(previousStyle);
            try {
                graphics.flush();
            } finally {
                if (area != null) graphics.disableScissor();
            }
        }
    }

    public static void registerShader(ResourceProvider resources,
                                      BiConsumer<ShaderInstance, Consumer<ShaderInstance>> register) {
        SdfGlyphProvider.reset();
        SdfRenderType.install(null);
        try {
            register.accept(new ShaderInstance(resources, ResourceLocation.tryParse("sh_better_ui:lyric_sdf_text"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), SdfRenderType::install);
        } catch (IOException e) {
            LoggerFactory.getLogger(LyricView.class).warn("Could not load the lyric SDF shader", e);
        }
    }

    static net.minecraft.network.chat.Component scopedText(String value,ResourceLocation fontId) {
        var text=net.minecraft.network.chat.Component.literal(value);
        return fontId==null?text:text.withStyle(style->style.withFont(fontId));
    }

    private record Painter(GuiGraphics graphics, Font font, ResourceLocation fontId, int scopedLineHeight) implements FloatingLyricRenderer.Painter {
        @Override public int screenWidth() { return graphics.guiWidth(); }
        @Override public int screenHeight() { return graphics.guiHeight(); }
        @Override public int textWidth(String text) { return fontId==null?font.width(text):font.width(scopedText(text,fontId)); }
        @Override public int charWidth(char c) { return fontId==null?font.width(String.valueOf(c)):font.width(scopedText(String.valueOf(c),fontId)); }
        @Override public int lineHeight() { return scopedLineHeight; }

        @Override public void drawText(String text, int x, int y, int color) {
            drawText(text, x, y, color, true);
        }

        @Override public void drawText(String text, int x, int y, int color, boolean shadow) {
            if ((color >>> 24) >= 4) {
                if(fontId==null)graphics.drawString(font,text,x,y,color,shadow);
                else graphics.drawString(font,scopedText(text,fontId),x,y,color,shadow);
            }
        }

        @Override public void drawTextScaled(String text, int x, int y, float scale, int color, boolean shadow) {
            var stack = graphics.pose();
            stack.pushPose();
            try {
                stack.translate(x, y, 0f);
                stack.scale(scale, scale, 1f);
                drawText(text, 0, 0, color, shadow);
            } finally {
                stack.popPose();
            }
        }

        @Override public void drawGlyphSpans(String glyph, float x, int y, float lift, GlyphSpan[] spans,
                                             int originX, boolean shadow) {
            drawGlyphSpans(glyph, x, y, lift, spans, originX, shadow, 0f, 0f, 0f, 0);
        }

        @Override public void drawGlyphSpans(String glyph, float x, int y, float lift, GlyphSpan[] spans,
                                             int originX, boolean shadow, float glow, float glowAlpha) {
            drawGlyphSpans(glyph, x, y, lift, spans, originX, shadow, glow, glowAlpha, 0f, 0);
        }

        @Override public void drawGlyphSpans(String glyph, float x, int y, float lift, GlyphSpan[] spans,
                                             int originX, boolean shadow, float glow, float glowAlpha,
                                             float strokeWidth, int strokeColor) {
            if (spans.length == 0) return;
            var stack = graphics.pose();
            stack.pushPose();
            try {
                stack.translate(0f, -lift, 0f);
                if (!GradientPainter.drawSpans(graphics, font, glyph, x, y, spans, originX, shadow,
                        glow, glowAlpha, strokeWidth, strokeColor, fontId)) {
                    drawText(glyph, Math.round(x), y, spans[0].startColor(), shadow);
                }
            } finally {
                stack.popPose();
            }
        }

        @Override public void drawGlyphGradient(String glyph, float x, int y, float lift, GlyphColors colors,
                                                boolean shadow) {
            drawGlyphGradient(glyph, x, y, lift, colors, shadow, 0f, 0f, 0f, 0);
        }

        @Override public void drawGlyphGradient(String glyph, float x, int y, float lift, GlyphColors colors,
                                                boolean shadow, float glow, float glowAlpha) {
            drawGlyphGradient(glyph, x, y, lift, colors, shadow, glow, glowAlpha, 0f, 0);
        }

        @Override public void drawGlyphGradient(String glyph, float x, int y, float lift, GlyphColors colors,
                                                boolean shadow, float glow, float glowAlpha,
                                                float strokeWidth, int strokeColor) {
            var stack = graphics.pose();
            stack.pushPose();
            try {
                stack.translate(0f, -lift, 0f);
                if (!GradientPainter.draw(graphics, font, glyph, x, y, colors, shadow,
                        glow, glowAlpha, strokeWidth, strokeColor, fontId)) {
                    drawText(glyph, Math.round(x), y, colors.left(), shadow);
                }
            } finally {
                stack.popPose();
            }
        }

        @Override public FloatingLyricRenderer.Pose2D pose() {
            var stack = graphics.pose();
            return new FloatingLyricRenderer.Pose2D() {
                @Override public void pushPose() { stack.pushPose(); }
                @Override public void popPose() { stack.popPose(); }
                @Override public void translate(float x, float y) { stack.translate(x, y, 0f); }
                @Override public void scale(float x, float y) { stack.scale(x, y, 1f); }
                @Override public void rotate(float degrees) { stack.mulPose(Axis.ZP.rotationDegrees(degrees)); }
                @Override public void shear(float x, float y) {
                    stack.last().pose().mul(new Matrix4f().m10(x).m01(y));
                }
            };
        }

        @Override public void fillRect(int x, int y, int width, int height, int color) {
            graphics.fill(x, y, x + width, y + height, color);
        }

        @Override public boolean supportsScissor() { return true; }
        @Override public void enableScissor(int x, int y, int width, int height) {
            graphics.enableScissor(x, y, x + width, y + height);
        }
        @Override public void disableScissor() { graphics.disableScissor(); }
    }
}
