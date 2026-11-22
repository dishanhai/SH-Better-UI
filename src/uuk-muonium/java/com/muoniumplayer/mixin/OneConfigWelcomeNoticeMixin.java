// Author: dishanhai
package com.muoniumplayer.mixin;

import com.dishanhai.sh_better_ui.kernel.internal.WelcomeShortcutNotice;
import com.muoniumplayer.core.screens.hud.OneConfigHudBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.internal.OneConfig", remap = false)
abstract class OneConfigWelcomeNoticeMixin {
    @Inject(method = "showFirstLaunchNotification", at = @At(value = "INVOKE",
            target = "Lorg/polyfrost/oneconfig/internal/OneConfigConfig;markFirstLaunchShown()V",
            shift = At.Shift.AFTER), cancellable = true, require = 0)
    private static void shbui$showWelcomeNotice(CallbackInfo ci) {
        WelcomeShortcutNotice.show(OneConfigHudBridge::openLastSession);
        ci.cancel();
    }

    @Inject(method = "dismissFirstLaunchToast", at = @At("HEAD"), require = 0)
    private static void shbui$dismissWelcomeNotice(CallbackInfo ci) {
        WelcomeShortcutNotice.dismiss();
    }
}
