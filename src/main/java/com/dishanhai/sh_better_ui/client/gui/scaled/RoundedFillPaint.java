// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.kernel.api.geometry.FilledGeometry;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import com.dishanhai.sh_better_ui.kernel.api.geometry.RoundedFill;
import com.dishanhai.sh_better_ui.kernel.api.geometry.RoundedFill.Direction;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.DoubleUnaryOperator;

/** Paints both interior colors against one silhouette, over the native outline. */
final class RoundedFillPaint {
    private RoundedFill shape;
    private int width;
    private int height;
    private float radius;

    void draw(GuiGraphics graphics, int x, int y, int w, int h, float corner, int stroke,
              int border, int background, int fill, Direction direction, int step, DoubleUnaryOperator front) {
        int innerWidth = w - 2 * stroke, innerHeight = h - 2 * stroke;
        float innerRadius = RoundedCorners.insetRadius(w, h, corner, stroke);
        if (shape == null || width != innerWidth || height != innerHeight || radius != innerRadius) {
            shape = new RoundedFill(innerWidth, innerHeight, innerRadius);
            width = innerWidth;
            height = innerHeight;
            radius = innerRadius;
        }
        var wet = shape.filled(direction, step, front);
        RoundedCorners.draw(graphics, x, y, w, h, corner, 0, border, border);
        var vertices = CapsuleRenderer.colorVertices(graphics, x + stroke, y + stroke);
        if (wet != shape.interior()) draw(shape.interior(), background, vertices);
        draw(wet, fill, vertices);
    }

    private static void draw(FilledGeometry<Local> mesh, int color, CapsuleMesh.VertexSink vertices) {
        for (var triangle : mesh.triangles()) {
            vertices.vertex((float) triangle.a().x(), (float) triangle.a().y(), color);
            vertices.vertex((float) triangle.c().x(), (float) triangle.c().y(), color);
            vertices.vertex((float) triangle.b().x(), (float) triangle.b().y(), color);
            vertices.vertex((float) triangle.b().x(), (float) triangle.b().y(), color);
        }
    }
}
