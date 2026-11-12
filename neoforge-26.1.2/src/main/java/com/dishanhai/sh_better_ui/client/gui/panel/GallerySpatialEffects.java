// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Kind;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.ShadowType;
import com.dishanhai.sh_better_ui.spatial.internal.Scene.Paint;
import com.dishanhai.sh_better_ui.spatial.internal.SpatialMesh.Vertex;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Submits ordered Scene paint and regional effects as native GUI elements. */
@EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT)
public final class GallerySpatialEffects {
    private static final int RADIAL = 4;
    private static final int WAVES = 5;
    private static final int REVEAL_IMAGE = 6;
    private static final int REVEAL_COVER = 7;
    private static final int ELLIPSE = 8;
    private static final RenderPipeline PAINT = GallerySpatialPipeline.create(false);
    private static final RenderPipeline REVEAL_PAINT = GallerySpatialPipeline.create(true);
    private static final Identifier REVEAL_TEXTURE = Identifier.fromNamespaceAndPath(ShBetterUi.MOD_ID,
            "material_catalog/fixtures/liquid-glass.png");

    private GallerySpatialEffects() {}

    @SubscribeEvent
    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PAINT);
        event.registerPipeline(REVEAL_PAINT);
    }

    static void radial(GuiGraphicsExtractor graphics, Bounds area, int x, int y, int radius, int argb) {
        submitQuad(graphics, area, new Parameters(RADIAL | radius << 4, argb, area.width(), area.height(), x, y),
                PAINT, TextureSetup.noTexture());
    }

    static void waves(GuiGraphicsExtractor graphics, Bounds area, int phase, int shape, int x, int y, int argb) {
        submitQuad(graphics, area, new Parameters(WAVES | shape << 4 | phase << 6, argb, area.width(), area.height(), x, y),
                PAINT, TextureSetup.noTexture());
    }

    static void reveal(GuiGraphicsExtractor graphics, Bounds area, @Nullable GpuTextureView snapshot,
                       int progress, int centerX, int centerY, boolean contrast) {
        var texture = Minecraft.getInstance().getTextureManager().getTexture(REVEAL_TEXTURE).getTextureView();
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        int flags = (snapshot == null ? 0 : 1) | (contrast ? 2 : 0);
        var coverTexture = TextureSetup.singleTexture(snapshot == null ? texture : snapshot, sampler);
        submitQuad(graphics, area, new Parameters(REVEAL_COVER | flags << 4,
                0xFFFFFFFF, area.width(), area.height(), 0, 0), REVEAL_PAINT, coverTexture);
        if (progress > 0) {
            var imageTexture = TextureSetup.singleTexture(texture, sampler);
            submitQuad(graphics, area, new Parameters(REVEAL_IMAGE | flags << 4 | progress << 6,
                    0xFFFFFFFF, area.width(), area.height(), centerX, centerY), REVEAL_PAINT, imageTexture);
        }
    }

    static void spatial(GuiGraphicsExtractor graphics, Paint paint, List<Vertex> mesh) {
        var shape = paint.shape();
        int metadata = shape.kind() == Kind.ELLIPSE ? ELLIPSE : 0;
        // Position.z carries metadata; UV0 and UV1 retain the untransformed local point and size.
        // Only the bounded gallery styles use this compact vertex layout.
        metadata |= (int) Math.round(shape.topLeft() * 4) << 4;
        int first, second;
        if (paint.type() == ShadowType.SURFACE) {
            metadata |= (int) Math.round(paint.roughness() * 63) << 12;
            metadata |= (int) Math.round((paint.depth() + 1) * 31.5) << 18;
            first = packBytes(paint.highlight(), paint.border());
            second = packBytes(paint.lightX() * .5 + .5, paint.lightY() * .5 + .5);
        } else {
            metadata |= paint.type() == ShadowType.INNER ? 2 : paint.clipNode() ? 3 : 1;
            metadata |= (int) Math.round(paint.blur() * 4) << 12;
            metadata |= ((int) Math.round(paint.spread() * 2) + 16) << 18;
            if (paint.soft()) metadata |= 1 << 23;
            first = (int) Math.round(paint.x() * 16);
            second = (int) Math.round(paint.y() * 16);
        }
        submit(graphics, mesh, new Parameters(metadata, paint.color(), (int) shape.width(), (int) shape.height(),
                first, second), PAINT, TextureSetup.noTexture());
    }

    private static int packBytes(double low, double high) {
        int a = (int) Math.round(Math.clamp(low, 0, 1) * 255);
        int b = (int) Math.round(Math.clamp(high, 0, 1) * 255);
        return a | b << 8;
    }

    private static void submitQuad(GuiGraphicsExtractor graphics, Bounds area, Parameters values,
                                   RenderPipeline pipeline, TextureSetup texture) {
        submit(graphics, List.of(new Vertex(area.x(), area.y(), 0, 0),
                new Vertex(area.x(), area.bottom(), 0, area.height()),
                new Vertex(area.right(), area.bottom(), area.width(), area.height()),
                new Vertex(area.right(), area.y(), area.width(), 0)), values, pipeline, texture);
    }

    private static void submit(GuiGraphicsExtractor graphics, List<Vertex> mesh, Parameters values,
                               RenderPipeline pipeline, TextureSetup texture) {
        if (mesh.size() < 3 || values.width() <= 0 || values.height() <= 0) return;
        var pose = new Matrix3x2f(graphics.pose());
        var scissor = graphics.peekScissorStack();
        var viewport = new ScreenRectangle(0, 0, graphics.guiWidth(), graphics.guiHeight());
        var clip = scissor == null ? viewport : viewport.intersection(scissor);
        if (clip == null) return;
        var point = new Vector2f();
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (var vertex : mesh) {
            pose.transformPosition((float) vertex.x(), (float) vertex.y(), point);
            left = Math.min(left, point.x);
            top = Math.min(top, point.y);
            right = Math.max(right, point.x);
            bottom = Math.max(bottom, point.y);
        }
        int x = (int) Math.floor(left), y = (int) Math.floor(top);
        var bounds = new ScreenRectangle(x, y, (int) Math.ceil(right) - x, (int) Math.ceil(bottom) - y)
                .intersection(clip);
        if (bounds == null || bounds.width() == 0 || bounds.height() == 0) return;
        graphics.submitGuiElementRenderState(new Element(mesh, pose, values, pipeline, texture, scissor, bounds));
    }

    private record Parameters(int metadata, int color, int width, int height, int a, int b) {}

    private record Element(List<Vertex> mesh, Matrix3x2fc pose, Parameters values, RenderPipeline pipeline,
                           TextureSetup textureSetup, @Nullable ScreenRectangle scissorArea, ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer out) {
            var point = new Vector2f();
            if (mesh.size() == 4) {
                for (var vertex : mesh) vertex(out, point, vertex);
                return;
            }
            // A clipped convex polygon becomes a triangle fan with a degenerate half-quad.
            for (int index = 1; index < mesh.size() - 1; index++) {
                vertex(out, point, mesh.getFirst());
                vertex(out, point, mesh.get(index));
                vertex(out, point, mesh.get(index + 1));
                vertex(out, point, mesh.get(index + 1));
            }
        }

        private void vertex(VertexConsumer out, Vector2f point, Vertex vertex) {
            pose.transformPosition((float) vertex.x(), (float) vertex.y(), point);
            out.addVertex(point.x, point.y, values.metadata()).setColor(values.color())
                    .setUv((float) vertex.u(), (float) vertex.v())
                    .setUv1(values.width(), values.height()).setUv2(values.a(), values.b());
        }
    }
}
