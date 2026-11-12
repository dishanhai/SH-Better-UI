// Author: dishanhai
package com.dishanhai.sh_better_ui.mixin.client;

import net.minecraft.client.Options;
import net.minecraft.client.PreferredGraphicsApi;
import net.neoforged.fml.loading.EarlyLoadingScreenController;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(Options.class)
abstract class GraphicsPreferenceMixin {
    // Options.load can override the initial value, and Minecraft retains its startup recovery.
    @Redirect(
            method = "<init>",
            slice = @Slice(
                    from = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
                            target = "Lnet/minecraft/client/Options;preferredGraphicsBackendFromStartup:Lnet/minecraft/client/PreferredGraphicsApi;"),
                    to = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
                            target = "Lnet/minecraft/client/Options;preferredGraphicsBackend:Lnet/minecraft/client/OptionInstance;")),
            at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
                    target = "Lnet/minecraft/client/PreferredGraphicsApi;DEFAULT:Lnet/minecraft/client/PreferredGraphicsApi;"),
            require = 1)
    private PreferredGraphicsApi shBetterUi$initialGraphicsPreference() {
        return EarlyLoadingScreenController.current() == null
                ? PreferredGraphicsApi.VULKAN
                : PreferredGraphicsApi.DEFAULT;
    }
}
