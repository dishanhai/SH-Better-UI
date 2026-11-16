package com.dishanhai.sh_better_ui.client.mixin.text;

import com.dishanhai.sh_better_ui.client.gui.text.UiFormattedText;
import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import com.dishanhai.sh_better_ui.client.gui.text.InlineItemPlatform;
import com.dishanhai.sh_better_ui.client.gui.text.VanillaTextEffects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 山海：在公开批次入口处理，兼容替换私有字体渲染路径的字体引擎。Forge 生产同时匹配 SRG 名称。 */
@Mixin(Font.class)
abstract class FontTextEffectsMixin {
    private static final String BATCH = "FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "Lnet/minecraft/client/gui/Font$DisplayMode;II";
    private static final String STRING_BATCH = "(Ljava/lang/String;" + BATCH + "Z)I";
    private static final String SEQUENCE_BATCH = "(Lnet/minecraft/util/FormattedCharSequence;" + BATCH + ")I";
    private static final String COMPONENT_BATCH = "(Lnet/minecraft/network/chat/Component;" + BATCH + ")I";
    private static final String WIDTH = "(Lnet/minecraft/network/chat/FormattedText;)I";
    private static final String SPLIT = "(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;";

    @ModifyVariable(method = {"width(Lnet/minecraft/util/FormattedCharSequence;)I",
            "m_92724_(Lnet/minecraft/util/FormattedCharSequence;)I"},
            at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private FormattedCharSequence shbui$inlineWidth(FormattedCharSequence text) {
        return UiRichText.prepare(text);
    }

    @Inject(method = {"drawInBatch" + SEQUENCE_BATCH, "m_272191_" + SEQUENCE_BATCH},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void shbui$drawEffects(FormattedCharSequence text, float x, float y, int color, boolean shadow,
                                   Matrix4f pose, MultiBufferSource buffers, Font.DisplayMode mode,
                                   int background, int light, CallbackInfoReturnable<Integer> result) {
        var prepared = UiRichText.prepare(text);
        if (prepared != text) {
            result.setReturnValue(((Font) (Object) this).drawInBatch(prepared, x, y, color, shadow,
                    pose, buffers, mode, background, light));
            return;
        }
        Integer end = UiFormattedText.drawBatch((Font) (Object) this, text, x, y, color, shadow, pose, buffers,
                mode, background, light, VanillaTextEffects.handlesLegacy());
        if (end != null) result.setReturnValue(end);
        else InlineItemPlatform.draw((Font) (Object) this, text, x, y, pose, buffers);
    }

    @Inject(method = {"drawInBatch" + STRING_BATCH, "m_272078_" + STRING_BATCH},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void shbui$drawLegacy(String raw, float x, float y, int color, boolean shadow,
                                  Matrix4f pose, MultiBufferSource buffers, Font.DisplayMode mode,
                                  int background, int light, boolean bidirectional, CallbackInfoReturnable<Integer> result) {
        var text = VanillaTextEffects.parse(raw);
        if (text != null) {
            result.setReturnValue(((Font) (Object) this).drawInBatch(text.getVisualOrderText(), x, y, color,
                    shadow, pose, buffers, mode, background, light));
        }
    }

    @Inject(method = {"drawInBatch" + COMPONENT_BATCH, "m_272077_" + COMPONENT_BATCH},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void shbui$drawRichComponent(Component text, float x, float y, int color, boolean shadow,
                                         Matrix4f pose, MultiBufferSource buffers, Font.DisplayMode mode,
                                         int background, int light, CallbackInfoReturnable<Integer> result) {
        var prepared = VanillaTextEffects.normalize(text);
        var sequence = prepared.getVisualOrderText();
        // Replacement engines may also replace this overload without entering the sequence overload.
        if (prepared != text || !sequence.accept((index, style, cp) -> !UiRichText.isObject(style, cp))) {
            result.setReturnValue(((Font) (Object) this).drawInBatch(sequence, x, y, color, shadow,
                    pose, buffers, mode, background, light));
        }
    }

    @Inject(method = {"width(Ljava/lang/String;)I", "m_92895_(Ljava/lang/String;)I"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void shbui$measureLegacy(String raw, CallbackInfoReturnable<Integer> result) {
        var text = VanillaTextEffects.parse(raw);
        if (text != null) result.setReturnValue(((Font) (Object) this).width(text));
    }

    @ModifyVariable(method = {"width" + WIDTH, "m_92852_" + WIDTH, "split" + SPLIT, "m_92923_" + SPLIT,
            "substrByWidth(Lnet/minecraft/network/chat/FormattedText;I)Lnet/minecraft/network/chat/FormattedText;",
            "m_92854_(Lnet/minecraft/network/chat/FormattedText;I)Lnet/minecraft/network/chat/FormattedText;"},
            at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false, require = 3)
    private FormattedText shbui$normalizeLegacy(FormattedText text) {
        return VanillaTextEffects.normalize(text);
    }
}
