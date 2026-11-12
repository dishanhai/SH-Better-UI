// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/** Batches translucent gallery marks into one native position/color GUI element. */
final class GalleryFieldMesh {
    private float[] positions = new float[1024];
    private int[] colors = new int[512];
    private int size;

    void rectangle(float x, float y, float halfX, float halfY, int color) {
        quad(x - halfX, y - halfY, x - halfX, y + halfY,
                x + halfX, y + halfY, x + halfX, y - halfY, color, color, color, color);
    }

    void line(float x0, float y0, float x1, float y1, float width, int start, int end) {
        float dx = x1 - x0, dy = y1 - y0;
        float length = (float) Math.hypot(dx, dy);
        if (length < 0.001f) return;
        float sx = -dy * width * .5f / length;
        float sy = dx * width * .5f / length;
        quad(x0 - sx, y0 - sy, x0 + sx, y0 + sy, x1 + sx, y1 + sy, x1 - sx, y1 - sy,
                start, start, end, end);
    }

    void triangle(float x, float y, float ax, float ay, float bx, float by,
                  int center, int edgeA, int edgeB) {
        quad(x, y, ax, ay, bx, by, bx, by, center, edgeA, edgeB, edgeB);
    }

    void disc(float x, float y, float radius, int center, int edge) {
        final float diagonal = .70710677f * radius;
        triangle(x, y, x + radius, y, x + diagonal, y - diagonal, center, edge, edge);
        triangle(x, y, x + diagonal, y - diagonal, x, y - radius, center, edge, edge);
        triangle(x, y, x, y - radius, x - diagonal, y - diagonal, center, edge, edge);
        triangle(x, y, x - diagonal, y - diagonal, x - radius, y, center, edge, edge);
        triangle(x, y, x - radius, y, x - diagonal, y + diagonal, center, edge, edge);
        triangle(x, y, x - diagonal, y + diagonal, x, y + radius, center, edge, edge);
        triangle(x, y, x, y + radius, x + diagonal, y + diagonal, center, edge, edge);
        triangle(x, y, x + diagonal, y + diagonal, x + radius, y, center, edge, edge);
    }

    // GUI front faces follow the same order as top-left, bottom-left, bottom-right, top-right.
    void quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy,
              int a, int b, int c, int d) {
        if (((a | b | c | d) >>> 24) == 0) return;
        if (size == colors.length) {
            colors = Arrays.copyOf(colors, size * 2);
            positions = Arrays.copyOf(positions, size * 4);
        }
        vertex(ax, ay, a);
        vertex(bx, by, b);
        vertex(cx, cy, c);
        vertex(dx, dy, d);
    }

    private void vertex(float x, float y, int color) {
        positions[size * 2] = x;
        positions[size * 2 + 1] = y;
        colors[size++] = color;
    }

    void submit(GuiGraphicsExtractor graphics, Bounds area) {
        if (size == 0) return;
        var pose = new Matrix3x2f(graphics.pose()).translate(area.x(), area.y());
        var bounds = new ScreenRectangle(area.x(), area.y(), area.width(), area.height());
        var clip = graphics.peekScissorStack();
        graphics.submitGuiElementRenderState(new State(positions, colors, size, pose, clip, bounds));
    }

    static int alpha(int color, double opacity) {
        return (int) Math.round((color >>> 24) * Math.clamp(opacity, 0, 1)) << 24 | color & 0xFFFFFF;
    }

    private record State(float[] positions, int[] colors, int size, Matrix3x2f pose,
                         @Nullable ScreenRectangle scissorArea, ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
        @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
        @Override public void buildVertices(VertexConsumer out) {
            for (int i = 0; i < size; i++) {
                out.addVertexWith2DPose(pose, positions[i * 2], positions[i * 2 + 1]).setColor(colors[i]);
            }
        }
    }
}
