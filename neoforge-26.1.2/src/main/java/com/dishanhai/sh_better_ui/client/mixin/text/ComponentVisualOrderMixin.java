// Author: dishanhai
package com.dishanhai.sh_better_ui.client.mixin.text;

import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import com.dishanhai.sh_better_ui.client.gui.text.UiTextEffects;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MutableComponent.class)
abstract class ComponentVisualOrderMixin {
    @Inject(method = "getVisualOrderText", at = @At("HEAD"), cancellable = true)
    private void shbui$richSequence(CallbackInfoReturnable<FormattedCharSequence> result) {
        Component source = (MutableComponent) (Object) this;
        Component prepared = UiRichText.prepare(UiTextEffects.normalize(source));
        if (source != prepared) result.setReturnValue(Language.getInstance().getVisualOrder(prepared));
    }
}
