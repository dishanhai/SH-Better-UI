// Author: dishanhai. Shared-loader vertex bridge; no framebuffer, texture reads or custom shader.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.ArtworkPalette;
import net.minecraft.client.gui.GuiGraphics;

/** Fixed-budget interpolated color mesh. Neighbor cells share vertices, so there are no card edges. */
public final class MusicArtworkStageRender {
    private MusicArtworkStageRender() {}
    public static void background(GuiGraphics graphics,Bounds area,ArtworkPalette palette,double phase,boolean lowPerformance) {
        if(area.width()<=0||area.height()<=0)return;
        var pose=graphics.pose().last().pose();
        if(!pose.isFinite()||Math.abs(pose.determinant())<1e-8)return;
        int columns=lowPerformance?1:8,rows=lowPerformance?1:6;
        int[][] colors=new int[rows+1][columns+1];
        for(int y=0;y<=rows;y++)for(int x=0;x<=columns;x++) {
            double u=x/(double)columns,v=y/(double)rows;
            double drift=lowPerformance?0:.025*Math.sin(phase+v*Math.PI)*Math.sin(u*Math.PI);
            colors[y][x]=palette.color(u+drift,v);
        }
        graphics.flush();
        var sink=CapsuleRenderer.colorVertices(graphics,area.x(),area.y());
        for(int y=0;y<rows;y++)for(int x=0;x<columns;x++) {
            float left=area.width()*x/(float)columns,right=area.width()*(x+1)/(float)columns;
            float top=area.height()*y/(float)rows,bottom=area.height()*(y+1)/(float)rows;
            sink.vertex(left,top,colors[y][x]);sink.vertex(left,bottom,colors[y+1][x]);
            sink.vertex(right,bottom,colors[y+1][x+1]);sink.vertex(right,top,colors[y][x+1]);
        }
        graphics.flush();
    }
}
