// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;

/** UUK binding scope for native GUI effects, including target allocation and failure cleanup. */
public final class GuiRenderBindings implements AutoCloseable {
    private final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int write = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int active = GlStateManager._getActiveTexture();
    private final int activeBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    private final int unpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
    private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int[] viewport = new int[4];
    private final int[] scissor = new int[4];
    private final boolean clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    private final float[] clear = new float[4];
    private final float[] color = RenderSystem.getShaderColor().clone();
    // Native GUI textures, overlay and lightmap. Also preserve a caller using a higher unit.
    private final int[] textures = new int[3];
    private final int[] shaderTextures = new int[3];
    private final int[] samplers = GL.getCapabilities().glBindSampler == 0 ? null : new int[3];

    public GuiRenderBindings() {
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clear);
        try {
            for (int unit = 0; unit < textures.length; unit++) {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
                textures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                shaderTextures[unit] = RenderSystem.getShaderTexture(unit);
                if (samplers != null) samplers[unit] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            }
        } finally {
            RenderSystem.activeTexture(active);
        }
    }

    @Override public void close() {
        var client = Minecraft.getInstance();
        var main = client == null ? null : client.getMainRenderTarget();
        // TextureTarget creation calls bindWrite and can notify Oculus/Iris that main is unbound.
        if (main != null && main.frameBufferId == write) main.bindWrite(false);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, write);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        RenderSystem.clearColor(clear[0], clear[1], clear[2], clear[3]);
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
        if (!clipped) RenderSystem.disableScissor();
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpack);
        for (int unit = 0; unit < textures.length; unit++) {
            RenderSystem.setShaderTexture(unit, shaderTextures[unit]);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
            RenderSystem.bindTexture(textures[unit]);
            if (samplers != null) GL33.glBindSampler(unit, samplers[unit]);
        }
        RenderSystem.activeTexture(active);
        RenderSystem.bindTexture(activeBinding);
        // Raw text/material draws may change the VAO behind BufferUploader's cached binding.
        if (GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) != vao) {
            BufferUploader.invalidate();
            GlStateManager._glBindVertexArray(vao);
        }
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
    }
}
