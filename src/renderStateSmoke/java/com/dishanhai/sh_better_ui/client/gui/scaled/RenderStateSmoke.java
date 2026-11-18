// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

import java.util.Arrays;

/** Opt-in native regression; creates a hidden GL window and always releases its resources. */
public final class RenderStateSmoke {
    public static void main(String[] args) {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(64, 64, "SHBUI render state regression", 0, 0);
        if (window == 0) throw new AssertionError("GL context creation failed");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            RenderSystem.initRenderThread();
            var target = new TextureTarget(64, 64, true, false);
            var textures = new int[]{GL11.glGenTextures(), GL11.glGenTextures(), GL11.glGenTextures()};
            try {
                target.bindWrite(true);
                for (int unit = 0; unit < textures.length; unit++) {
                    RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
                    RenderSystem.bindTexture(textures[unit]);
                    RenderSystem.setShaderTexture(unit, textures[unit]);
                }
                RenderSystem.clearColor(.2f, .3f, .4f, .5f);
                RenderSystem.enableScissor(3, 4, 23, 25);
                RenderSystem.depthMask(false);
                int[] before = snapshot();
                var failure = new IllegalStateException("injected effect failure");
                try {
                    GuiStateGuard.draw(() -> {
                        var scratch = new TextureTarget(16, 16, false, false);
                        scratch.destroyBuffers();
                        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                        RenderSystem.bindTexture(0);
                        RenderSystem.setShaderTexture(0, 0);
                        RenderSystem.depthMask(true);
                        RenderSystem.disableScissor();
                        throw failure;
                    });
                    throw new AssertionError("Effect failure was swallowed");
                } catch (IllegalStateException expected) {
                    require(expected == failure, "Effect failure changed");
                }
                require(Arrays.equals(before, snapshot()), "Effect cleanup leaked state: before="
                        + Arrays.toString(before) + ", after=" + Arrays.toString(snapshot()));
                for (int size : new int[]{8, 32, 16}) {
                    int answer = GuiStateGuard.call(() -> {
                        var scratch = new TextureTarget(size, size, false, false);
                        try {
                            GuiStateGuard.draw(() -> {
                                scratch.resize(size * 2, size, false);
                                scratch.bindWrite(true);
                            });
                            return 42; // A successful early return must also restore the host.
                        } finally {
                            scratch.destroyBuffers();
                        }
                    });
                    require(answer == 42 && Arrays.equals(before, snapshot()), "Resize/nested/return leaked state");
                }
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error after cleanup");
                System.out.println("PASS texture allocation/failure/cleanup isolation on " + GL11.glGetString(GL11.GL_RENDERER));
            } finally {
                RenderSystem.depthMask(true);
                RenderSystem.disableScissor();
                target.destroyBuffers();
                for (int texture : textures) GL11.glDeleteTextures(texture);
            }
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    static int[] snapshot() {
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] state = new int[23];
        state[0] = active;
        state[1] = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        state[2] = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        state[3] = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) ? 1 : 0;
        state[4] = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) ? 1 : 0;
        int[] box = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, box);
        System.arraycopy(box, 0, state, 5, 4);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
        System.arraycopy(box, 0, state, 9, 4);
        float[] clear = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clear);
        for (int i = 0; i < 4; i++) state[13 + i] = Float.floatToIntBits(clear[i]);
        for (int i = 0; i < 3; i++) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
            state[17 + i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            state[20 + i] = RenderSystem.getShaderTexture(i);
        }
        RenderSystem.activeTexture(active);
        return state;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
