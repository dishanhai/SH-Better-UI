// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Restores the GUI pipeline and native framebuffer/scissor after scoped rendering. */
public final class GuiStateGuard {
    private GuiStateGuard() {}

    public static void draw(Runnable draw) {
        var pipeline = WaveRenderState.capture();
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int write = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        int[] scissor = new int[4];
        boolean clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        if (clipped) GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        float[] color = RenderSystem.getShaderColor().clone();
        try {
            draw.run();
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, write);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (clipped) RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            else RenderSystem.disableScissor();
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            pipeline.restore();
        }
    }
}
