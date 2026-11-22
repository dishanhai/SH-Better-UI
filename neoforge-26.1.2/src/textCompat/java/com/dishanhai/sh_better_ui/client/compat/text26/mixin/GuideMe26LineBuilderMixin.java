// Author: dishanhai
package com.dishanhai.sh_better_ui.client.compat.text26.mixin;

import com.dishanhai.sh_better_ui.client.compat.text26.GuideMe26Text;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import guideme.document.flow.LytFlowContent;
import guideme.layout.flow.LineTextRun;
import guideme.style.ResolvedTextStyle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Shared descriptors in GuideME 26.1.17-beta and 26.2.2-alpha. Required hits detect drift when installed. */
@Pseudo
@Mixin(targets = "guideme.layout.flow.LineBuilder", remap = false)
abstract class GuideMe26LineBuilderMixin {
    @Unique private GuideMe26Text.Layout shbui$layout;

    @WrapMethod(method = "appendText(Ljava/lang/String;Lguideme/document/flow/LytFlowContent;)V", require = 1)
    private void shbui$layout(String raw, LytFlowContent content, Operation<Void> original) {
        GuideMe26Text.Layout previous = shbui$layout;
        try {
            shbui$layout = GuideMe26Text.prepare(raw);
            original.call(shbui$layout == null ? raw : shbui$layout.text(), content);
        } finally {
            shbui$layout = previous;
        }
    }

    @WrapOperation(method = "lambda$appendText$0", at = @At(value = "NEW", target = "guideme/layout/flow/LineTextRun"), require = 1)
    private LineTextRun shbui$attach(String text, ResolvedTextStyle style, ResolvedTextStyle hover,
                                    Operation<LineTextRun> original) {
        LineTextRun run = original.call(text, style, hover);
        if (shbui$layout != null) ((GuideMe26Text.StyledRun) run).shbui$setStyled(shbui$layout.take(text), shbui$layout.literalFallback());
        return run;
    }
}
