// Author: dishanhai
package com.dishanhai.sh_better_ui.client.blur;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

final class BlurRenderTarget {
    private BlurRenderTarget() {}

    static @Nullable GpuTexture colorTexture(Minecraft minecraft) {
        return minecraft.getMainRenderTarget().getColorTexture();
    }

    static void clear(CommandEncoder encoder, GpuTexture texture) {
        encoder.clearColorTexture(texture, 0);
    }
}
