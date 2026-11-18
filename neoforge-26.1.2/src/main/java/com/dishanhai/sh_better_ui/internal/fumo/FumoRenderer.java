// Author: dishanhai
package com.dishanhai.sh_better_ui.internal.fumo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Submits the dancing plush through the same model path as vanilla block displays. */
public final class FumoRenderer implements BlockEntityRenderer<DishanhaiFumoBlock.DanceEntity, FumoRenderer.State> {
    private static final BlockDisplayContext DISPLAY_CONTEXT = BlockDisplayContext.create();
    private final BlockModelResolver blocks;

    public FumoRenderer(BlockEntityRendererProvider.Context context) {
        blocks = context.blockModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRender(DishanhaiFumoBlock.DanceEntity plush, Vec3 cameraPosition) {
        return plush.getBlockState().getValue(DishanhaiFumoBlock.DANCING)
                && BlockEntityRenderer.super.shouldRender(plush, cameraPosition);
    }

    @Override
    public int getViewDistance() {
        return 512;
    }

    @Override
    public void extractRenderState(DishanhaiFumoBlock.DanceEntity plush, State state, float partialTick,
                                   Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(plush, state, partialTick, cameraPosition, breaking);
        var level = plush.getLevel();
        var block = plush.getBlockState();
        if (level == null || !block.getValue(DishanhaiFumoBlock.DANCING)) {
            state.model.clear();
            return;
        }
        state.squash = DishanhaiFumoBlock.squash(level.getGameTime(), partialTick);
        blocks.update(state.model, block, DISPLAY_CONTEXT);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.model.isEmpty()) return;
        float squash = state.squash;
        pose.pushPose();
        pose.translate(0.5, 0.08F * squash, 0.5);
        pose.scale(1 + 0.18F * squash, 1 - 0.27F * squash, 1 + 0.12F * squash);
        pose.translate(-0.5, 0, -0.5);
        state.model.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    public static final class State extends BlockEntityRenderState {
        private final BlockModelRenderState model = new BlockModelRenderState();
        private float squash;
    }
}
