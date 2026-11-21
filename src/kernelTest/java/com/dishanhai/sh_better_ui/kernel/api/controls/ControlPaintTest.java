// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControlPaintTest {
    private record Shape(double x, double y, double w, double h, double radius, double stroke, int edge, int fill) {}

    @Test
    void toggleUsesNetworkToggleGeometryAtBothEndsAndScales() {
        List<Shape> shapes = new ArrayList<>();
        ControlPaint.Rounded capture = (x, y, w, h, r, s, e, f) ->
                shapes.add(new Shape(x, y, w, h, r, s, e, f));
        ControlPaint.toggle(capture, 10, 20, 1, 0, 1, 2, 3);
        assertEquals(List.of(new Shape(10, 20, 22, 12, 6, 1, 1, 2),
                new Shape(12, 22, 8, 8, 4, 0, 0, 3)), shapes);
        shapes.clear();
        ControlPaint.toggle(capture, 10, 20, 2, 1, 1, 2, 3);
        assertEquals(new Shape(34, 24, 16, 16, 8, 0, 0, 3), shapes.get(1));
    }

    @Test
    void sliderUsesRoundedPositionAndRectangularThumb() {
        List<Shape> shapes = new ArrayList<>();
        ControlPaint.Rectangle capture = (x, y, w, h, c) -> shapes.add(new Shape(x, y, w, h, 0, 0, 0, c));
        ControlPaint.slider(capture, 10, 20, 21, 0.5, 1, 1, 2);
        assertEquals(List.of(new Shape(10, 20, 21, 1, 0, 0, 0, 1),
                new Shape(10, 20, 11, 1, 0, 0, 0, 2),
                new Shape(19, 19, 4, 3, 0, 0, 0, 2)), shapes);
        shapes.clear();
        ControlPaint.slider(capture, 10, 20, 21, 0, 1.5, 1, 2);
        assertEquals(List.of(new Shape(10, 20, 21, 1.5, 0, 0, 0, 1),
                new Shape(7, 18.5, 6, 4.5, 0, 0, 0, 2)), shapes);
    }
}
