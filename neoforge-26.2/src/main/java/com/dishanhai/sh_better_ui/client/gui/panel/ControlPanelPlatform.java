// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

final class ControlPanelPlatform {
    private ControlPanelPlatform() {}

    static @Nullable Screen screen(Minecraft minecraft) { return minecraft.gui.screen(); }

    static boolean hasOverlay(Minecraft minecraft) { return minecraft.gui.overlay() != null; }

    static void show(Minecraft minecraft, @Nullable Screen screen) { minecraft.gui.setScreen(screen); }

    static void subtitles(Minecraft minecraft) { minecraft.gui.hud.extractDeferredSubtitles(); }

    static ToastManager toasts(Minecraft minecraft) { return minecraft.gui.toastManager(); }

    static PanelOverview.Graphics graphics() {
        var device = RenderSystem.getDevice().getDeviceInfo();
        return new PanelOverview.Graphics(device.backendName(), device.name(), device.vendorName(), device.driverInfo());
    }
}
