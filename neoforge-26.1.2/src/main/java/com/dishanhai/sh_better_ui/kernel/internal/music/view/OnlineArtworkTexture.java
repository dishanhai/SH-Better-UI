// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.ArtworkPalette;
import com.dishanhai.sh_better_ui.kernel.internal.music.OnlineArtwork;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** The 26.x view owns and releases its native TextureManager registration.
 * One view owns one cover. Downloads/decoding never block rendering; no persistent disk cache. */
public final class OnlineArtworkTexture implements AutoCloseable {
    public record Texture(Identifier id, int width, int height, ArtworkPalette palette) {
        public Texture(Identifier id, int width, int height) { this(id, width, height, null); }
    }
    private static final AtomicLong IDS = new AtomicLong();
    private final long instance = IDS.incrementAndGet();
    private final Identifier id = Identifier.tryParse("sh_better_ui:music/online_cover_" + instance);
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
    public OnlineArtworkTexture() { worker.setRemoveOnCancelPolicy(true); }

    public synchronized Optional<Texture> texture(OnlineTrack track) {
        RenderSystem.assertOnRenderThread();
        if (closed) return Optional.empty();
        String key = track == null ? "" : track.artworkUrl();
        if (!java.util.Objects.equals(key, requested)) {
            invalidate(); requested = key;
            if (!key.isBlank()) {
                long ticket = generation;
                task = worker.submit(() -> load(ticket, key));
            }
        }
        if (pending != null) {
            var image = pending; var colors = pendingPalette; pending = null; pendingPalette = null;
            DynamicTexture texture = null;
            try {
                int width = image.getWidth(), height = image.getHeight();
                texture = new DynamicTexture(() -> "SHBUI online cover " + instance, image);
                Minecraft.getInstance().getTextureManager().register(id, texture);
                current = new Texture(id, width, height, colors);
            } catch (RuntimeException error) {
                if (texture != null) texture.close(); else image.close();
                release();
            }
        }
        return Optional.ofNullable(current);
    }
    synchronized long revision() { return generation; }
    private void load(long ticket, String url) {
        NativeImage image = null;
        try {
            image = NativeImage.read(new ByteArrayInputStream(OnlineArtwork.download(url)));
            // Sample the image already decoded for this texture, once, off the render thread.
            // No second cover request, no unrelated system-media artwork, no GPU readback.
            var colors = samplePalette(image.getWidth(), image.getHeight(), image::getPixel);
            synchronized (this) {
                if (!closed && ticket == generation && !Thread.currentThread().isInterrupted()) {
                    pending = image; pendingPalette = colors; image = null;
                }
            }
        } catch (java.io.IOException | RuntimeException ignored) { /* The view retains its explicit missing-cover fallback. */ }
        finally { if (image != null) image.close(); }
    }
    static ArtworkPalette samplePalette(int width, int height, java.util.function.IntBinaryOperator argb) {
        int columns = Math.min(64, width), rows = Math.min(64, height);
        int[] pixels = new int[columns * rows];
        for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++) {
            pixels[y * columns + x] = argb.applyAsInt(x * width / columns, y * height / rows);
        }
        return ArtworkPalette.fromArgb(columns, rows, pixels);
    }

    private synchronized void release() {
        requested = null;
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
        release();
    }
    @Override public synchronized void close() {
        invalidate(); closed = true; worker.shutdownNow();
    }
}
