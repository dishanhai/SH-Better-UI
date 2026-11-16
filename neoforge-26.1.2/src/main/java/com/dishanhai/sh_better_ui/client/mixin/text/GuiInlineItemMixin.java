// Author: dishanhai
package com.dishanhai.sh_better_ui.client.mixin.text;

import com.dishanhai.sh_better_ui.client.gui.text.InlineItemPlatform;
import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
abstract class GuiInlineItemMixin {
    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"), cancellable = true)
    private void shbui$inlineItems(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow,
                                   CallbackInfo result) {
        var prepared = UiRichText.prepare(text);
        var graphics = (GuiGraphicsExtractor) (Object) this;
        if (prepared != text) {
            graphics.text(font, prepared, x, y, color, shadow);
            result.cancel();
        } else {
            InlineItemPlatform.draw(graphics, font, prepared, x, y);
        }
    }

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
            at = @At("HEAD"), cancellable = true)
    private void shbui$inlineString(Font font, String text, int x, int y, int color, boolean shadow,
                                    CallbackInfo result) {
        if (text == null || !text.contains("<rich:")) return;
        var prepared = UiRichText.parse(text);
        ((GuiGraphicsExtractor) (Object) this).text(font, prepared.getVisualOrderText(), x, y, color, shadow);
        result.cancel();
    }
}
