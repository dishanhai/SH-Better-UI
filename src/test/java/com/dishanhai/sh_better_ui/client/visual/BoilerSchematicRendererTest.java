// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoilerSchematicRendererTest {
    @Test void primitivesFitTheirBoundsAndStayBelowCommandBudget() {
        for (int w : new int[]{1, 8, 24, 159, 160, 240, 320, 640}) {
            for (int h : new int[]{1, 8, 32, 63, 64, 104, 208}) {
                var shapes = BoilerSchematicRenderer.shapes(w, h);
                assertTrue(shapes.size() + 3 < 32);
                for (var r : shapes) {
                    assertTrue(r.x0() >= 0 && r.y0() >= 0);
                    assertTrue(r.x1() <= w && r.y1() <= h);
                    assertTrue(r.x1() > r.x0() && r.y1() > r.y0());
                }
            }
        }
    }
    @Test void compactFallbackIsExplicitDeterministicAndImmutable() {
        var compact = BoilerSchematicRenderer.shapes(32, 32);
        assertEquals(5, compact.size());
        assertEquals(compact, BoilerSchematicRenderer.shapes(32, 32));
        assertThrows(UnsupportedOperationException.class, () -> compact.clear());
        assertTrue(BoilerSchematicRenderer.shapes(0, 32).isEmpty());
        assertTrue(BoilerSchematicRenderer.shapes(32, -1).isEmpty());
        assertTrue(BoilerSchematicRenderer.shapes(320, 104).size() > compact.size());
    }
}
