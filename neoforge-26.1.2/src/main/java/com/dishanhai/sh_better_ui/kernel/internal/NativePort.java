// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

public final class NativePort {
    static final ModuleDescriptor.Platform PLATFORM = ModuleDescriptor.Platform.NEOFORGE_26_1_2;

    private NativePort() {}

    public static Screen screen(Minecraft client) { return client.screen; }
    public static void show(Minecraft client, Screen screen) { client.setScreen(screen); }

    static GpuTexture colorTarget(Minecraft client) {
        return client.getMainRenderTarget().getColorTexture();
    }

    static boolean isRgba(GpuTexture texture) {
        return texture.getFormat() == TextureFormat.RGBA8;
    }

    static void readPixels(CommandEncoder encoder, GpuBuffer buffer, Consumer<ByteBuffer> read) {
        try (var mapped = encoder.mapBuffer(buffer, true, false)) {
            read.accept(mapped.data());
        }
    }
}
