// Author: dishanhai
package com.dishanhai.sh_better_ui.client.blur;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

/** A draw-range boundary with no pixels. The native renderer runs the action between GPU render passes. */
public record GuiReadbackMarker(Runnable capture) implements GuiElementRenderState {
    public static void submit(GuiGraphicsExtractor graphics, Runnable capture) {
        graphics.nextStratum();
        graphics.submitGuiElementRenderState(new GuiReadbackMarker(capture));
        graphics.nextStratum();
    }

    @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
    @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
    @Override public ScreenRectangle scissorArea() { return null; }
    @Override public ScreenRectangle bounds() { return new ScreenRectangle(0, 0, 1, 1); }
    @Override public void buildVertices(VertexConsumer vertices) {}
}
