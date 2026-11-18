// Author: dishanhai
package com.dishanhai.sh_better_ui.client.compat.text26.mixin;

import com.dishanhai.sh_better_ui.client.compat.text26.GuideMe26Text;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import guideme.render.RenderContext;
import guideme.style.ResolvedTextStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "guideme.layout.flow.LineTextRun", remap = false)
abstract class GuideMe26LineTextRunMixin implements GuideMe26Text.StyledRun {
    @Unique private Component shbui$styled;
    @Unique private boolean shbui$literalFallback;
    @Override public void shbui$setStyled(Component component, boolean literalFallback) {
        shbui$styled = component;
        shbui$literalFallback = literalFallback;
    }

    @WrapOperation(method = "render(Lguideme/render/RenderContext;)V", at = @At(value = "INVOKE",
            target = "Lguideme/render/RenderContext;renderText(Ljava/lang/String;Lguideme/style/ResolvedTextStyle;FF)V"), require = 1)
    private void shbui$render(RenderContext context, String raw, ResolvedTextStyle style, float x, float y,
                              Operation<Void> original) {
        if (shbui$styled == null && !shbui$literalFallback) { original.call(context, raw, style, x, y); return; }
        Style host = Style.EMPTY.withBold(style.bold()).withItalic(style.italic())
                .withUnderlined(style.underlined()).withStrikethrough(style.strikethrough()).withFont(style.font());
        var graphics = context.guiGraphics();
        var pose = graphics.pose();
        boolean scaled = style.fontScale() != 1;
        if (scaled) pose.pushMatrix();
        try {
            if (scaled) {
                pose.scale(style.fontScale(), style.fontScale());
                pose.translate(x / style.fontScale(), y / style.fontScale());
                x = 0; y = 0;
            }
            graphics.text(context.font(), GuideMe26Text.renderSequence(shbui$styled, raw, host, shbui$literalFallback), (int) x, (int) y, context.resolveColor(style.color()), style.dropShadow());
            GuideMe26Text.RENDERED.incrementAndGet();
        } finally {
            if (scaled) pose.popMatrix();
        }
    }
}
