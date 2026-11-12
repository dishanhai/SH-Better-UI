// Author: dishanhai
package com.dishanhai.sh_better_ui.spatial.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Point;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.ShadowPath;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Shape;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Transform;
import com.dishanhai.sh_better_ui.spatial.SpatialScene.Spec;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Shadow;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.ShadowType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class SpatialShadowPathTest {
    @Test
    void validatesAndCopiesBoundedSimpleContoursWithHoles() {
        var outer = new ArrayList<>(box(-4, -6, 20, 22));
        var hole = box(1, 2, 5, 6);
        var path = new ShadowPath(List.of(outer, hole));
        outer.clear();
        assertEquals(8, path.contours().stream().mapToInt(List::size).sum());
        assertEquals(-4, path.bounds().left());
        assertEquals(-6, path.bounds().top());
        assertEquals(20, path.bounds().right());
        assertEquals(22, path.bounds().bottom());
        assertThrows(UnsupportedOperationException.class, () -> path.contours().get(0).clear());
        assertThrows(IllegalArgumentException.class, () -> new ShadowPath(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ShadowPath(List.of(box(0, 0, 50_000, 20))));
        assertThrows(IllegalArgumentException.class, () -> new ShadowPath(List.of(
                box(0, 0, 20, 20), box(10, 10, 30, 30))));
        assertThrows(IllegalArgumentException.class, () -> new ShadowPath(List.of(
                box(0, 0, 20, 20), box(20, 2, 30, 10))));
        assertThrows(IllegalArgumentException.class, () -> new ShadowPath(List.of(
                List.of(point(0, 0), point(20, 20), point(20, 0), point(0, 20)))));
        assertEquals(ShadowPath.MAX_EDGES, new ShadowPath(List.of(ring(ShadowPath.MAX_EDGES))).contours().get(0).size());
        assertThrows(IllegalArgumentException.class, () -> new ShadowPath(List.of(ring(ShadowPath.MAX_EDGES + 1))));
    }

    @Test
    void emitsLocalPathShadowsInOrderAndClipsTheirBoundsThroughTheScene() {
        try (var scene = new Scene(() -> {})) {
            var parent = scene.create(new Spec(new UiId("test", "parent"), null,
                    Shape.rectangle(25, 25), new Transform(30, 30, 1, 1, 0, 0, 0), 0, 0, false, true));
            var node = scene.create(new Spec(new UiId("test", "path"), parent.id(),
                    Shape.rectangle(10, 10), new Transform(6, 5, 1, 1, 0, 0, 0), 1, 0, true, false));
            var path = new ShadowPath(List.of(box(-10, -7, 30, 20), box(0, 0, 4, 4)));
            node.shadowPath(path);
            node.shadows(List.of(layer(ShadowType.DROP), layer(ShadowType.INNER)));
            var paints = new ArrayList<Scene.Paint>();
            var meshes = new ArrayList<List<SpatialMesh.Vertex>>();
            scene.draw(100, 100, 1, (paint, mesh, backdrop) -> {
                paints.add(paint);
                meshes.add(mesh);
            });
            assertEquals(List.of(ShadowType.SURFACE, ShadowType.DROP, ShadowType.SURFACE, ShadowType.INNER),
                    paints.stream().map(Scene.Paint::type).toList());
            assertEquals(path, paints.get(1).shadowPath());
            assertEquals(path, paints.get(3).shadowPath());
            assertNull(paints.get(2).shadowPath());
            assertEquals(55, meshes.get(1).stream().mapToDouble(SpatialMesh.Vertex::x).max().orElseThrow());
            assertEquals(-6, meshes.get(1).stream().mapToDouble(SpatialMesh.Vertex::u).min().orElseThrow(), 1e-9);
            assertEquals(node, scene.hitTest(39, 38).orElseThrow());
            assertTrue(scene.hitTest(50, 45).isEmpty());

            paints.clear();
            scene.draw(100, 100, 1, true, false, (paint, mesh, backdrop) -> paints.add(paint), false);
            assertEquals(List.of(ShadowType.SURFACE, ShadowType.SURFACE), paints.stream().map(Scene.Paint::type).toList());
            node.shadowPath(null);
            paints.clear();
            scene.draw(100, 100, 1, true, false, (paint, mesh, backdrop) -> paints.add(paint), false);
            assertEquals(List.of(ShadowType.SURFACE, ShadowType.DROP, ShadowType.SURFACE, ShadowType.INNER),
                    paints.stream().map(Scene.Paint::type).toList());
            assertTrue(paints.stream().allMatch(paint -> paint.shadowPath() == null));
        }
    }

    private static Shadow layer(ShadowType type) {
        return new Shadow(type, 3, 2, 4, 1, 1, 0xff000000, true);
    }

    private static List<Point<Local>> box(double x, double y, double right, double bottom) {
        return List.of(point(x, y), point(right, y), point(right, bottom), point(x, bottom));
    }

    private static List<Point<Local>> ring(int edges) {
        return IntStream.range(0, edges).mapToObj(i -> {
            double radians = 2 * Math.PI * i / edges;
            return point(100 * Math.cos(radians), 100 * Math.sin(radians));
        }).toList();
    }

    private static Point<Local> point(double x, double y) {
        return new Point<>(x, y);
    }
}
