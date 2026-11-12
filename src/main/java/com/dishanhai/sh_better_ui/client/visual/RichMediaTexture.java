// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** A nearest-filtered texture. Animated media shares an immutable atlas of source rectangles. */
final class RichMediaTexture extends AbstractTexture {
    private static final AtomicLong IDS = new AtomicLong();
    private static final int MAX_MOUNT_PIXELS = 4 * 1024 * 1024;
    private static final int MAX_CLIENT_PIXELS = 16 * 1024 * 1024;
    private static int clientPixels;

    record Pixels(NativeImage image, int width, int height, List<RichMediaDecoder.Frame> frames, long plays)
            implements AutoCloseable {
        @Override public void close() { image.close(); }
    }

    private final ResourceLocation location;
    private final int sheetWidth;
    private final int sheetHeight;
    private final List<RichMediaDecoder.Frame> frames;
    private final Map<RichMediaDecoder.Frame, RichTextImages.Image> regions = new HashMap<>();
    private final long plays;
    private final long duration;
    private final long startedAt;
    private boolean accounted;

    private RichMediaTexture(Pixels pixels, long startedAt) {
        location = ResourceLocation.tryParse("sh_better_ui:rich_media/atlas_" + IDS.incrementAndGet());
        sheetWidth = pixels.image().getWidth();
        sheetHeight = pixels.image().getHeight();
        frames = pixels.frames();
        for (var frame : frames) {
            regions.put(frame, new RichTextImages.Image(location, pixels.width(), pixels.height(),
                    frame.x(), frame.y(), sheetWidth, sheetHeight));
        }
        plays = pixels.plays();
        duration = frames.stream().mapToLong(RichMediaDecoder.Frame::durationMs).sum();
        this.startedAt = startedAt;
    }

    static Pixels convert(RichMediaDecoder.Sheet sheet) throws IOException {
        var pixels = sheet.pixels();
        int width = pixels.getWidth(), height = pixels.getHeight();
        var image = new NativeImage(width, height, false);
        boolean completed = false;
        try {
            var row = new int[width];
            for (int y = 0; y < height; y++) {
                RichMediaDecoder.checkInterrupted();
                pixels.getRGB(0, y, width, 1, row, 0, width);
                for (int x = 0; x < width; x++) {
                    int argb = row[x];
                    int abgr = argb & 0xff00ff00 | (argb & 0xff) << 16 | argb >>> 16 & 0xff;
                    image.setPixelRGBA(x, y, abgr);
                }
            }
            completed = true;
            return new Pixels(image, sheet.frameWidth(), sheet.frameHeight(), sheet.frames(), sheet.plays());
        } finally {
            if (!completed) image.close();
        }
    }

    static RichMediaTexture upload(Pixels pixels, int mountedPixels, long nowMs) {
        RenderSystem.assertOnRenderThread();
        var texture = new RichMediaTexture(pixels, nowMs);
        int max = RenderSystem.maxSupportedTextureSize();
        int area = texture.area();
        if (texture.sheetWidth > max || texture.sheetHeight > max
                || mountedPixels + area > MAX_MOUNT_PIXELS || clientPixels + area > MAX_CLIENT_PIXELS) return null;

        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        boolean registered = false;
        try {
            TextureUtil.prepareImage(texture.getId(), texture.sheetWidth, texture.sheetHeight);
            texture.bind();
            pixels.image().upload(0, 0, 0, false);
            texture.setFilter(false, false);
            Minecraft.getInstance().getTextureManager().register(texture.location, texture);
            registered = true;
            clientPixels += area;
            texture.accounted = true;
            return texture;
        } finally {
            if (!registered) texture.releaseId();
            GlStateManager._bindTexture(oldTexture);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, alignment);
        }
    }

    RichTextImages.Image image(long nowMs, boolean reducedMotion) {
        return imageAt(Math.max(0, nowMs - startedAt), reducedMotion);
    }

    RichTextImages.Image imageAt(long elapsedMs, boolean reducedMotion) {
        var frame = RichMediaDecoder.frameAt(frames, elapsedMs, duration, plays, reducedMotion);
        return regions.get(frame);
    }

    long cycleMs() { return frames.size() > 1 ? duration : 0; }

    int area() { return sheetWidth * sheetHeight; }

    @Override public void load(ResourceManager resources) {
        // The owning view releases this texture on resource generation change.
    }

    @Override public void close() {
        if (accounted) {
            accounted = false;
            clientPixels -= area();
        }
        super.close();
    }

    void release() { Minecraft.getInstance().getTextureManager().release(location); }
}
