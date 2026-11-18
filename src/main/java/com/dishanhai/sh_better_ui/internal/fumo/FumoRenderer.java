// Author: dishanhai
package com.dishanhai.sh_better_ui.internal.fumo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/** Draws the baked plush in an entity render layer while it dances. */
public final class FumoRenderer implements BlockEntityRenderer<DishanhaiFumoBlock.DanceEntity> {
    private static final RenderType LAYER = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
    private final BlockRenderDispatcher blocks;

    public FumoRenderer(BlockEntityRendererProvider.Context context) {
        blocks = context.getBlockRenderDispatcher();
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
    public void render(DishanhaiFumoBlock.DanceEntity plush, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        var level = plush.getLevel();
        var state = plush.getBlockState();
        if (level == null || !state.getValue(DishanhaiFumoBlock.DANCING)) return;

        float squash = DishanhaiFumoBlock.squash(level.getGameTime(), partialTick);
        pose.pushPose();
        pose.translate(0.5, 0.08F * squash, 0.5);
        pose.scale(1 + 0.18F * squash, 1 - 0.27F * squash, 1 + 0.12F * squash);
        pose.translate(-0.5, 0, -0.5);
        blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(LAYER), state,
                blocks.getBlockModel(state), 1, 1, 1, light, overlay);
        pose.popPose();
    }
}
