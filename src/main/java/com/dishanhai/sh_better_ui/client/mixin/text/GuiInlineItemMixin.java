// Author: dishanhai
package com.dishanhai.sh_better_ui.client.mixin.text;

import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import com.dishanhai.sh_better_ui.client.gui.text.VanillaTextEffects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** All native GUI sequence paths, including buttons and tooltips, share the inline renderer. */
@Mixin(GuiGraphics.class)
abstract class GuiInlineItemMixin {
    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void shbui$inlineItems(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow,
                                   CallbackInfoReturnable<Integer> result) {
        var prepared = UiRichText.prepare(text);
        var graphics = (GuiGraphics) (Object) this;
        if (prepared != text) {
            result.setReturnValue(graphics.drawString(font, prepared, x, y, color, shadow));
        }
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void shbui$inlineString(Font font, String text, float x, float y, int color, boolean shadow,
                                    CallbackInfoReturnable<Integer> result) {
        var prepared = VanillaTextEffects.parse(text);
        if (prepared != null) result.setReturnValue(((GuiGraphics) (Object) this)
                .drawString(font, prepared.getVisualOrderText(), x, y, color, shadow));
    }
}
