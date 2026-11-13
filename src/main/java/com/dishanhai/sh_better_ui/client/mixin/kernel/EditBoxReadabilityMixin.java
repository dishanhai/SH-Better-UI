// Author: dishanhai
package com.dishanhai.sh_better_ui.client.mixin.kernel;

import com.dishanhai.sh_better_ui.kernel.internal.NativeTextReadability.EditBoxBinding;
import com.dishanhai.sh_better_ui.kernel.internal.NativeTextReadability.Label;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Submits only the native text runs; the editor continues to own caret, selection, and horizontal scrolling. */
@Mixin(EditBox.class)
abstract class EditBoxReadabilityMixin implements EditBoxBinding {
    private static final String RENDER = "renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V";
    private static final String SRG_RENDER = "m_87963_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V";
    private static final String GRAPHICS = "Lnet/minecraft/client/gui/GuiGraphics;";
    private static final String FONT = "(Lnet/minecraft/client/gui/Font;";
    private static final String SEQUENCE = "Lnet/minecraft/util/FormattedCharSequence;";
    private static final String COMPONENT = "Lnet/minecraft/network/chat/Component;";
    @Unique private Label shbui$label;
    @Unique private int shbui$editRun;

    @Inject(method = {RENDER, SRG_RENDER}, at = @At("HEAD"), remap = false)
    private void shbui$beginEditText(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback) {
        shbui$editRun = 0;
    }

    @Override
    public void shbui$bindReadability(Label label) {
        shbui$label = label;
    }

    @Group(name = "shbui$editText", min = 2, max = 2)
    @Redirect(method = RENDER, at = @At(value = "INVOKE",
            target = GRAPHICS + "drawString" + FONT + SEQUENCE + "III)I", remap = false), require = 0, remap = false)
    private int shbui$editTextForge(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        return shbui$label == null ? graphics.drawString(font, text, x, y, color)
                : shbui$drawText(graphics, font, text, x, y, color, true, shbui$editRun++);
    }

    @Group(name = "shbui$editText", min = 2, max = 2)
    @Redirect(method = SRG_RENDER, at = @At(value = "INVOKE",
            target = GRAPHICS + "m_280648_" + FONT + SEQUENCE + "III)I", remap = false), require = 0, remap = false)
    private int shbui$editTextSrg(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        return shbui$label == null ? graphics.drawString(font, text, x, y, color)
                : shbui$drawText(graphics, font, text, x, y, color, true, shbui$editRun++);
    }

    @Group(name = "shbui$editText", min = 2, max = 2)
    @Redirect(method = RENDER, at = @At(value = "INVOKE",
            target = GRAPHICS + "drawString" + FONT + SEQUENCE + "IIIZ)I", remap = false), require = 0, remap = false)
    private int shbui$editTextNeo(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y,
                                 int color, boolean shadow) {
        return shbui$drawText(graphics, font, text, x, y, color, shadow, shbui$editRun++);
    }

    @Group(name = "shbui$editHint", min = 1, max = 1)
    @Redirect(method = RENDER, at = @At(value = "INVOKE",
            target = GRAPHICS + "drawString" + FONT + COMPONENT + "III)I", remap = false), require = 0, remap = false)
    private int shbui$editHintForge(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        return shbui$label == null ? graphics.drawString(font, text, x, y, color)
                : shbui$drawText(graphics, font, text.getVisualOrderText(), x, y, color, true, 2);
    }

    @Group(name = "shbui$editHint", min = 1, max = 1)
    @Redirect(method = SRG_RENDER, at = @At(value = "INVOKE",
            target = GRAPHICS + "m_280430_" + FONT + COMPONENT + "III)I", remap = false), require = 0, remap = false)
    private int shbui$editHintSrg(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        return shbui$label == null ? graphics.drawString(font, text, x, y, color)
                : shbui$drawText(graphics, font, text.getVisualOrderText(), x, y, color, true, 2);
    }

    @Group(name = "shbui$editHint", min = 1, max = 1)
    @Redirect(method = RENDER, at = @At(value = "INVOKE",
            target = GRAPHICS + "drawString" + FONT + COMPONENT + "IIIZ)I", remap = false), require = 0, remap = false)
    private int shbui$editHintNeo(GuiGraphics graphics, Font font, Component text, int x, int y, int color, boolean shadow) {
        return shbui$label == null ? graphics.drawString(font, text, x, y, color, shadow)
                : shbui$drawText(graphics, font, text.getVisualOrderText(), x, y, color, shadow, 2);
    }

    @Unique
    private int shbui$drawText(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color,
                              boolean shadow, int line) {
        var editor = (EditBox) (Object) this;
        int opaqueColor = (color & 0xfc000000) == 0 ? color | 0xff000000 : color;
        if (shbui$label != null && shbui$label.draw(graphics, font, text, x, y, opaqueColor,
                shadow, line, editor.isHovered(), editor.isFocused())) {
            return (int) (x + font.getSplitter().stringWidth(text)) + (shadow ? 1 : 0);
        }
        return graphics.drawString(font, text, x, y, color, shadow);
    }
}
