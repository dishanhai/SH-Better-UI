// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.effect.Dither;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

/** Screen-owned dither image. Close it when the screen or background is removed. */
public final class DitherBackground implements AutoCloseable {
    public static final ResourceLocation DEMO = ResourceLocation.fromNamespaceAndPath("sh_better_ui", "textures/background/dither_demo.jpg");
    private final ResourceLocation source;
    private BufferedImage image;
    private boolean attempted;
    private DynamicTexture texture;
    private ResourceLocation textureId;
    private int width, height;
    private Dither.Options lastOptions;
    private long lastFrame;
    private double time;

    public DitherBackground(ResourceLocation source) { this.source = Objects.requireNonNull(source); }

    public void draw(GuiGraphics graphics, Bounds bounds, Dither.Options options, boolean reducedMotion, long now) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        if (!attempted) {
            attempted = true;
            try (var stream = Minecraft.getInstance().getResourceManager().open(source)) {
                image = ImageIO.read(stream);
            } catch (IOException e) {
                com.dishanhai.sh_better_ui.ShBetterUi.LOGGER.warn("Could not load dither image {}", source, e);
            }
        }
        if (image == null) return;
        int w = Math.min(bounds.width(), 2048), h = Math.min(bounds.height(), 2048);
        boolean animate = options.animated() && !reducedMotion && options.mode() == Dither.Mode.NOISE;
        boolean changed = w != width || h != height || !options.equals(lastOptions);
        if (animate && now - lastFrame >= 50) {
            time += options.animationSpeed();
            changed = true;
        }
        if (changed || texture == null) update(w, h, options, now);
        if (textureId != null) graphics.blit(textureId, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                0f, 0f, w, h, w, h);
    }

    private void update(int w, int h, Dither.Options options, long now) {
        var fitted = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g = fitted.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale = switch (options.fit()) {
                case COVER -> Math.max(w / (double) image.getWidth(), h / (double) image.getHeight());
                case CONTAIN -> Math.min(w / (double) image.getWidth(), h / (double) image.getHeight());
                default -> 1;
            };
            int dw = options.fit() == Dither.Fit.FILL ? w : (int)Math.ceil(image.getWidth() * scale);
            int dh = options.fit() == Dither.Fit.FILL ? h : (int)Math.ceil(image.getHeight() * scale);
            int dx = options.fit() == Dither.Fit.FILL ? 0 : (int)Math.floor((w - dw) / 2.0);
            int dy = options.fit() == Dither.Fit.FILL ? 0 : (int)Math.floor((h - dh) / 2.0);
            g.drawImage(image, dx, dy, dw, dh, null);
        } finally { g.dispose(); }
        int[] pixels = Dither.apply(fitted.getRGB(0, 0, w, h, null, 0, w), w, h, options, time);
        if (texture == null || w != width || h != height) {
            releaseTexture();
            texture = new DynamicTexture(w, h, true);
            textureId = Minecraft.getInstance().getTextureManager().register("sh_better_ui_dither", texture);
            texture.setFilter(false, false);
        }
        var nativeImage = texture.getPixels();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int argb = pixels[y * w + x];
            nativeImage.setPixelRGBA(x, y, argb & 0xff00ff00 | (argb >> 16 & 255) | (argb & 255) << 16);
        }
        texture.upload();
        width = w; height = h; lastOptions = options; lastFrame = now;
    }

    private void releaseTexture() {
        if (textureId != null) Minecraft.getInstance().getTextureManager().release(textureId);
        texture = null; textureId = null;
    }

    @Override public void close() { releaseTexture(); image = null; attempted = false; }
}
