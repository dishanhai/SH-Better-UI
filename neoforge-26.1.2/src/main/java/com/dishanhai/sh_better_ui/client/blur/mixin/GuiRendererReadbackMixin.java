// Author: dishanhai
package com.dishanhai.sh_better_ui.client.blur.mixin;

import com.dishanhai.sh_better_ui.client.blur.GuiReadbackMarker;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Supplier;

@Mixin(GuiRenderer.class)
abstract class GuiRendererReadbackMixin {
    @Shadow @Final private List<?> meshesToDraw;
    @Shadow private RenderPipeline previousPipeline;
    @Shadow private TextureSetup previousTextureSetup;
    @Shadow private ScreenRectangle previousScissorArea;
    @Shadow private BufferBuilder bufferBuilder;
    @Shadow protected abstract void recordMesh(BufferBuilder builder, RenderPipeline pipeline, TextureSetup textures, ScreenRectangle clip);
    @Shadow protected abstract void executeDrawRange(Supplier<String> label, RenderTarget target,
            GpuBufferSlice fog, GpuBufferSlice transforms, GpuBuffer indices, VertexFormat.IndexType indexType, int start, int end);
    @Unique private final TreeMap<Integer, List<Runnable>> shbui$readbacks = new TreeMap<>();

    @Inject(method = "prepare", at = @At("HEAD"))
    private void shbui$resetReadbacks(CallbackInfo callback) {
        shbui$readbacks.clear();
    }

    @Inject(method = "addElementToMesh", at = @At("HEAD"), cancellable = true)
    private void shbui$markBoundary(GuiElementRenderState state, CallbackInfo callback) {
        if (!(state instanceof GuiReadbackMarker marker)) return;
        if (bufferBuilder != null) {
            recordMesh(bufferBuilder, previousPipeline, previousTextureSetup, previousScissorArea);
            bufferBuilder = null;
        }
        previousPipeline = null;
        previousTextureSetup = null;
        previousScissorArea = null;
        shbui$readbacks.computeIfAbsent(meshesToDraw.size(), ignored -> new ArrayList<>()).add(marker.capture());
        callback.cancel();
    }

    @Redirect(method = "draw", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;executeDrawRange(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;II)V"))
    private void shbui$drawAndCapture(GuiRenderer self, Supplier<String> label, RenderTarget target,
            GpuBufferSlice fog, GpuBufferSlice transforms, GpuBuffer indices, VertexFormat.IndexType indexType, int start, int end) {
        int cursor = start;
        for (var entry : shbui$readbacks.subMap(start, true, end, false).entrySet()) {
            int next = entry.getKey();
            if (next > cursor) executeDrawRange(label, target, fog, transforms, indices, indexType, cursor, next);
            entry.getValue().forEach(Runnable::run);
            cursor = next;
        }
        if (cursor < end) executeDrawRange(label, target, fog, transforms, indices, indexType, cursor, end);
    }
}
