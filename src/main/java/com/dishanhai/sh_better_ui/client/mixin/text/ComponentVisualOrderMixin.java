package com.dishanhai.sh_better_ui.client.mixin.text;

import com.dishanhai.sh_better_ui.client.gui.text.GuideMeTextEffects;
import com.dishanhai.sh_better_ui.client.gui.text.VanillaTextEffects;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 山海：GuiGraphics 直接取视觉序列的路径也识别旧格式。 */
@Mixin(MutableComponent.class)
abstract class ComponentVisualOrderMixin {
    @Inject(method = {"getVisualOrderText()Lnet/minecraft/util/FormattedCharSequence;",
            "m_7532_()Lnet/minecraft/util/FormattedCharSequence;"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void shbui$visualEffects(CallbackInfoReturnable<FormattedCharSequence> result) {
        Component source = (MutableComponent) (Object) this;
        Component normalized = GuideMeTextEffects.applyTo(source);
        normalized = VanillaTextEffects.normalize(normalized);
        if (source != normalized) result.setReturnValue(Language.getInstance().getVisualOrder(normalized));
    }
}
