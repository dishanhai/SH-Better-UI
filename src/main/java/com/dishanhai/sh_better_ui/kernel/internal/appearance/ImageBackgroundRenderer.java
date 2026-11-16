// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.appearance;

import com.dishanhai.sh_better_ui.client.api.BackgroundSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.kernel.api.appearance.BackgroundImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Per-view asynchronous image loading and render-thread texture ownership. */
public final class ImageBackgroundRenderer implements BackgroundSpec.Renderer {
    private static final Map<ImageBackgroundRenderer, Boolean> LIVE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final java.util.concurrent.atomic.AtomicLong IDS = new java.util.concurrent.atomic.AtomicLong();
    private final long instanceId = IDS.incrementAndGet();
    private com.dishanhai.sh_better_ui.kernel.api.OwnershipLease lease;
    private String source;
    private CompletableFuture<BackgroundImageLoader.Pixels> pending;
    private ResourceLocation texture;
    private int imageWidth, imageHeight;
    private boolean failed;

    @Override
    public void draw(GuiGraphics g, Font font, Bounds area, UiMouseState mouse, double x, double y,
                     long now, UiPreferences preferences) {
        var options = ThemeEffects.profile(BackgroundImage.ID, BackgroundImage.DEFAULT);
        if (!options.source().equals(source)) {
            close();
            source = options.source();
            if (!source.isEmpty()) {
                try { ownResources(); load(source); }
                catch (RuntimeException invalidPath) { failed = true; }
            }
        }
        LIVE.put(this, Boolean.TRUE);
        if (pending != null && pending.isDone()) {
            try {
                var pixels = pending.join();
                upload(pixels);
            } catch (RuntimeException failure) {
                failed = true;
            } finally {
                pending = null;
            }
        }
        if (texture == null) {
            var message = Component.translatable("sh_better_ui.custom_theme.image." +
                    (source.isEmpty() ? "empty" : failed ? "failed" : "loading"));
            g.drawCenteredString(font, message, area.x() + area.width() / 2, area.y() + area.height() / 2,
                    UiTheme.current().text());
            return;
        }
        double scale = options.fit() == BackgroundImage.Fit.COVER
                ? Math.max((double) area.width() / imageWidth, (double) area.height() / imageHeight)
                : Math.min((double) area.width() / imageWidth, (double) area.height() / imageHeight);
        int w = options.fit() == BackgroundImage.Fit.STRETCH ? area.width() : Math.max(1, (int) Math.ceil(imageWidth * scale));
        int h = options.fit() == BackgroundImage.Fit.STRETCH ? area.height() : Math.max(1, (int) Math.ceil(imageHeight * scale));
        g.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        try {
            g.blit(texture, area.x() + (area.width() - w) / 2, area.y() + (area.height() - h) / 2,
                    w, h, 0f, 0f, imageWidth, imageHeight, imageWidth, imageHeight);
        } finally {
            g.disableScissor();
        }
    }

    private void load(String value) {
        var client = Minecraft.getInstance();
        var resource = BackgroundImage.resource(value)
                ? client.getResourceManager().getResource(ResourceLocation.tryParse(value)).orElse(null) : null;
        Path path = BackgroundImage.resource(value) ? null : Path.of(value);
        if (path != null && !path.isAbsolute()) path = client.gameDirectory.toPath().resolve(path);
        Path local = path;
        pending = CompletableFuture.supplyAsync(() -> {
            try {
                if (local == null && resource == null) throw new java.io.IOException("Missing background image");
                try (var stream = local != null ? Files.newInputStream(local) : resource.open()) {
                    return BackgroundImageLoader.read(stream);
                }
            } catch (java.io.IOException failure) {
                throw new CompletionException(failure);
            }
        }, Util.backgroundExecutor());
    }

    private void upload(BackgroundImageLoader.Pixels pixels) {
        var image = new com.mojang.blaze3d.platform.NativeImage(pixels.width(), pixels.height(), false);
        DynamicTexture dynamic = null;
        try {
            for (int y = 0; y < pixels.height(); y++) for (int x = 0; x < pixels.width(); x++) {
                int argb = pixels.argb()[y * pixels.width() + x];
                image.setPixelRGBA(x, y, argb & 0xFF00FF00 | (argb >> 16 & 255) | (argb & 255) << 16);
            }
            dynamic = new DynamicTexture(image);
            dynamic.setFilter(true, false);
            texture = Minecraft.getInstance().getTextureManager().register("shb_background_image", dynamic);
            imageWidth = pixels.width();
            imageHeight = pixels.height();
        } catch (RuntimeException failure) {
            if (dynamic != null) dynamic.close();
            else image.close();
            throw failure;
        }
    }

    public static void invalidateAll() {
        ImageBackgroundRenderer[] renderers;
        synchronized (LIVE) { renderers = LIVE.keySet().toArray(ImageBackgroundRenderer[]::new); }
        for (var renderer : renderers) renderer.close();
    }

    private void ownResources() {
        if (lease == null) lease = com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.ownNativeGpu(
                com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceKind.TEXTURE_RESOURCE,
                com.dishanhai.sh_better_ui.kernel.api.UiId.parse("sh_better_ui:native/background_image/" + instanceId),
                this::releaseResources);
    }

    private void releaseResources() {
        lease = null;
        if (pending != null) pending.cancel(true);
        pending = null;
        if (texture != null) Minecraft.getInstance().getTextureManager().release(texture);
        texture = null;
        source = null;
        failed = false;
    }

    @Override public void close() {
        if (lease != null) lease.close();
        else releaseResources();
        LIVE.remove(this);
    }

}
