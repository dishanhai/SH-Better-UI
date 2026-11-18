// Author: dishanhai
package com.dishanhai.sh_better_ui.internal.fumo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;

final class FumoMotion {
    private static final int CYCLE_TICKS = 20;

    private FumoMotion() {}

    static float swing(long tick, float partialTick) {
        double phase = (Math.floorMod(tick, CYCLE_TICKS) + partialTick) / CYCLE_TICKS;
        return (float) Math.sin(phase * Math.PI * 2);
    }

    static void apply(PoseStack pose, Direction facing, float swing) {
        float jump = Math.abs(swing);
        float compression = 1 - jump;
        compression *= compression;
        float shift = 0.095F * swing;
        boolean northSouth = facing.getAxis() == Direction.Axis.Z;
        pose.translate(0.5 + (northSouth ? shift : 0), 0.36F * jump, 0.5 + (northSouth ? 0 : shift));
        pose.mulPose((northSouth ? Axis.ZP : Axis.XN).rotationDegrees(-11 * swing));
        float width = 1 - 0.07F * jump + 0.18F * compression;
        float height = 1 + 0.12F * jump - 0.21F * compression;
        float depth = 1 - 0.05F * jump + 0.11F * compression;
        pose.scale(northSouth ? width : depth, height, northSouth ? depth : width);
        pose.translate(-0.5, 0, -0.5);
    }
}
