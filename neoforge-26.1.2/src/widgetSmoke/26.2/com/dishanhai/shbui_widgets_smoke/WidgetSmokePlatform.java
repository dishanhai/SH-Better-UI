// Author: dishanhai
package com.dishanhai.shbui_widgets_smoke;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class WidgetSmokePlatform {
    private WidgetSmokePlatform() {}

    static boolean hasOverlay(Minecraft minecraft) {
        return minecraft.gui.overlay() != null;
    }

    static void open(Minecraft minecraft, Screen screen) {
        minecraft.gui.setScreen(screen);
    }

    static Screen screen(Minecraft minecraft) {
        return minecraft.gui.screen();
    }

    static String backend() {
        return RenderSystem.getDevice().getDeviceInfo().backendName();
    }

    static String backendPreference(Minecraft minecraft) {
        return minecraft.options.preferredGraphicsBackend().get().getSerializedName();
    }

    static RenderTarget target(Minecraft minecraft) {
        return minecraft.gameRenderer.mainRenderTarget();
    }
}
