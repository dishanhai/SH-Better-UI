// Author: dishanhai
package com.dishanhai.sh_better_ui.mixin.client;

import com.mojang.blaze3d.opengl.GlBackend;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.logging.LogUtils;
import net.minecraft.client.PreferredGraphicsApi;
import net.neoforged.fml.loading.EarlyLoadingScreenController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PreferredGraphicsApi.class)
abstract class EarlyWindowGraphicsMixin {
    @Inject(method = "getBackendsToTry", at = @At("HEAD"), cancellable = true)
    private void shBetterUi$useEarlyWindowBackend(CallbackInfoReturnable<GpuBackend[]> callback) {
        if (EarlyLoadingScreenController.current() == null) return;
        // NeoForge 26.2 transfers the OpenGL early window to Minecraft, which Vulkan cannot use.
        if ((Object) this == PreferredGraphicsApi.VULKAN) {
            LogUtils.getLogger().warn("Using OpenGL for NeoForge's early window. "
                    + "Set earlyWindowControl = false in config/fml.toml to use Vulkan.");
        }
        callback.setReturnValue(new GpuBackend[] {new GlBackend()});
    }
}
