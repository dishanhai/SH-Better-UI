// Author: dishanhai
package com.dishanhai.sh_better_ui.client.blur.mixin;

import com.dishanhai.sh_better_ui.client.blur.BackdropCapture;
import com.dishanhai.sh_better_ui.client.blur.BlurFrame;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiRenderer.class)
abstract class GuiRendererBlurMixin {
    @Shadow @Final private GuiRenderState renderState;

    @Redirect(method = "draw", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;processBlurEffect()V"))
    private void shbui$captureBetweenGuiRanges(GameRenderer gameRenderer) {
        var frame = (BlurFrame) renderState;
        if (!frame.shbui$captureOnly()) gameRenderer.processBlurEffect();
        var capture = frame.shbui$capture();
        if (capture != null) BackdropCapture.copy(capture);
    }
}
