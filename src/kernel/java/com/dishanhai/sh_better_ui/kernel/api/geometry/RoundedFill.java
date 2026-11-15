// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.geometry;

import com.dishanhai.sh_better_ui.kernel.api.geometry.FilledGeometry.Triangle;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ToDoubleFunction;

/** A rounded interior and a fill front clipped to the same contour in continuous GUI units. */
public final class RoundedFill {
    private static final int ARC_STEPS = 8;
    private static final int MAX_STRIPS = 96;

    /** The direction in which the filled region grows from the opposite edge. */
    public enum Direction {
        UP(true, false), DOWN(true, true), LEFT(false, false), RIGHT(false, true);

        private final boolean vertical;
        private final boolean increasing;

        Direction(boolean vertical, boolean increasing) {
            this.vertical = vertical;
            this.increasing = increasing;
        }
    }

    private final int width;
    private final int height;
    private final List<Point<Local>> contour;
    private final FilledGeometry<Local> interior;

    /** Builds a local contour from positive dimensions and clamps the radius to the shorter half-side. */
    public RoundedFill(int width, int height, double radius) {
        if (width <= 0 || height <= 0 || !Double.isFinite(radius)) {
            throw new IllegalArgumentException("Invalid rounded fill bounds");
        }
        this.width = width;
        this.height = height;
        double r = Math.max(0, Math.min(radius, Math.min(width, height) / 2.0));
        var points = new ArrayList<Point<Local>>();
        arc(points, width - r, r, r, 0, -1);
        arc(points, width - r, height - r, r, 1, 0);
        arc(points, r, height - r, r, 0, 1);
        arc(points, r, r, r, -1, 0);
        contour = List.copyOf(points);
        var triangles = new ArrayList<Triangle<Local>>();
        appendFan(triangles, contour);
        interior = FilledGeometry.ofTriangles(triangles);
    }

    /** Returns the complete, immutable interior mesh. */
    public FilledGeometry<Local> interior() { return interior; }

    /** Builds a straight progress fill. Finite values are clamped to the range [0, 1]. */
    public FilledGeometry<Local> filled(Direction direction, double progress) {
        if (!Double.isFinite(progress)) throw new IllegalArgumentException("Progress must be finite");
        double fraction = Math.max(0, Math.min(1, progress));
        int depth = direction.vertical ? height : width;
        double edge = depth * (direction.increasing ? fraction : 1 - fraction);
        return filled(direction, direction.vertical ? width : height, position -> edge);
    }

    /**
     * Samples and linearly connects a custom fill edge. Vertical fills receive local x and return
     * local y. Horizontal fills receive local y and return local x. Boundary coordinates can lie
     * outside the interior and must be finite. The sampling step uses positive GUI units and
     * grows as needed to cap the number of strips.
     */
    public FilledGeometry<Local> filled(Direction direction, int step, DoubleUnaryOperator edge) {
        if (step <= 0) throw new IllegalArgumentException("Fill step must be positive");
        int extent = direction.vertical ? width : height;
        int depth = direction.vertical ? height : width;
        double first = sample(edge, 0);
        step = Math.max(step, 1 + (extent - 1) / MAX_STRIPS);
        var triangles = new ArrayList<Triangle<Local>>();
        if (step >= extent) {
            double last = sample(edge, extent);
            if (direction.increasing ? Math.min(first, last) >= depth : Math.max(first, last) <= 0) return interior;
            appendFan(triangles, clipFront(contour, direction, 0, extent, first, last));
            return FilledGeometry.ofTriangles(triangles);
        }
        for (int start = 0; start < extent;) {
            int end = start + Math.min(step, extent - start);
            double last = sample(edge, end);
            int from = start;
            var strip = clip(contour, p -> across(p, direction) - from);
            strip = clip(strip, p -> end - across(p, direction));
            appendFan(triangles, clipFront(strip, direction, from, end, first, last));
            start = end;
            first = last;
        }
        return FilledGeometry.ofTriangles(triangles);
    }

    private static double sample(DoubleUnaryOperator edge, int position) {
        double sample = edge.applyAsDouble(position);
        if (!Double.isFinite(sample)) throw new IllegalArgumentException("Fill edge must be finite");
        return sample;
    }

    private static List<Point<Local>> clipFront(List<Point<Local>> polygon, Direction direction,
                                               int start, int end, double first, double last) {
        return clip(polygon, p -> {
            double part = (across(p, direction) - start) / (end - start);
            double front = first == last ? first : (1 - part) * first + part * last;
            double offset = (direction.vertical ? p.y() : p.x()) - front;
            return direction.increasing ? -offset : offset;
        });
    }

    private static double across(Point<Local> p, Direction direction) {
        return direction.vertical ? p.x() : p.y();
    }

    private static List<Point<Local>> clip(List<Point<Local>> polygon, ToDoubleFunction<Point<Local>> distance) {
        if (polygon.isEmpty()) return polygon;
        var result = new ArrayList<Point<Local>>(polygon.size() + 1);
        var previous = polygon.get(polygon.size() - 1);
        double from = distance.applyAsDouble(previous);
        for (var point : polygon) {
            double to = distance.applyAsDouble(point);
            if (from < 0 && to > 0 || from > 0 && to < 0) {
                // Use the same interpolation order if an adjacent strip visits this edge in reverse.
                var low = from < to ? previous : point;
                var high = from < to ? point : previous;
                double negative = -Math.min(from, to), positive = Math.max(from, to);
                double part = negative < positive
                        ? (negative / positive) / (1 + negative / positive)
                        : 1 / (1 + positive / negative);
                result.add(new Point<>(low.x() + part * (high.x() - low.x()),
                        low.y() + part * (high.y() - low.y())));
            }
            if (to >= 0) result.add(point);
            previous = point;
            from = to;
        }
        return result;
    }

    private static void arc(List<Point<Local>> contour, double x, double y, double radius, int dx, int dy) {
        contour.add(new Point<>(x + radius * dx, y + radius * dy));
        if (radius == 0) return;
        for (int step = 1; step < ARC_STEPS; step++) {
            double angle = step * Math.PI / (2 * ARC_STEPS);
            double c = Math.cos(angle), s = Math.sin(angle);
            contour.add(new Point<>(x + radius * (dx * c - dy * s), y + radius * (dy * c + dx * s)));
        }
        contour.add(new Point<>(x - radius * dy, y + radius * dx));
    }

    private static void appendFan(List<Triangle<Local>> output, List<Point<Local>> points) {
        for (int i = 2; i < points.size(); i++) {
            var a = points.get(0);
            var b = points.get(i - 1);
            var c = points.get(i);
            if (Geometry2.orientation(a, b, c) > 0) output.add(new Triangle<>(a, b, c));
        }
    }
}
