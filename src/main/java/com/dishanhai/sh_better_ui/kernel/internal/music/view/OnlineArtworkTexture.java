// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.ArtworkPalette;
import com.dishanhai.sh_better_ui.kernel.internal.music.OnlineArtwork;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** One view owns one cover. Downloads/decoding never block rendering; no persistent disk cache. */
public final class OnlineArtworkTexture implements AutoCloseable, MusicCoverHandoff.Slot<OnlineArtworkTexture.Texture> {
    public record Texture(ResourceLocation id, int width, int height, ArtworkPalette palette) {
        public Texture(ResourceLocation id, int width, int height) { this(id, width, height, null); }
    }
    private static final AtomicLong IDS = new AtomicLong();
    private final long instance = IDS.incrementAndGet();
    private final float cornerFraction;
    private final ResourceLocation id = ResourceLocation.tryParse("sh_better_ui:music/online_cover_" + instance);
    private final ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1, job -> {
        var thread = new Thread(job, "shb-online-cover"); thread.setDaemon(true); return thread;
    });
    private String requested;
    private Future<?> task;
    private NativeImage pending;
    private ArtworkPalette pendingPalette;
    private Texture current;
    private long generation;
    private boolean closed;
    private com.dishanhai.sh_better_ui.kernel.api.OwnershipLease lease;
    public OnlineArtworkTexture() { this(0); }
    OnlineArtworkTexture(float cornerFraction) {
        this.cornerFraction = cornerFraction;
        worker.setRemoveOnCancelPolicy(true);
        worker.setKeepAliveTime(2, TimeUnit.SECONDS);
        worker.allowCoreThreadTimeOut(true);
    }

    public synchronized Optional<Texture> texture(OnlineTrack track) {
        return Optional.ofNullable(poll(track == null ? "" : track.artworkUrl()).image());
    }

    @Override public synchronized MusicCoverHandoff.Result<Texture> poll(String key) {
        RenderSystem.assertOnRenderThread();
        if (closed) return new MusicCoverHandoff.Result<>(null, false);
        if (!java.util.Objects.equals(key, requested)) {
            invalidate(); requested = key;
            if (!key.isBlank()) {
                long ticket = generation;
                task = worker.submit(() -> load(ticket, key));
            }
        }
        if (pending != null) {
            var image = pending; var colors = pendingPalette; pending = null; pendingPalette = null;
            try {
                int width = image.getWidth(), height = image.getHeight();
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
                current = new Texture(id, width, height, colors);
                lease = com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.ownNativeGpu(
                    com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceKind.TEXTURE_RESOURCE,
                    com.dishanhai.sh_better_ui.kernel.api.UiId.parse("sh_better_ui:native/music/online_cover_" + instance), this::release);
            } catch (RuntimeException error) { image.close(); release(); requested = key; }
        }
        return new MusicCoverHandoff.Result<>(current, current == null && task != null
                && !task.isDone() && java.util.Objects.equals(key, requested));
    }

    @Override public synchronized boolean owns(Texture image) { return current == image; }
    @Override public void clear() { invalidate(); }
    private void load(long ticket, String url) {
        NativeImage image = null;
        try {
            image = NativeImage.read(new ByteArrayInputStream(OnlineArtwork.download(url)));
            // Sample the image already decoded for this texture, once, off the render thread.
            // No second cover request, no unrelated system-media artwork, no GPU readback.
            int columns = Math.min(64, image.getWidth()), rows = Math.min(64, image.getHeight());
            int[] pixels = new int[columns * rows];
            for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++) {
                int abgr = image.getPixelRGBA(x * image.getWidth() / columns, y * image.getHeight() / rows);
                pixels[y * columns + x] = (abgr & 0xff00ff00) | ((abgr >>> 16) & 255) | ((abgr & 255) << 16);
            }
            var colors = ArtworkPalette.fromArgb(columns, rows, pixels);
            if (cornerFraction > 0) {
                int size = MusicArtworkThumbnail.size(image.getWidth(), image.getHeight());
                int[] thumbnailPixels = MusicArtworkThumbnail.pixels(image.getWidth(), image.getHeight(),
                        cornerFraction, image::getPixelRGBA);
                var thumbnail = new NativeImage(size, size, false);
                image.close();
                image = thumbnail;
                for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                    image.setPixelRGBA(x, y, thumbnailPixels[y * size + x]);
                }
            }
            synchronized (this) {
                if (!closed && ticket == generation && !Thread.currentThread().isInterrupted()) {
                    pending = image; pendingPalette = colors; image = null;
                }
            }
        } catch (java.io.IOException | RuntimeException ignored) { /* The view retains its explicit missing-cover fallback. */ }
        finally { if (image != null) image.close(); }
    }
    private synchronized void release() {
        lease = null; requested = null;
        if (current != null) Minecraft.getInstance().getTextureManager().release(current.id());
        current = null;
    }
    public synchronized void invalidate() {
        RenderSystem.assertOnRenderThread();
        generation++; requested = null;
        if (task != null) task.cancel(true);
        task = null;
        if (pending != null) pending.close();
        pending = null; pendingPalette = null;
        if (lease != null) lease.close(); else release();
    }
    @Override public synchronized void close() {
        invalidate(); closed = true; worker.shutdownNow();
    }
}
