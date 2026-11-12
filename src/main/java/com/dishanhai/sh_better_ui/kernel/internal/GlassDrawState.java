// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/** Raw GL draws restore their bindings without changing the game's cached RenderSystem state. */
final class GlassDrawState implements AutoCloseable {
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
    private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int rgbEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
    private final int alphaEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    private final int texture;
    private final int sampler;

    GlassDrawState() {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        sampler = GL.getCapabilities().glBindSampler == 0 ? -1 : GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
    }

    void bind(int shader, int vertices, int backdrop) {
        GL20.glUseProgram(shader);
        GL30.glBindVertexArray(vertices);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, backdrop);
        if (sampler >= 0) GL33.glBindSampler(0, 0);
        GL11.glEnable(GL11.GL_BLEND);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    @Override public void close() {
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vao);
        if (sampler >= 0) GL33.glBindSampler(0, sampler);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL13.glActiveTexture(activeTexture);
        GL20.glBlendEquationSeparate(rgbEquation, alphaEquation);
        GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        restore(GL11.GL_BLEND, blend);
        restore(GL11.GL_DEPTH_TEST, depth);
        GL11.glDepthFunc(depthFunction);
        GL11.glDepthMask(depthWrite);
        restore(GL11.GL_CULL_FACE, cull);
        restore(GL11.GL_STENCIL_TEST, stencil);
    }

    private static void restore(int state, boolean enabled) {
        if (enabled) GL11.glEnable(state);
        else GL11.glDisable(state);
    }
}
