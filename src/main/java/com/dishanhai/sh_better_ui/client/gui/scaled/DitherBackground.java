// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.effect.Dither;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Screen-owned image: CPU preparation off-thread, bounded stripe uploads on the render thread. */
public final class DitherBackground implements AutoCloseable {
    public static final ResourceLocation DEMO = ResourceLocation.fromNamespaceAndPath("sh_better_ui", "textures/background/dither_demo.jpg");
    private static final Map<DitherBackground, Boolean> LIVE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicLong IDS = new AtomicLong();
    private final long instanceId = IDS.incrementAndGet();
    private final ResourceLocation source;
    private DitherImagePipeline pipeline;
    private DitherImagePipeline.Request requested, displayed;
    private DitherImagePipeline.Result staging;
    private Slot current, spare;
    private int nextRow;
    private long lastAnimation;
    private double time;
    private boolean failureLogged;
    private com.dishanhai.sh_better_ui.kernel.api.OwnershipLease lease;

    public DitherBackground(ResourceLocation source) { this.source = Objects.requireNonNull(source); }

    public void draw(GuiGraphics graphics, Bounds bounds, Dither.Options options, boolean reducedMotion, long now) {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(options);
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        LIVE.put(this, Boolean.TRUE);
        if (pipeline == null) {
            var resource = Minecraft.getInstance().getResourceManager().getResource(source);
            pipeline = new DitherImagePipeline(() -> {
                if (resource.isEmpty()) throw new IOException("Missing dither image " + source);
                return resource.get().open(); // Immutable resource snapshot, not a worker-side Minecraft lookup.
            });
            lastAnimation = now;
        }
        ownResources();
        // Keep the established raster/grid/fit semantics; no performance-driven downsampling.
        int width = Math.min(bounds.width(), 2048), height = Math.min(bounds.height(), 2048);
        // Freeze an already displayed frame immediately, even if a newer animation frame is in flight.
        // Initial loading and explicit size/option edits must still be allowed to finish while reduced.
        if (reducedMotion && displayed != null && displayed.width() == width && displayed.height() == height
                && displayed.options().equals(options)) {
            lastAnimation = now;
            drawCurrent(graphics, bounds);
            return;
        }
        boolean animate = options.animated() && !reducedMotion && options.mode() == Dither.Mode.NOISE && !failureLogged;
        boolean changed = requested == null || requested.width() != width || requested.height() != height
                || !requested.options().equals(options);
        if (!animate || now < lastAnimation) lastAnimation = now;
        // Finish and display a complete frame before scheduling another animation frame. Under load,
        // animation slows down instead of continuously invalidating work or queueing stale frames.
        if (!changed && animate && staging == null && !pipeline.pending() && now - lastAnimation >= 50) {
            time += options.animationSpeed();
            lastAnimation = now;
            changed = true;
        }
        if (changed) {
            requested = new DitherImagePipeline.Request(width, height, options, time);
            staging = null;
            nextRow = 0;
        }
        pipeline.request(requested);
        var ready = pipeline.poll();
        if (ready != null && ready.request().equals(requested)) {
            if (ready.failure() != null) {
                if (!failureLogged) {
                    com.dishanhai.sh_better_ui.ShBetterUi.LOGGER.warn("Could not prepare dither image {}", source, ready.failure());
                    failureLogged = true;
                }
            } else {
                failureLogged = false;
                staging = ready;
                nextRow = 0;
            }
        }
        if (staging != null) uploadStripe(graphics);
        if (current == null) {
            if ((options.backgroundColor() >>> 24) != 0)
                graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), options.backgroundColor());
        } else {
            drawCurrent(graphics, bounds);
        }
    }

    private void drawCurrent(GuiGraphics graphics, Bounds bounds) {
        graphics.blit(current.id, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                0f, 0f, current.width, current.height, current.width, current.height);
    }

    private void uploadStripe(GuiGraphics graphics) {
        var request = staging.request();
        int width = request.width(), height = request.height();
        // Flush existing host batches before touching a spare which may have been displayed previously.
        graphics.flush();
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int unpackBuffer = GL11.glGetInteger(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            if (spare == null || spare.width != width || spare.height != height) {
                release(spare);
                spare = null;
                var texture = new DynamicTexture(width, height, false);
                try {
                    var id = Minecraft.getInstance().getTextureManager().register("sh_better_ui_dither", texture);
                    spare = new Slot(texture, id, width, height);
                    texture.setFilter(false, false);
                } catch (RuntimeException error) { texture.close(); throw error; }
            }
            int rows = DitherUploadBudget.rows(width, height, nextRow);
            var pixels = spare.texture.getPixels();
            int end = nextRow + rows;
            for (int y = nextRow; y < end; y++) for (int x = 0; x < width; x++)
                pixels.setPixelRGBA(x, y, staging.pixels()[y * width + x]);
            spare.texture.bind();
            pixels.upload(0, 0, nextRow, 0, nextRow, width, rows, false, false, false, false);
            nextRow = end;
            if (nextRow == height) {
                Slot previous = current;
                current = spare;
                displayed = request;
                spare = previous; // Reuse the two buffers for animated frames of the same size.
                staging = null;
                nextRow = 0;
            }
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            RenderSystem.bindTexture(binding);
        }
    }

    private void ownResources() {
        if (lease != null) return;
        lease = com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.ownNativeGpu(
                com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceKind.TEXTURE_RESOURCE,
                com.dishanhai.sh_better_ui.kernel.api.UiId.parse("sh_better_ui:native/dither/" + instanceId),
                this::releaseResources);
    }
    private static void release(Slot slot) {
        if (slot != null) Minecraft.getInstance().getTextureManager().release(slot.id);
    }
    private void releaseResources() {
        lease = null;
        if (pipeline != null) pipeline.close();
        pipeline = null;
        requested = displayed = null;
        staging = null;
        nextRow = 0;
        release(current); release(spare);
        current = spare = null;
        time = 0;
        lastAnimation = 0;
        failureLogged = false;
    }

    /** Apply-stage resource reload hook, including instances whose CPU preparation is still pending. */
    public static void invalidateAll() {
        RenderSystem.assertOnRenderThread();
        DitherImagePipeline.warmUp();
        DitherBackground[] instances;
        synchronized (LIVE) { instances = LIVE.keySet().toArray(DitherBackground[]::new); }
        for (var background : instances) background.invalidate();
    }
    /** Custom hosts may call this on the render thread to retry or replace resource-pack content. */
    public void invalidate() {
        RenderSystem.assertOnRenderThread();
        if (lease != null) lease.close();
        else releaseResources();
    }
    @Override public void close() {
        invalidate();
        LIVE.remove(this);
    }
    private record Slot(DynamicTexture texture, ResourceLocation id, int width, int height) {}
}
