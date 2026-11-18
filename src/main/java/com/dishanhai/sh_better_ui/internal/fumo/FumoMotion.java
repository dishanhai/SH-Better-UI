// Author: dishanhai
package com.dishanhai.sh_better_ui.internal.fumo;

import com.mojang.blaze3d.vertex.PoseStack;

final class FumoMotion {
    private static final int SQUASH_TICKS = 10;

    private FumoMotion() {}

    static float squash(long tick, float partialTick) {
        double beat = (Math.floorMod(tick, SQUASH_TICKS) + partialTick) / SQUASH_TICKS;
        return (float) ((1 - Math.cos(beat * Math.PI * 2)) * 0.5);
    }

    static void apply(PoseStack pose, float squash) {
        pose.translate(0.5, 0.08F * squash, 0.5);
        pose.scale(1 + 0.18F * squash, 1 - 0.27F * squash, 1 + 0.12F * squash);
        pose.translate(-0.5, 0, -0.5);
    }
}
