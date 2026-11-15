// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.motion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhotonKnotTest {
    @Test
    void emitsTranslucentQuadsInsideTheViewport() {
        var vertices = sample(new PhotonKnot(), 0, false);
        assertFalse(vertices.isEmpty());
        assertEquals(0, vertices.size() % 4);
        assertTrue(vertices.stream().allMatch(vertex -> Math.abs(vertex.x) < .5f && Math.abs(vertex.y) < .5f));
        assertTrue(vertices.stream().anyMatch(vertex -> vertex.alpha() == 0));
        assertTrue(vertices.stream().anyMatch(vertex -> vertex.alpha() > 90));
        assertTrue(vertices.stream().allMatch(vertex -> vertex.alpha() < 255));
    }

    @Test
    void pausesAndResumesWithoutConsumingHiddenOrReducedMotionTime() {
        var knot = new PhotonKnot();
        var initial = sample(knot, 0, false);
        var moving = sample(knot, 100, false);
        assertNotEquals(initial, moving);
        knot.setPaused(true);
        assertEquals(moving, sample(knot, 200, false));
        knot.setPaused(false);
        assertEquals(moving, sample(knot, 5_000, false));
        assertEquals(moving, sample(knot, 5_100, true));
        assertEquals(moving, sample(knot, 10_000, false));
        knot.suspend();
        assertEquals(moving, sample(knot, 20_000, false));
        assertNotEquals(moving, sample(knot, 20_100, false));
    }

    private static List<Vertex> sample(PhotonKnot knot, long time, boolean reducedMotion) {
        var vertices = new ArrayList<Vertex>();
        knot.sample(time, reducedMotion, 0x2288ff, 0xf0fffa,
                (x, y, color) -> vertices.add(new Vertex(x, y, color)));
        return vertices;
    }

    private record Vertex(float x, float y, int color) {
        int alpha() {
            return color >>> 24;
        }
    }
}
