// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.ArtworkPalette;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/** Fixed-budget vertex-color mesh, captured during GUI extraction rather than immediate rendering. */
public final class MusicArtworkStageRender {
    private MusicArtworkStageRender() {}
    public static void background(GuiGraphicsExtractor graphics, Bounds area, ArtworkPalette palette,
                                  double phase, boolean lowPerformance) {
        if (area.width() <= 0 || area.height() <= 0) return;
        var pose = new Matrix3x2f(graphics.pose());
        if (!pose.isFinite() || Math.abs(pose.determinant()) < 1e-8) return;
        var scissor = graphics.peekScissorStack();
        var bounds = new ScreenRectangle(area.x(), area.y(), area.width(), area.height()).transformMaxBounds(pose);
        var viewport = new ScreenRectangle(0, 0, graphics.guiWidth(), graphics.guiHeight());
        bounds = bounds.intersection(viewport);
        if (bounds != null && scissor != null) bounds = bounds.intersection(scissor);
        if (bounds == null) return;
        int columns = lowPerformance ? 1 : 8, rows = lowPerformance ? 1 : 6;
        int[][] colors = new int[rows + 1][columns + 1];
        for (int y=0; y<=rows; y++) for (int x=0; x<=columns; x++) {
            double u=x/(double)columns,v=y/(double)rows;
            double drift=lowPerformance ? 0 : .025*Math.sin(phase+v*Math.PI)*Math.sin(u*Math.PI);
            colors[y][x]=palette.color(u+drift,v);
        }
        graphics.submitGuiElementRenderState(new Mesh(pose, area, colors, columns, rows, scissor, bounds));
    }
    private record Mesh(Matrix3x2fc pose, Bounds area, int[][] colors, int columns, int rows,
                        @Nullable ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
        @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
        @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
        @Override public void buildVertices(VertexConsumer out) {
            for (int y=0;y<rows;y++) for (int x=0;x<columns;x++) {
                float left=area.x()+area.width()*x/(float)columns, right=area.x()+area.width()*(x+1)/(float)columns;
                float top=area.y()+area.height()*y/(float)rows, bottom=area.y()+area.height()*(y+1)/(float)rows;
                out.addVertexWith2DPose(pose,left,top).setColor(colors[y][x]);
                out.addVertexWith2DPose(pose,left,bottom).setColor(colors[y+1][x]);
                out.addVertexWith2DPose(pose,right,bottom).setColor(colors[y+1][x+1]);
                out.addVertexWith2DPose(pose,right,top).setColor(colors[y][x+1]);
            }
        }
    }
}
