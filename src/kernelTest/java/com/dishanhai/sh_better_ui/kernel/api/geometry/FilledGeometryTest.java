// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.geometry;

import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Framebuffer;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Gui;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Point;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Transform;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Matrix;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FilledGeometryTest {
    @Test
    void concavePolygonSharesItsDrawTrianglesAndHitBoundary() {
        var corners = new ArrayList<>(List.of(point(0, 0), point(8, 0), point(8, 2),
                point(3, 2), point(3, 7), point(0, 7)));
        var mesh = FilledGeometry.polygon(corners, 500);
        assertEquals(4, mesh.triangles().size());
        assertEquals(31, mesh.triangles().stream().mapToDouble(FilledGeometry.Triangle::area).sum(), 1e-9);
        assertTrue(mesh.contains(point(6, 1)));
        assertTrue(mesh.contains(point(3, 4)));
        assertFalse(mesh.contains(point(6, 4)));
        assertEquals(mesh.triangles(), FilledGeometry.polygon(corners, 500).triangles());

        Collections.reverse(corners);
        var reversed = FilledGeometry.polygon(corners, 500);
        for (int y = 0; y <= 14; y++) {
            for (int x = 0; x <= 16; x++) {
                var sample = point(x * 0.5, y * 0.5);
                assertEquals(mesh.contains(sample), reversed.contains(sample));
            }
        }
        corners.clear();
        assertTrue(mesh.contains(point(2, 5)));
        assertThrows(UnsupportedOperationException.class, () -> mesh.triangles().clear());
    }

    @Test
    void composedSpacesAndReflectionUseTheProjectedTrianglesForPicking() {
        Transform<Local, Gui> pose = new Transform<>(new Matrix(0, -2, -1, 0, 20, 20));
        Transform<Gui, Framebuffer> scale = new Transform<>(new Matrix(1.5, 0, 0, 2.5, 0, 0));
        var transform = scale.compose(pose);
        var local = point(1, 2);
        assertEquals(new Point<Framebuffer>(27, 45), transform.map(local));
        var restored = transform.inverse().orElseThrow().map(transform.map(local));
        assertEquals(local.x(), restored.x(), 1e-12);
        assertEquals(local.y(), restored.y(), 1e-12);

        var mesh = FilledGeometry.rectangle(new Bounds<Local>(0, 0, 4, 4)).transform(transform);
        assertTrue(mesh.contains(transform.map(local)));
        assertFalse(mesh.contains(transform.map(point(5, 2))));
        assertEquals(120, mesh.triangles().stream().mapToDouble(FilledGeometry.Triangle::area).sum(), 1e-9);
    }

    @Test
    void clippingKeepsDrawAndHitInTheSameRegion() {
        var original = FilledGeometry.rectangle(new Bounds<Local>(0, 0, 8, 8));
        var clip = new Bounds<Local>(3, 1, 6, 4);
        var clipped = original.clip(clip, 100);
        assertEquals(clip, clipped.bounds().orElseThrow());
        assertTrue(clipped.contains(point(5, 2)));
        assertTrue(clipped.contains(point(6, 4)));
        assertFalse(clipped.contains(point(2, 2)));
        assertEquals(9, clipped.triangles().stream().mapToDouble(FilledGeometry.Triangle::area).sum(), 1e-9);

        assertTrue(original.clip(new Bounds<>(8, 0, 10, 8), 100).bounds().isEmpty());
        assertTrue(original.clip(new Bounds<>(1, 0, 1, 8), 100).triangles().isEmpty());
        assertThrows(FilledGeometry.BudgetExceededException.class, () -> original.clip(clip, 1));
        assertEquals(2, original.triangles().size());
    }

    @Test
    void invalidDegenerateAndOverBudgetGeometryHasNoPartialMesh() {
        assertThrows(IllegalArgumentException.class, () -> point(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new Transform<Local, Gui>(
                new Matrix(Double.POSITIVE_INFINITY, 0, 0, 1, 0, 0)));
        var box = List.of(point(0, 0), point(5, 0), point(5, 5), point(0, 5));
        assertThrows(FilledGeometry.BudgetExceededException.class, () -> FilledGeometry.polygon(box, 1));
        assertThrows(IllegalArgumentException.class, () -> FilledGeometry.polygon(
                List.of(point(0, 0), point(5, 5), point(0, 5), point(5, 0)), 100));
        assertThrows(IllegalArgumentException.class, () -> FilledGeometry.polygon(
                List.of(point(0, 0), point(1, 1), point(2, 2)), 100));
        assertThrows(IllegalArgumentException.class, () -> FilledGeometry.polygon(
                List.of(point(0, 0), point(3, 0), point(3, 1), point(1, 0), point(1, 2)), 100));

        Transform<Local, Gui> collapsed = new Transform<>(new Matrix(1, 0, 0, 0, 0, 0));
        assertTrue(collapsed.inverse().isEmpty());
        var mesh = FilledGeometry.polygon(box, 100).transform(collapsed);
        assertTrue(mesh.triangles().isEmpty());
        assertFalse(mesh.contains(new Point<>(0, 0)));
    }

    @Test
    void seededContoursAgreeWithRayCrossingAfterTransformAndClip() {
        var random = new Random(31701);
        Transform<Local, Gui> pose = new Transform<>(new Matrix(0.8, 0.6, 0.6, -0.8, 2.7, -1.3));
        var inverse = pose.inverse().orElseThrow();
        var clip = new Bounds<Gui>(-5.1, -4.2, 7.8, 6.3);
        for (int trial = 0; trial < 16; trial++) {
            var contour = new ArrayList<Point<Local>>();
            for (int vertex = 0; vertex < 12; vertex++) {
                double angle = vertex * Math.PI / 6;
                double radius = 2 + random.nextDouble() * 6;
                contour.add(point(Math.cos(angle) * radius, Math.sin(angle) * radius));
            }
            var mesh = FilledGeometry.polygon(contour, 1_000).transform(pose).clip(clip, 1_000);
            for (int sample = 0; sample < 200; sample++) {
                var gui = new Point<Gui>(random.nextDouble() * 20 - 10, random.nextDouble() * 20 - 10);
                assertEquals(clip.contains(gui) && rayCrossing(contour, inverse.map(gui)), mesh.contains(gui),
                        "trial=" + trial + " sample=" + gui);
            }
        }
    }

    private static boolean rayCrossing(List<Point<Local>> contour, Point<Local> point) {
        boolean inside = false;
        var previous = contour.get(contour.size() - 1);
        for (var current : contour) {
            if ((previous.y() > point.y()) != (current.y() > point.y())) {
                double crossing = previous.x() + (current.x() - previous.x())
                        * (point.y() - previous.y()) / (current.y() - previous.y());
                if (point.x() < crossing) inside = !inside;
            }
            previous = current;
        }
        return inside;
    }

    private static Point<Local> point(double x, double y) {
        return new Point<>(x, y);
    }
}
