// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.display;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

/** Extracts the material demonstrations with the same texture pipeline on both graphics backends. */
@EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT)
public final class MaterialEffects {
    private static final RenderPipeline PIPELINE = MaterialPipeline.create();

    public enum Effect {
        DIRECTIONAL_BLUR, REFRACTION_WAVE, GRADIENT_SWEEP, COLOR_CYCLE, ENERGY_WAVE, PULSE,
        HOLOGRAM, GLITCH, SCANLINE, SPARKLE, DISSOLVE, OUTLINE
    }

    private MaterialEffects() {}

    @SubscribeEvent
    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PIPELINE);
    }

    public static void drawBackdrop(GuiGraphicsExtractor graphics, Bounds area, GpuTextureView snapshot,
                             Effect effect, int intensity, int angleOrPhase) {
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        submit(graphics, area, TextureSetup.singleTexture(snapshot, sampler), effect, intensity, angleOrPhase);
    }

    public static void drawIcon(GuiGraphicsExtractor graphics, Bounds area, GraphicIcon icon, Effect effect, int phase) {
        var svg = SvgIconTexture.find(icon.texture(), Math.max(area.width(), area.height()));
        var texture = Minecraft.getInstance().getTextureManager().getTexture(svg == null ? icon.texture() : svg.id());
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(svg == null ? FilterMode.NEAREST : FilterMode.LINEAR);
        submit(graphics, area, TextureSetup.singleTexture(texture.getTextureView(), sampler), effect, 0, phase);
    }

    private static void submit(GuiGraphicsExtractor graphics, Bounds area, TextureSetup texture,
                               Effect effect, int intensity, int angleOrPhase) {
        var pose = new Matrix3x2f(graphics.pose());
        var scissor = graphics.peekScissorStack();
        var viewport = new ScreenRectangle(0, 0, graphics.guiWidth(), graphics.guiHeight());
        var clip = scissor == null ? viewport : viewport.intersection(scissor);
        if (clip == null) return;
        var bounds = new ScreenRectangle(area.x(), area.y(), area.width(), area.height())
                .transformMaxBounds(pose).intersection(clip);
        if (bounds == null) return;
        graphics.submitGuiElementRenderState(new Quad(pose, area, texture, effect, intensity, angleOrPhase,
                scissor, bounds));
    }

    private record Quad(Matrix3x2fc pose, Bounds area, TextureSetup textureSetup, Effect effect,
                        int intensity, int angleOrPhase, @Nullable ScreenRectangle scissorArea,
                        ScreenRectangle bounds) implements GuiElementRenderState {
        @Override public RenderPipeline pipeline() { return PIPELINE; }

        @Override
        public void buildVertices(VertexConsumer out) {
            var point = new Vector2f();
            vertex(out, point, area.x(), area.y(), 0, 0);
            vertex(out, point, area.x(), area.bottom(), 0, 1);
            vertex(out, point, area.right(), area.bottom(), 1, 1);
            vertex(out, point, area.right(), area.y(), 1, 0);
        }

        private void vertex(VertexConsumer out, Vector2f point, int x, int y, int u, int v) {
            pose.transformPosition(x, y, point);
            // The shader receives effect parameters per quad, including when the renderer batches the icons.
            out.addVertex(point.x, point.y, effect.ordinal())
                    .setUv(u, v)
                    .setUv1(area.width(), area.height())
                    .setUv2(intensity, angleOrPhase);
        }
    }
}
