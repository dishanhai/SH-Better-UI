// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.spatial.GuiGeometry;
import com.dishanhai.sh_better_ui.kernel.api.geometry.FilledGeometry;
import com.dishanhai.sh_better_ui.kernel.api.geometry.FilledGeometry.Triangle;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Point;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Extracts the fill and inset stroke as separate, non-overlapping meshes. */
public final class RoundedCorners {
    private static final int ARC_STEPS = 8;
    private static final int CACHE_SIZE = 256;
    private static final LinkedHashMap<Shape, Meshes> CACHE = new LinkedHashMap<>(32, 0.75f, true);

    private RoundedCorners() {}

    public static float fit(float width, float height, float radius) {
        return Math.min(Math.max(0f, radius), Math.max(0f, Math.min(width, height) * 0.5f));
    }

    public static float outsetRadius(float width, float height, float radius, float padding) {
        return fit(width, height, radius) + Math.max(0f, padding);
    }

    public static float insetRadius(float width, float height, float radius, float padding) {
        return Math.max(0f, fit(width, height, radius) - Math.max(0f, padding));
    }

    public static void draw(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                            float radius, float stroke, int edge, int fill) {
        if (!(width > 0 && height > 0) || !Float.isFinite(width) || !Float.isFinite(height)
                || !Float.isFinite(x) || !Float.isFinite(y) || Float.isNaN(radius) || Float.isNaN(stroke)
                || ((edge | fill) >>> 24) == 0) return;
        float padding = Math.clamp(stroke, 0f, Math.min(width, height) / 2);
        var meshes = meshes(new Shape(width, height, fit(width, height, radius), padding));
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            GuiGeometry.fill(graphics, meshes.fill(), fill);
            GuiGeometry.fill(graphics, meshes.stroke(), edge);
        } finally {
            pose.popMatrix();
        }
    }

    private static synchronized Meshes meshes(Shape shape) {
        var result = CACHE.get(shape);
        if (result != null) return result;
        result = tessellate(shape);
        CACHE.put(shape, result);
        if (CACHE.size() > CACHE_SIZE) CACHE.pollFirstEntry();
        return result;
    }

    private static Meshes tessellate(Shape shape) {
        double width = shape.width(), height = shape.height(), stroke = shape.stroke();
        var outer = contour(0, 0, width, height, shape.radius());
        var fill = new ArrayList<Triangle<Local>>();
        var edge = new ArrayList<Triangle<Local>>();
        var center = new Point<Local>(width / 2, height / 2);
        if (stroke == 0) {
            fan(fill, outer, center);
        } else if (2 * stroke >= Math.min(width, height)) {
            fan(edge, outer, center);
        } else {
            var inner = contour(stroke, stroke, width - stroke, height - stroke, Math.max(0, shape.radius() - stroke));
            fan(fill, inner, center);
            for (int i = 0; i < outer.size(); i++) {
                int j = (i + 1) % outer.size();
                triangle(edge, outer.get(i), outer.get(j), inner.get(i));
                triangle(edge, outer.get(j), inner.get(j), inner.get(i));
            }
        }
        return new Meshes(FilledGeometry.ofTriangles(fill), FilledGeometry.ofTriangles(edge));
    }

    private static List<Point<Local>> contour(double left, double top, double right, double bottom, double radius) {
        var points = new ArrayList<Point<Local>>(4 * (ARC_STEPS + 1));
        arc(points, right - radius, top + radius, radius, 0, -1);
        arc(points, right - radius, bottom - radius, radius, 1, 0);
        arc(points, left + radius, bottom - radius, radius, 0, 1);
        arc(points, left + radius, top + radius, radius, -1, 0);
        return points;
    }

    private static void arc(List<Point<Local>> points, double x, double y, double radius, int dx, int dy) {
        points.add(new Point<>(x + radius * dx, y + radius * dy));
        for (int step = 1; step < ARC_STEPS; step++) {
            double angle = step * Math.PI / (2 * ARC_STEPS);
            double c = Math.cos(angle), s = Math.sin(angle);
            points.add(new Point<>(x + radius * (dx * c - dy * s), y + radius * (dy * c + dx * s)));
        }
        points.add(new Point<>(x - radius * dy, y + radius * dx));
    }

    private static void fan(List<Triangle<Local>> triangles, List<Point<Local>> contour, Point<Local> center) {
        for (int i = 0; i < contour.size(); i++) {
            triangle(triangles, center, contour.get(i), contour.get((i + 1) % contour.size()));
        }
    }

    private static void triangle(List<Triangle<Local>> triangles, Point<Local> a, Point<Local> b, Point<Local> c) {
        double first = (b.x() - a.x()) * (c.y() - a.y());
        double second = (b.y() - a.y()) * (c.x() - a.x());
        if (first - second > 4 * Math.ulp(Math.max(Math.abs(first), Math.abs(second)))) {
            triangles.add(new Triangle<>(a, b, c));
        }
    }

    private record Shape(float width, float height, float radius, float stroke) {}
    private record Meshes(FilledGeometry<Local> fill, FilledGeometry<Local> stroke) {}
}
