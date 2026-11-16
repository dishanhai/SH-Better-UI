// Author: dishanhai
package com.dishanhai.sh_better_ui.client.mixin.text;

import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import com.dishanhai.sh_better_ui.client.gui.text.UiTextEffects;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
abstract class FontInlineItemMixin {
    @ModifyVariable(method = {"width(Lnet/minecraft/network/chat/FormattedText;)I",
            "split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;",
            "substrByWidth(Lnet/minecraft/network/chat/FormattedText;I)Lnet/minecraft/network/chat/FormattedText;",
            "splitIgnoringLanguage(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"},
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 4)
    private FormattedText shbui$richLayout(FormattedText text) {
        return UiRichText.prepare(text instanceof Component component ? UiTextEffects.normalize(component) : text);
    }

    @ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private FormattedCharSequence shbui$richWidth(FormattedCharSequence text) { return UiRichText.prepare(text); }

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void shbui$richStringWidth(String text, CallbackInfoReturnable<Integer> result) {
        if (text != null && text.contains("<rich:")) result.setReturnValue(((Font) (Object) this).width(UiRichText.parse(text)));
    }
}
