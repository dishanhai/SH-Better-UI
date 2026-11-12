// Author: dishanhai
package com.dishanhai.sh_better_ui.client.blur.mixin;

import com.dishanhai.sh_better_ui.client.blur.BlurFrame;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
abstract class GuiRenderStateBlurMixin implements BlurFrame {
    @Shadow private int firstStratumAfterBlur;
    @Unique private boolean shbui$captureOnly;
    @Unique private @Nullable GpuTextureView shbui$capture;

    @Override public boolean shbui$hasBoundary() { return firstStratumAfterBlur != Integer.MAX_VALUE; }
    @Override public boolean shbui$captureOnly() { return shbui$captureOnly; }
    @Override public @Nullable GpuTextureView shbui$capture() { return shbui$capture; }

    @Override
    public void shbui$capture(GpuTextureView view, boolean captureOnly) {
        shbui$capture = view;
        shbui$captureOnly = captureOnly;
    }

    @Inject(method = "blurBeforeThisStratum", at = @At("HEAD"), cancellable = true)
    private void shbui$reuseCaptureBoundary(CallbackInfo callback) {
        if (shbui$captureOnly) {
            // A later vanilla request can enable its blur on the existing capture boundary.
            shbui$captureOnly = false;
            callback.cancel();
        }
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void shbui$resetCapture(CallbackInfo callback) {
        shbui$captureOnly = false;
        shbui$capture = null;
    }
}
