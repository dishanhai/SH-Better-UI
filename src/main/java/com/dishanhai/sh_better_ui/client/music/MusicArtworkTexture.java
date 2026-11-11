// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.music;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/** Decodes one bounded cover off-thread, then uploads it on demand on the render thread. */
public final class MusicArtworkTexture {
    public record Texture(ResourceLocation id, int width, int height) {}
    private static final ResourceLocation ID = ResourceLocation.tryParse("sh_better_ui:music/current_cover");
    private final ScheduledThreadPoolExecutor decoder = new ScheduledThreadPoolExecutor(1, task -> {
        var thread = new Thread(task, "shb-music-artwork");
        thread.setDaemon(true);
        return thread;
    });
    private String requested;
    private Future<?> task;
    private NativeImage pending;
    private Texture current;
    private com.dishanhai.sh_better_ui.kernel.api.OwnershipLease textureLease;

    private void ownTexture() {
        if (textureLease != null || current == null) return;
        textureLease = com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.ownNativeGpu(
                com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceKind.TEXTURE_RESOURCE,
                com.dishanhai.sh_better_ui.kernel.api.UiId.parse("sh_better_ui:native/music/current_cover"),
                this::releaseTexture);
    }

    private synchronized void releaseTexture() {
        textureLease = null;
        var texture = current;
        current = null;
        requested = null;
        if (texture != null) Minecraft.getInstance().getTextureManager().release(texture.id());
    }
    private long generation;
    private boolean closed;

    public MusicArtworkTexture() { decoder.setRemoveOnCancelPolicy(true); }

    public synchronized Optional<Texture> texture(MusicSnapshot.Artwork artwork) {
        RenderSystem.assertOnRenderThread();
        if (closed) return Optional.empty();
        String key = artwork == null ? null : artwork.id();
        if (!java.util.Objects.equals(requested, key)) {
            invalidate();
            requested = key;
            if (artwork != null) {
                long ticket = generation;
                task = decoder.submit(() -> decode(ticket, artwork));
            }
        }
        if (pending != null) {
            NativeImage image = pending;
            pending = null;
            try {
                int width = image.getWidth(), height = image.getHeight();
                Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(image));
                current = new Texture(ID, width, height);
            } catch (RuntimeException e) {
                image.close();
            }
        }
        ownTexture();
        return Optional.ofNullable(current);
    }

    private void decode(long ticket, MusicSnapshot.Artwork artwork) {
        try {
            var image = readArtwork(artwork);
            if (image == null) return;
            synchronized (this) {
                if (closed || generation != ticket) image.close();
                else pending = image;
            }
        } catch (java.io.IOException | RuntimeException ignored) {
            // Keep the host's missing-cover placeholder.
        }
    }

    /** Decodes only the bounded PNG cover. The caller owns and closes the returned CPU image. */
    public static NativeImage readArtwork(MusicSnapshot.Artwork artwork) throws java.io.IOException {
        byte[] png = artwork.png();
        if (png.length < 24) return null;
        var header = ByteBuffer.wrap(png);
        if (header.getLong() != 0x89504e470d0a1a0aL || header.getInt() != 13 || header.getInt() != 0x49484452) return null;
        int width = header.getInt(), height = header.getInt();
        if (width <= 0 || height <= 0 || width > 240 || height > 240) return null;
        return NativeImage.read(new java.io.ByteArrayInputStream(png));
    }

    /** Resource reload runs on the render thread; the next draw uploads the current cover again. */
    public synchronized void invalidate() {
        requested = null;
        cancelDecode();
        if (textureLease != null) textureLease.close();
        else releaseTexture();
    }

    /** At JVM shutdown the game owns GL cleanup; this only disposes the CPU side. */
    public synchronized void closeDecoder() {
        closed = true;
        cancelDecode();
        decoder.shutdownNow();
    }

    private void cancelDecode() {
        generation++;
        if (task != null) task.cancel(true);
        task = null;
        if (pending != null) pending.close();
        pending = null;
    }
}
