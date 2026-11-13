// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.GpuFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

public final class NativePort {
    static final ModuleDescriptor.Platform PLATFORM = ModuleDescriptor.Platform.NEOFORGE_26_2;

    private NativePort() {}

    public static Screen screen(Minecraft client) { return client.gui.screen(); }
    public static void show(Minecraft client, Screen screen) { client.gui.setScreen(screen); }

    static GpuTexture colorTarget(Minecraft client) {
        return client.gameRenderer.mainRenderTarget().getColorTexture();
    }

    static boolean isRgba(GpuTexture texture) {
        return texture.getFormat() == GpuFormat.RGBA8_UNORM;
    }

    static void readPixels(CommandEncoder encoder, GpuBuffer buffer, Consumer<ByteBuffer> read) {
        try (var mapped = buffer.map(true, false)) {
            read.accept(mapped.data());
        }
    }
}
