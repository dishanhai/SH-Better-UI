// Author: dishanhai
package com.dishanhai.sh_better_ui.client.blur;

import com.dishanhai.sh_better_ui.client.blur.mixin.GuiGraphicsBlurAccessor;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/** Internal per-frame state attached to Minecraft's GUI render state. */
public interface BlurFrame {
    static BlurFrame from(GuiGraphicsExtractor graphics) {
        return (BlurFrame) ((GuiGraphicsBlurAccessor) graphics).shbui$blurState();
    }

    boolean shbui$hasBoundary();
    boolean shbui$captureOnly();
    @Nullable GpuTextureView shbui$capture();
    void shbui$capture(GpuTextureView view, boolean captureOnly);
}
