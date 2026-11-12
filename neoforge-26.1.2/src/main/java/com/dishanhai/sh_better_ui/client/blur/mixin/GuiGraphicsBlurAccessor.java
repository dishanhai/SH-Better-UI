// Author: dishanhai
package com.dishanhai.sh_better_ui.client.blur.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsBlurAccessor {
    @Accessor("guiRenderState")
    GuiRenderState shbui$blurState();
}
