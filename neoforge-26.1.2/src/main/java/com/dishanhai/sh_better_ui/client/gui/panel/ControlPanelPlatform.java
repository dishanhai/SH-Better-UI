// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

final class ControlPanelPlatform {
    private ControlPanelPlatform() {}

    static @Nullable Screen screen(Minecraft minecraft) { return minecraft.screen; }

    static boolean hasOverlay(Minecraft minecraft) { return minecraft.getOverlay() != null; }

    static void show(Minecraft minecraft, @Nullable Screen screen) { minecraft.setScreen(screen); }

    static void subtitles(Minecraft minecraft) { minecraft.gui.extractDeferredSubtitles(); }

    static ToastManager toasts(Minecraft minecraft) { return minecraft.getToastManager(); }

    static PanelOverview.Graphics graphics() {
        var device = RenderSystem.getDevice();
        return new PanelOverview.Graphics(device.getBackendName(), device.getRenderer(), device.getVendor(),
                device.getVersion());
    }
}
