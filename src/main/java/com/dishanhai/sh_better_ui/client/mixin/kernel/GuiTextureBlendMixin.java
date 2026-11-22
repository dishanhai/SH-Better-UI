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

/** Preserves the vanilla vignette and crosshair blend modes across ShaderInstance.apply(). */
@Mixin(ShaderInstance.class)
abstract class GuiTextureBlendMixin {
    @Group(name = "shbui$guiTextureBlend", min = 1, max = 1)
    @Redirect(method = "apply", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/shaders/BlendMode;apply()V"), remap = false, require = 0)
    private void shbui$applyGuiBlend(BlendMode blend) {
        shbui$preserveOverlayBlend(blend);
    }

    @Group(name = "shbui$guiTextureBlend")
    @Redirect(method = "m_173363_", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/shaders/BlendMode;m_85526_()V"), remap = false, require = 0)
    private void shbui$applyGuiBlendSrg(BlendMode blend) {
        shbui$preserveOverlayBlend(blend);
    }

    @Unique
    private void shbui$preserveOverlayBlend(BlendMode blend) {
        if ((Object) this != GameRenderer.getPositionTexShader()
                || GL11.glGetInteger(GL14.GL_BLEND_DST_RGB) != GL11.GL_ONE_MINUS_SRC_COLOR) {
            blend.apply();
            return;
        }

        int source = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        blend.apply();
        // Vignette multiplies the scene, while the crosshair uses the texture to invert it.
        if (source == GL11.GL_ZERO || source == GL11.GL_ONE_MINUS_DST_COLOR) {
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.blendFuncSeparate(source, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }
}
