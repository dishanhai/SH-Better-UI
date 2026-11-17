// Author: dishanhai. Public UUK drawing primitive, not a Minecraft widget or screen wrapper.
package com.dishanhai.sh_better_ui.kernel.api.music.presentation;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicArtworkStageRender;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import net.minecraft.resources.Identifier;

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
    /** Sharp, aspect-preserving artwork. The host supplies the already-loaded resource and actual dimensions. */
    public void renderCover(UiFrame frame,Bounds area,Identifier texture,int imageWidth,int imageHeight) {
        if(texture==null||imageWidth<=0||imageHeight<=0||area.width()<=0||area.height()<=0)return;
        float fit=Math.min(area.width()/(float)imageWidth,area.height()/(float)imageHeight);
        int width=Math.min(area.width(),Math.max(1,Math.round(imageWidth*fit)));
        int height=Math.min(area.height(),Math.max(1,Math.round(imageHeight*fit)));
        frame.graphics().blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,texture,area.x()+(area.width()-width)/2,area.y()+(area.height()-height)/2,
                0f,0f,width,height,imageWidth,imageHeight,imageWidth,imageHeight);
    }
    /** Clears presentation memory only; image ownership remains with its loader. Reusable after close. */
    @Override public void close() { motion.reset(); }
}
