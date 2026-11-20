// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.OwnershipLease;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Renders only peripheral glyphs into one bounded, transparent native target. */
public final class LyricGlyphMask {
    private static TextureTarget target;
    private static OwnershipLease lease;
    private static final int MAX_SIZE = 2048;
    private LyricGlyphMask() {}

    public static boolean draw(GuiGraphics graphics, Bounds box, float anchor, float linePitch, Runnable text) {
        if (!VisualRevealRenderer.supportsGlyphMask()
                || (graphics.pose().last().pose().properties() & org.joml.Matrix4fc.PROPERTY_IDENTITY) == 0) return false;
        var window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        if (!(scale > 0)) return false;
        int left = (int)Math.floor(box.x()*scale), right = (int)Math.ceil(box.right()*scale);
        int bottom = window.getHeight()-(int)Math.ceil(box.bottom()*scale);
        int top = window.getHeight()-(int)Math.floor(box.y()*scale);
        int width = right-left, height = top-bottom;
        if (width <= 0 || height <= 0 || width > MAX_SIZE || height > MAX_SIZE) return false;
        var main = Minecraft.getInstance().getMainRenderTarget();
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId)return false;
        graphics.flush();
        GuiStateGuard.draw(() -> {
            RenderSystem.disableScissor();
            if (target == null) {
                target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
                target.setClearColor(0,0,0,0);
                lease = ClientUiKernel.ownNativeGpu(ModuleDescriptor.ResourceKind.RENDER_TARGET,
                        UiId.parse("sh_better_ui:native/music/lyric_glyph_mask"), LyricGlyphMask::release);
            } else if (target.width != width || target.height != height) {
                target.resize(width,height,Minecraft.ON_OSX);
            }
            RenderSystem.colorMask(true,true,true,true);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(false);
            target.bindRead();
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            RenderSystem.viewport(-left,-bottom,window.getWidth(),window.getHeight());
            text.run();
            graphics.flush();
        });
        GuiStateGuard.draw(() -> VisualRevealRenderer.drawGlyphMask(graphics,target,box,anchor,linePitch));
        return true;
    }

    private static void release() {
        var old=target;
        target=null;lease=null;
        if (old!=null) GuiStateGuard.draw(old::destroyBuffers);
    }
}
