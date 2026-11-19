// Author: dishanhai. Public UUK drawing primitive, not a Minecraft widget or screen wrapper.
package com.dishanhai.sh_better_ui.kernel.api.music.presentation;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicArtworkStageRender;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

/** Host-owned presentation state; all supplied textures are borrowed and never fetched or released. */
public final class MusicArtworkStage implements AutoCloseable {
    public static final int FOREGROUND=0xfff8f8fa,SECONDARY=0xffeae8eb;
    private final ArtworkStageMotion motion=new ArtworkStageMotion();
    public void renderBackground(UiFrame frame,Bounds bounds,ArtworkPalette palette,long nowMs,boolean lowPerformance) {
        if(bounds.width()<=0||bounds.height()<=0)return;
        if(frame.preferences().highContrast()) {
            motion.reset();frame.graphics().fill(bounds.x(),bounds.y(),bounds.right(),bounds.bottom(),UiTheme.current().background()|0xff000000);return;
        }
        var shown=motion.sample(palette==null?ArtworkPalette.fallback(UiTheme.current().background()):palette,
                nowMs,frame.preferences().reducedMotion(),lowPerformance);
        MusicArtworkStageRender.background(frame.graphics(),bounds,shown.palette(),shown.phase(),lowPerformance);
    }
    /** Playback-scoped PCM response. No PCM means a still palette, including protected providers. */
    public void renderAudioBackground(UiFrame frame,Bounds bounds,ArtworkPalette palette,long nowMs,boolean lowPerformance,float activity) {
        if(bounds.width()<=0||bounds.height()<=0)return;
        if(frame.preferences().highContrast()) {
            motion.reset();frame.graphics().fill(bounds.x(),bounds.y(),bounds.right(),bounds.bottom(),UiTheme.current().background()|0xff000000);return;
        }
        var shown=motion.sample(palette==null?ArtworkPalette.fallback(UiTheme.current().background()):palette,
                nowMs,frame.preferences().reducedMotion(),lowPerformance,activity);
        MusicArtworkStageRender.background(frame.graphics(),bounds,shown.palette(),shown.phase(),lowPerformance);
    }
    /** Sharp, aspect-preserving artwork. The host supplies the already-loaded resource and actual dimensions. */
    public void renderCover(UiFrame frame,Bounds area,ResourceLocation texture,int imageWidth,int imageHeight) {
        renderCover(frame,area,texture,imageWidth,imageHeight,1f);
    }
    /** Draw the borrowed image with opacity; the original overload stays fully opaque. */
    public void renderCover(UiFrame frame,Bounds area,ResourceLocation texture,int imageWidth,int imageHeight,float opacity) {
        if(!(opacity>0f))return;
        if(texture==null||imageWidth<=0||imageHeight<=0||area.width()<=0||area.height()<=0)return;
        float fit=Math.min(area.width()/(float)imageWidth,area.height()/(float)imageHeight);
        int width=Math.min(area.width(),Math.max(1,Math.round(imageWidth*fit)));
        int height=Math.min(area.height(),Math.max(1,Math.round(imageHeight*fit)));
        var graphics=frame.graphics();
        int x=area.x()+(area.width()-width)/2,y=area.y()+(area.height()-height)/2;
        if(opacity>=1f) {
            graphics.blit(texture,x,y,width,height,0f,0f,imageWidth,imageHeight,imageWidth,imageHeight);
            return;
        }
        // The legacy region blit does not enable blending on its own.
        graphics.flush();
        var color=RenderSystem.getShaderColor();
        float r=color[0],g=color[1],b=color[2],a=color[3];
        boolean blending=GL11.glIsEnabled(GL11.GL_BLEND);
        int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int eqRgb=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), eqAlpha=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        try {
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.defaultBlendFunc();
            graphics.setColor(r,g,b,a*opacity);
            graphics.blit(texture,x,y,width,height,0f,0f,imageWidth,imageHeight,imageWidth,imageHeight);
        } finally {
            graphics.setColor(r,g,b,a);
            RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            GL20.glBlendEquationSeparate(eqRgb,eqAlpha);
            if(!blending)RenderSystem.disableBlend();
        }
    }
    /** Clears presentation memory only; image ownership remains with its loader. Reusable after close. */
    @Override public void close() { motion.reset(); }
}
