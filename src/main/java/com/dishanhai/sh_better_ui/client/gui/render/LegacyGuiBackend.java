// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.render;

import com.dishanhai.sh_better_ui.client.lyrics.render.LyricGlyphPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Legacy ports may install a Vulkan-backed, GLFW_NO_API game window. */
public final class LegacyGuiBackend {
    private static long windowChecked;
    private static boolean nonOpenGlWindow;

    private LegacyGuiBackend() {}

    /** Query the game's window, not installed mods or a compatibility-layer GL context. */
    public static boolean isNonOpenGlWindow() {
        var client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) return false;
        long window = client.getWindow().getWindow();
        if (window == 0L) return false;
        if (window != windowChecked) {
            nonOpenGlWindow = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_CLIENT_API) == GLFW.GLFW_NO_API;
            windowChecked = window;
        }
        return nonOpenGlWindow;
    }

    /**
     * A tinted, full-bright vanilla textured quad. The host renderer owns sampling,
     * blend/depth state and the GUI pass; no LWJGL OpenGL query or custom shader is required.
     */
    public static void texturedQuad(GuiGraphics graphics, ResourceLocation texture, int x, int y, int w, int h,
                                    float u0, float v0, float u1, float v1, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        var pose = graphics.pose().last().pose();
        var buffer = graphics.bufferSource().getBuffer(RenderType.textSeeThrough(texture));
        int light = LightTexture.FULL_BRIGHT;
        LyricGlyphPlatform.vertex(buffer, pose, x, y, u0, v0, argb, light);
        LyricGlyphPlatform.vertex(buffer, pose, x, y + h, u0, v1, argb, light);
        LyricGlyphPlatform.vertex(buffer, pose, x + w, y + h, u1, v1, argb, light);
        LyricGlyphPlatform.vertex(buffer, pose, x + w, y, u1, v0, argb, light);
        graphics.flush();
    }
}
