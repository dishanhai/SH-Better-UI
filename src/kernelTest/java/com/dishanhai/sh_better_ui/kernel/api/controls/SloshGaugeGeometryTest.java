// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import com.dishanhai.sh_better_ui.kernel.api.controls.SloshGaugeState.Surface;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Point;
import com.dishanhai.sh_better_ui.kernel.api.geometry.RoundedFill;
import com.dishanhai.sh_better_ui.kernel.api.geometry.RoundedFill.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SloshGaugeGeometryTest {
    @Test
    void restingLiquidSharesTheBottomCornersAtSubGuiPixelPositions() {
        var chamber = new RoundedFill(84, 176, 18);
        var surface = new Surface(39, 0);
        var liquid = chamber.filled(Direction.UP, 84, x -> surface.topAt(x, 84, 176));
        for (double y = 157.125; y < 176; y += 0.25) {
            for (double x = 0.125; x < 84; x += 0.25) {
                var pixel = new Point<Local>(x, y);
                assertEquals(chamber.interior().contains(pixel), liquid.contains(pixel), pixel::toString);
            }
        }
        assertTrue(liquid.contains(new Point<>(0.125, 158.125)));
        assertFalse(liquid.contains(new Point<>(0.125, 175.875)));
    }

    @Test
    void tiltedSurfaceIsContinuousAndClippedToTheChamber() {
        var chamber = new RoundedFill(84, 176, 18);
        var surface = new Surface(4, Math.toRadians(30));
        var liquid = chamber.filled(Direction.UP, 84, x -> surface.topAt(x, 84, 176));
        for (double x = 0.125; x < 84; x += 0.25) {
            for (double y = 140.125; y < 176; y += 0.25) {
                var pixel = new Point<Local>(x, y);
                assertEquals(y >= surface.topAt(x, 84, 176) && chamber.interior().contains(pixel),
                        liquid.contains(pixel), pixel::toString);
            }
        }
    }

    @Test
    void flatEndpointsWorkForSquareAndCapsuleChambers() {
        for (double radius : new double[]{0, 4.5}) {
            var chamber = new RoundedFill(9, 9, radius);
            for (var direction : Direction.values()) {
                assertTrue(chamber.filled(direction, -0.1).triangles().isEmpty());
                assertSame(chamber.interior(), chamber.filled(direction, 1.1));
            }
        }
    }

    @Test
    void rightwardHoldWaveCoversTheCompleteLeadingCapsuleEnd() {
        var chamber = new RoundedFill(168, 20, 10);
        var progress = chamber.filled(Direction.RIGHT, 2, y -> 56 + 6 * Math.sin(y * Math.PI / 10));
        for (double y = 0.125; y < 20; y += 0.25) {
            for (double x = 0.125; x < 11; x += 0.25) {
                var pixel = new Point<Local>(x, y);
                assertEquals(chamber.interior().contains(pixel), progress.contains(pixel), pixel::toString);
            }
        }
        assertTrue(progress.contains(new Point<>(61, 5)));
        assertFalse(progress.contains(new Point<>(62, 5)));
    }

    @Test
    void upwardHoldWaveCoversTheCompleteBottomEdge() {
        var chamber = new RoundedFill(40, 20, 10);
        var progress = chamber.filled(Direction.UP, 2, x -> 7 + 2 * Math.sin(x * Math.PI / 10));
        for (double x = 0.125; x < 40; x += 0.25) {
            for (double y = 10.125; y < 20; y += 0.25) {
                var pixel = new Point<Local>(x, y);
                assertEquals(chamber.interior().contains(pixel), progress.contains(pixel), pixel::toString);
            }
        }
        assertFalse(progress.contains(new Point<>(15, 4)));
        assertTrue(progress.contains(new Point<>(15, 6)));
    }

    @Test
    void progressStartsAtTheOppositeEdgeOfEachDirection() {
        var shape = new RoundedFill(24, 12, 0);
        assertEquals(9, shape.filled(Direction.UP, 0.25).bounds().orElseThrow().top());
        assertEquals(3, shape.filled(Direction.DOWN, 0.25).bounds().orElseThrow().bottom());
        assertEquals(18, shape.filled(Direction.LEFT, 0.25).bounds().orElseThrow().left());
        assertEquals(6, shape.filled(Direction.RIGHT, 0.25).bounds().orElseThrow().right());
    }
}
