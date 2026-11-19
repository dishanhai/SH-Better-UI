// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

/** Private color attachment for GUI passes. Draw bindings are scoped by the caller. */
public final class GuiColorTarget implements AutoCloseable {
    private final int width;
    private final int height;
    private int framebuffer;
    private int texture;

    public GuiColorTarget(int width, int height, int format, int filter) {
        this.width = width;
        this.height = height;
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousUnpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            framebuffer = GlStateManager.glGenFramebuffers();
            texture = GlStateManager._genTexture();
            if (framebuffer == 0 || texture == 0) throw new IllegalStateException("Could not allocate GUI color target");

            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            RenderSystem.bindTexture(texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);

            int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            try {
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
                if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("Incomplete GUI color target");
                }
            } finally {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previous);
            }
        } catch (RuntimeException failure) {
            close();
            throw failure;
        } finally {
            RenderSystem.bindTexture(previousTexture);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, previousUnpack);
        }
    }

    public int width() { return width; }
    public int height() { return height; }
    public int texture() { return texture; }

    public void bindDraw(int width, int height) {
        if (framebuffer == 0) throw new IllegalStateException("GUI color target is closed");
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        RenderSystem.viewport(0, 0, width, height);
    }

    @Override
    public void close() {
        // Deleting our objects leaves another current framebuffer and its attachments intact.
        if (framebuffer != 0) GlStateManager._glDeleteFramebuffers(framebuffer);
        if (texture != 0) GlStateManager._deleteTexture(texture);
        framebuffer = texture = 0;
    }
}
