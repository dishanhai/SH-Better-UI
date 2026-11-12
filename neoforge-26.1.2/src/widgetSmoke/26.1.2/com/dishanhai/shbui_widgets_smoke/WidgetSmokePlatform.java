// Author: dishanhai
package com.dishanhai.shbui_widgets_smoke;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class WidgetSmokePlatform {
    private WidgetSmokePlatform() {}

    static boolean hasOverlay(Minecraft minecraft) {
        return minecraft.getOverlay() != null;
    }

    static void open(Minecraft minecraft, Screen screen) {
        minecraft.setScreen(screen);
    }

    static Screen screen(Minecraft minecraft) {
        return minecraft.screen;
    }

    static String backend() {
        return RenderSystem.getDevice().getBackendName();
    }

    static String backendPreference(Minecraft minecraft) {
        return "opengl";
    }

    static RenderTarget target(Minecraft minecraft) {
        return minecraft.getMainRenderTarget();
    }
}
