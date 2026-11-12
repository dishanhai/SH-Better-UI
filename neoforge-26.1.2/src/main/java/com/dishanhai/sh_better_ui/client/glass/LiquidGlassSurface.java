// Author: dishanhai
package com.dishanhai.sh_better_ui.client.glass;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.blur.BackdropCapture;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Local optical glass for the native GUI extractor. Foreground text is drawn after the surface. */
@EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT)
public final class LiquidGlassSurface {
    private static final RenderPipeline PIPELINE = LiquidGlassPipeline.create();
    private static final int MAX_OPTICAL_EXTENT = Short.MAX_VALUE;

    private LiquidGlassSurface() {}

    @SubscribeEvent
    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PIPELINE);
    }

    /**
     * Draws from the current frame's background snapshot. Draw the scene before the first glass
     * surface and place foreground controls afterwards. All glass on the foreground side samples
     * that same snapshot. Tint alpha is the amount of colored absorption, not surface opacity.
     */
    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            float radius, int tint, int edge, GlassOptics optics, UiPreferences preferences) {
        draw(graphics, x, y, width, height, radius, tint, edge, optics, preferences, 1f, true);
    }

    /** Uses a snapshot prepared by the host. Returns false for the caller to draw its own plate. */
    public static boolean drawPrepared(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                       float radius, int tint, int edge, GlassOptics optics, UiPreferences preferences,
                                       float opacity) {
        return draw(graphics, x, y, width, height, radius, tint, edge, optics, preferences, opacity, false);
    }

    private static boolean draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                float radius, int tint, int edge, GlassOptics optics, UiPreferences preferences,
                                float opacity, boolean prepareBackdrop) {
        Objects.requireNonNull(optics, "optics");
        Objects.requireNonNull(preferences, "preferences");
        if (width <= 0 || height <= 0 || width > Geometry2.MAX_COORDINATE || height > Geometry2.MAX_COORDINATE
                || x > Integer.MAX_VALUE - width || y > Integer.MAX_VALUE - height
                || !Float.isFinite(radius) || !(opacity > 0)) return false;
        opacity = Math.min(opacity, 1f);
        float corner = RoundedCorners.fit(width, height, radius);
        boolean opaque = optics.transmission() == 0;
        if (!preferences.useBackdropBlur() || opaque
                || width > MAX_OPTICAL_EXTENT || height > MAX_OPTICAL_EXTENT) {
            if (prepareBackdrop) {
                fallback(graphics, x, y, width, height, corner, opaque ? tint | 0xFF000000 : tint,
                        edge, preferences, opacity);
            }
            return false;
        }

        var pose = new Matrix3x2f(graphics.pose());
        if (!pose.isFinite() || pose.determinant() == 0) return false;
        var scissor = graphics.peekScissorStack();
        var viewport = new ScreenRectangle(0, 0, graphics.guiWidth(), graphics.guiHeight());
        var clip = scissor == null ? viewport : viewport.intersection(scissor);
        if (clip == null) return false;
        var bounds = clippedBounds(pose, clip, x, y, width, height);
        if (bounds.width() == 0 || bounds.height() == 0) return false;
        var snapshot = prepareBackdrop ? BackdropCapture.request(graphics) : BackdropCapture.current(graphics);
        if (snapshot == null) {
            if (prepareBackdrop) fallback(graphics, x, y, width, height, corner, tint, edge, preferences, opacity);
            return false;
        }

        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        graphics.submitGuiElementRenderState(new Surface(pose, x, y, width, height, corner, tint, optics, opacity,
                TextureSetup.singleTexture(snapshot, sampler), scissor, bounds));
        if ((edge >>> 24) != 0) {
            RoundedCorners.draw(graphics, x, y, width, height, corner, 0.75f, fade(edge, opacity), 0);
        }
        return true;
    }

    private static ScreenRectangle clippedBounds(Matrix3x2fc pose, ScreenRectangle clip, int x, int y, int width, int height) {
        float halfWidth = width * 0.5f, halfHeight = height * 0.5f;
        var center = pose.transformPosition(x + halfWidth, y + halfHeight, new Vector2f());
        if (!center.isFinite()) return ScreenRectangle.empty();
        float extentX = Math.abs(pose.m00()) * halfWidth + Math.abs(pose.m10()) * halfHeight;
        float extentY = Math.abs(pose.m01()) * halfWidth + Math.abs(pose.m11()) * halfHeight;
        int left = (int) Math.floor(Math.clamp(center.x - extentX, clip.left(), clip.right()));
        int right = (int) Math.ceil(Math.clamp(center.x + extentX, clip.left(), clip.right()));
        int top = (int) Math.floor(Math.clamp(center.y - extentY, clip.top(), clip.bottom()));
        int bottom = (int) Math.ceil(Math.clamp(center.y + extentY, clip.top(), clip.bottom()));
        return new ScreenRectangle(left, top, right - left, bottom - top);
    }

    private static void fallback(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                 float radius, int tint, int edge, UiPreferences preferences, float opacity) {
        boolean contrast = preferences.highContrast();
        int fill = contrast ? tint | 0xFF000000 : tint;
        int stroke = contrast ? edge | 0xFF000000 : edge;
        RoundedCorners.draw(graphics, x, y, width, height, radius, contrast ? 1.5f : 1f,
                fade(stroke, opacity), fade(fill, opacity));
    }

    private static int fade(int color, float opacity) {
        return (color & 0xFFFFFF) | Math.round((color >>> 24) * opacity) << 24;
    }

    private record Surface(Matrix3x2fc pose, int x, int y, int width, int height, float radius, int tint,
                           GlassOptics optics, float opacity, TextureSetup textureSetup, @Nullable ScreenRectangle scissorArea,
                           ScreenRectangle bounds) implements GuiElementRenderState {
        @Override public RenderPipeline pipeline() { return PIPELINE; }

        @Override
        public void buildVertices(VertexConsumer out) {
            var point = new Vector2f();
            vertex(out, point, x, y, 0, 0);
            vertex(out, point, x, (float) y + height, 0, 1);
            vertex(out, point, (float) x + width, (float) y + height, 1, 1);
            vertex(out, point, (float) x + width, y, 1, 0);
        }

        private void vertex(VertexConsumer out, Vector2f point, float px, float py, int u, int v) {
            pose.transformPosition(px, py, point);
            // Vertex attributes carry per-surface parameters through the GUI batch.
            out.addVertex(point.x, point.y, radius)
                    .setColor(tint)
                    .setUv(u, v)
                    .setUv1(width, height)
                    .setUv2(pack(optics.refraction() / 32, optics.blurRadius() / 32),
                            pack(optics.transmission(), optics.lensing()))
                    .setNormal(optics.scattering(), optics.edgeHighlight(), optics.thickness() / 32)
                    .setLineWidth(opacity);
        }

        private static int pack(float first, float second) {
            return Math.round(first * 255) | Math.round(second * 255) << 8;
        }
    }
}
