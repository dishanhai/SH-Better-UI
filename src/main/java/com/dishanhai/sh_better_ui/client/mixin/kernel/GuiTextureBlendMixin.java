// Author: dishanhai
package com.dishanhai.sh_better_ui.client.mixin.kernel;

import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserves the multiplicative blend of the vanilla vignette across ShaderInstance.apply(). */
@Mixin(ShaderInstance.class)
abstract class GuiTextureBlendMixin {
    @Group(name = "shbui$vignetteBlend", min = 1, max = 1)
    @Redirect(method = "apply", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/shaders/BlendMode;apply()V"), remap = false, require = 0)
    private void shbui$applyGuiBlend(BlendMode blend) {
        shbui$preserveVignetteBlend(blend);
    }

    @Group(name = "shbui$vignetteBlend")
    @Redirect(method = "m_173363_", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/shaders/BlendMode;m_85526_()V"), remap = false, require = 0)
    private void shbui$applyGuiBlendSrg(BlendMode blend) {
        shbui$preserveVignetteBlend(blend);
    }

    @Unique
    private void shbui$preserveVignetteBlend(BlendMode blend) {
        boolean vignette = (Object) this == GameRenderer.getPositionTexShader()
                && GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB) == GL11.GL_ZERO
                && GL11.glGetInteger(GL14.GL_BLEND_DST_RGB) == GL11.GL_ONE_MINUS_SRC_COLOR;
        blend.apply();
        if (vignette) {
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.blendFuncSeparate(GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }
}
