// Author: dishanhai
package com.dishanhai.sh_better_ui.client.blur;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import org.jspecify.annotations.Nullable;

/** Shares one same-frame GPU backdrop among GUI elements submitted after the blur boundary. */
@EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT)
public final class BackdropCapture {
    private static final System.Logger LOGGER = System.getLogger("sh_better_ui.backdrop");
    private static final int USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING
            | GpuTexture.USAGE_RENDER_ATTACHMENT;
    private static @Nullable Snapshot cached;
    private static boolean allocationWarning;
    private static boolean copyWarning;

    private BackdropCapture() {}

    /** Returns a backdrop the host requested for this frame, without creating a new boundary. */
    public static @Nullable GpuTextureView current(GuiGraphicsExtractor graphics) {
        return BlurFrame.from(graphics).shbui$capture();
    }

    /**
     * Requests the backdrop for this extraction. Submit a GUI element consuming the view after
     * this call, and keep the view within this frame. Source coordinates use bottom-left UVs.
     * A pre-existing native menu blur runs before capture.
     */
    public static @Nullable GpuTextureView request(GuiGraphicsExtractor graphics) {
        if (!RenderSystem.isOnRenderThread()) return null;
        var frame = BlurFrame.from(graphics);
        if (frame.shbui$capture() != null) return frame.shbui$capture();
        var minecraft = Minecraft.getInstance();
        var device = RenderSystem.tryGetDevice();
        if (minecraft == null || device == null) return null;
        var source = BlurRenderTarget.colorTexture(minecraft);
        if (source == null || source.isClosed() || (source.usage() & GpuTexture.USAGE_COPY_SRC) == 0) return null;
        var view = snapshot(device, source);
        if (view == null) return null;

        boolean captureOnly = !frame.shbui$hasBoundary();
        if (captureOnly) {
            graphics.nextStratum();
            graphics.blurBeforeThisStratum();
        }
        frame.shbui$capture(view, captureOnly);
        return view;
    }

    private static @Nullable GpuTextureView snapshot(GpuDevice device, GpuTexture source) {
        if (cached != null && cached.device() == device && !cached.view().isClosed()
                && matches(source, cached.view().texture())) return cached.view();
        closeCached();
        try {
            var texture = device.createTexture("SH-Better-UI GUI backdrop", USAGE, source.getFormat(),
                    source.getWidth(0), source.getHeight(0), 1, 1);
            try {
                cached = new Snapshot(device, device.createTextureView(texture));
                allocationWarning = false;
                return cached.view();
            } catch (RuntimeException failure) {
                texture.close();
                throw failure;
            }
        } catch (RuntimeException failure) {
            if (!allocationWarning) LOGGER.log(System.Logger.Level.WARNING, "Unable to allocate GUI backdrop", failure);
            allocationWarning = true;
            return null;
        }
    }

    /** Called by the GUI renderer after its background pass and before its foreground pass. */
    public static void copy(GpuTextureView view) {
        if (view.isClosed()) return;
        var destination = view.texture();
        var source = BlurRenderTarget.colorTexture(Minecraft.getInstance());
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try {
            if (source != null && !source.isClosed() && matches(source, destination)) {
                encoder.copyTextureToTexture(source, destination, 0, 0, 0, 0, 0,
                        destination.getWidth(0), destination.getHeight(0));
                copyWarning = false;
                return;
            }
        } catch (RuntimeException failure) {
            if (!copyWarning) LOGGER.log(System.Logger.Level.WARNING, "Unable to copy GUI backdrop", failure);
            copyWarning = true;
        }
        // Alpha zero lets the consuming shader use its tint without sampling stale pixels.
        BlurRenderTarget.clear(encoder, destination);
    }

    private static boolean matches(GpuTexture source, GpuTexture destination) {
        return source.getFormat() == destination.getFormat()
                && source.getWidth(0) == destination.getWidth(0) && source.getHeight(0) == destination.getHeight(0);
    }

    @SubscribeEvent
    public static void stop(ClientStoppingEvent event) {
        closeCached();
    }

    private static void closeCached() {
        var snapshot = cached;
        cached = null;
        if (snapshot == null) return;
        var texture = snapshot.view().texture();
        snapshot.view().close();
        texture.close();
    }

    private record Snapshot(GpuDevice device, GpuTextureView view) {}
}
