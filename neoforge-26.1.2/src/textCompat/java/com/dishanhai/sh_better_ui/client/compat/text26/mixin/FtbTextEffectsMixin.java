// Author: dishanhai
package com.dishanhai.sh_better_ui.client.compat.text26.mixin;

import com.dishanhai.sh_better_ui.client.compat.text26.GuideMe26Text;

import com.dishanhai.sh_better_ui.client.gui.text.UiFormattedText;
import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Function;

/** Optional FTB Library 26.1.2 parser bridge; FTB retains ownership of its formatting grammar. */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.util.TextComponentParser", remap = false)
abstract class FtbTextEffectsMixin {
    @Shadow(remap = false)
    private static Component parse0(String text, Function<String, Component> substitutes) {
        throw new AssertionError();
    }

    // If FTB is installed, a changed vendor signature must fail visibly rather than silently disable support.
    @Inject(method = "parse(Ljava/lang/String;Ljava/util/function/Function;)Lnet/minecraft/network/chat/Component;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void shbui$parseEffects(String raw, Function<String, Component> substitutes,
                                         CallbackInfoReturnable<Component> result) {
        if (raw == null) return;
        Component legacy = UiFormattedText.parseLegacy(raw);
        if (legacy == null && !raw.contains("<rich:")) return;
        Component formatted = legacy == null ? UiRichText.parse(raw) : UiRichText.prepare(legacy);
        var output = Component.empty();
        formatted.visit((style, text) -> {
            var part = parse0(text, substitutes).copy();
            part.setStyle(part.getStyle().applyTo(style));
            output.append(part);
            return Optional.empty();
        }, Style.EMPTY);
        result.setReturnValue(output);
    }
}
