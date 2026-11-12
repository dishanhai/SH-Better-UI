// Author: dishanhai
package com.dishanhai.sh_better_ui.client.spatial;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.kernel.api.geometry.FilledGeometry;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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

/** Submits shared 2D meshes to the current Minecraft GUI layer. */
@EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT)
public final class GuiGeometry {
    private static final RenderPipeline PIPELINE = GuiGeometryPipeline.create();

    private GuiGeometry() {}

    @SubscribeEvent
    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PIPELINE);
    }

    /** Call during GUI extraction. Mesh positions use the current pose's local coordinates. */
    public static void fill(GuiGraphicsExtractor graphics, FilledGeometry<Local> mesh, int argb) {
        if ((argb >>> 24) == 0 || mesh.bounds().isEmpty()) return;
        var pose = new Matrix3x2f(graphics.pose());
        if (!pose.isFinite() || pose.determinant() == 0) return;
        var scissor = graphics.peekScissorStack();
        var viewport = new ScreenRectangle(0, 0, graphics.guiWidth(), graphics.guiHeight());
        var clip = scissor == null ? viewport : viewport.intersection(scissor);
        if (clip == null) return;
        var bounds = bounds(mesh.bounds().orElseThrow(), pose, clip);
        if (bounds.width() == 0 || bounds.height() == 0) return;
        graphics.submitGuiElementRenderState(new MeshState(mesh, argb, pose, scissor, bounds));
    }

    private static ScreenRectangle bounds(Bounds<Local> box, Matrix3x2fc pose, ScreenRectangle clip) {
        float left = (float) box.left();
        float top = (float) box.top();
        float right = (float) box.right();
        float bottom = (float) box.bottom();
        var a = pose.transformPosition(left, top, new Vector2f());
        var b = pose.transformPosition(right, top, new Vector2f());
        var c = pose.transformPosition(left, bottom, new Vector2f());
        var d = pose.transformPosition(right, bottom, new Vector2f());
        if (!a.isFinite() || !b.isFinite() || !c.isFinite() || !d.isFinite()) return ScreenRectangle.empty();
        int x0 = (int) Math.floor(Math.clamp(Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x)), clip.left(), clip.right()));
        int y0 = (int) Math.floor(Math.clamp(Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y)), clip.top(), clip.bottom()));
        int x1 = (int) Math.ceil(Math.clamp(Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x)), clip.left(), clip.right()));
        int y1 = (int) Math.ceil(Math.clamp(Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y)), clip.top(), clip.bottom()));
        return new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
    }

    private record MeshState(FilledGeometry<Local> mesh, int color, Matrix3x2fc pose,
                             @Nullable ScreenRectangle scissorArea, ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override public RenderPipeline pipeline() { return PIPELINE; }
        @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }

        @Override
        public void buildVertices(VertexConsumer out) {
            for (var triangle : mesh.triangles()) {
                out.addVertexWith2DPose(pose, (float) triangle.a().x(), (float) triangle.a().y()).setColor(color);
                out.addVertexWith2DPose(pose, (float) triangle.b().x(), (float) triangle.b().y()).setColor(color);
                out.addVertexWith2DPose(pose, (float) triangle.c().x(), (float) triangle.c().y()).setColor(color);
            }
        }
    }
}
