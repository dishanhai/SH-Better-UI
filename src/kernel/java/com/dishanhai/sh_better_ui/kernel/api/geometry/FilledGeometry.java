// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.geometry;

import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Point;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Space;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.orientation;

/** Experimental immutable filled mesh. Drawing and hit testing share the same triangles. */
public final class FilledGeometry<S extends Space> {
    public static final int MAX_POLYGON_VERTICES = 256;
    public static final int MAX_CHECKS = 1_000_000;
    public static final int MAX_TRIANGLES = 4_096;

    private final List<Triangle<S>> triangles;
    private final Optional<Bounds<S>> bounds;

    private FilledGeometry(List<Triangle<S>> triangles) {
        this.triangles = List.copyOf(triangles);
        bounds = computeBounds(this.triangles);
    }

    /** Builds a mesh from a tessellation supplied by the caller. */
    public static <S extends Space> FilledGeometry<S> ofTriangles(List<Triangle<S>> triangles) {
        if (triangles.size() > MAX_TRIANGLES) throw new IllegalArgumentException("Too many mesh triangles");
        return new FilledGeometry<>(triangles);
    }

    public static <S extends Space> FilledGeometry<S> rectangle(Bounds<S> box) {
        if (box.empty()) return new FilledGeometry<>(List.of());
        var tl = new Point<S>(box.left(), box.top());
        var tr = new Point<S>(box.right(), box.top());
        var br = new Point<S>(box.right(), box.bottom());
        var bl = new Point<S>(box.left(), box.bottom());
        return new FilledGeometry<>(List.of(new Triangle<>(tl, tr, br), new Triangle<>(tl, br, bl)));
    }

    /**
     * Tessellates a simple contour with implicit closure in either winding. Each consecutive triple
     * must form a turn. Repeated vertices, self-intersections and numerically degenerate contours
     * cause {@link IllegalArgumentException}. Exceeding the work limit yields no partial result.
     */
    public static <S extends Space> FilledGeometry<S> polygon(List<Point<S>> contour, int maxChecks) {
        int size = contour.size();
        if (size < 3 || size > MAX_POLYGON_VERTICES) {
            throw new IllegalArgumentException("Invalid polygon vertex count: " + size);
        }
        var budget = new Budget(maxChecks);
        var vertices = new ArrayList<>(List.copyOf(contour));
        int first = validate(vertices, budget);
        int winding = orientation(previous(vertices, first), vertices.get(first), next(vertices, first));
        var result = new ArrayList<Triangle<S>>(size - 2);
        while (vertices.size() > 3) {
            int ear = findEar(vertices, winding, budget);
            if (ear == -1) throw new IllegalArgumentException("Polygon has no non-degenerate ear");
            append(result, previous(vertices, ear), vertices.get(ear), next(vertices, ear));
            vertices.remove(ear);
        }
        if (orientation(vertices.get(0), vertices.get(1), vertices.get(2)) != winding) {
            throw new IllegalArgumentException("Polygon has a degenerate remainder");
        }
        append(result, vertices.get(0), vertices.get(1), vertices.get(2));
        return new FilledGeometry<>(result);
    }

    public List<Triangle<S>> triangles() { return triangles; }

    public Optional<Bounds<S>> bounds() { return bounds; }

    /** Tests the closed triangle boundaries. Resolve shared-edge ties in the caller's draw order. */
    public boolean contains(Point<S> point) {
        Objects.requireNonNull(point, "point");
        if (bounds.isEmpty() || !bounds.orElseThrow().contains(point)) return false;
        for (var triangle : triangles) {
            if (inside(triangle.a, triangle.b, triangle.c, point, 1)) return true;
        }
        return false;
    }

    /** A collapsed or non-invertible transform produces an empty mesh. */
    public <T extends Space> FilledGeometry<T> transform(Transform<S, T> transform) {
        if (transform.inverse().isEmpty()) return new FilledGeometry<>(List.of());
        var result = new ArrayList<Triangle<T>>(triangles.size());
        for (var triangle : triangles) {
            append(result, transform.map(triangle.a), transform.map(triangle.b), transform.map(triangle.c));
        }
        return new FilledGeometry<>(result);
    }

    /** Clips the actual triangles. Bounds, drawing and picking see the same clipped result. */
    public FilledGeometry<S> clip(Bounds<S> clip, int maxChecks) {
        var budget = new Budget(maxChecks);
        if (clip.empty()) return new FilledGeometry<>(List.of());
        var result = new ArrayList<Triangle<S>>();
        for (var triangle : triangles) {
            List<Point<S>> polygon = List.of(triangle.a, triangle.b, triangle.c);
            polygon = cut(polygon, true, clip.left(), true, budget);
            polygon = cut(polygon, true, clip.right(), false, budget);
            polygon = cut(polygon, false, clip.top(), true, budget);
            polygon = cut(polygon, false, clip.bottom(), false, budget);
            for (int i = 1; i + 1 < polygon.size(); i++) {
                append(result, polygon.get(0), polygon.get(i), polygon.get(i + 1));
            }
        }
        return new FilledGeometry<>(result);
    }

    public record Triangle<S extends Space>(Point<S> a, Point<S> b, Point<S> c) {
        public Triangle {
            if (orientation(a, b, c) <= 0) throw new IllegalArgumentException("Triangle requires positive winding");
        }

        public double area() {
            return ((b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x())) / 2;
        }
    }

    public static final class BudgetExceededException extends IllegalStateException {
        private BudgetExceededException(String message) { super(message); }
    }

    private static <S extends Space> int validate(List<Point<S>> vertices, Budget budget) {
        int first = 0;
        for (int i = 0; i < vertices.size(); i++) {
            var a = vertices.get(i);
            var b = next(vertices, i);
            if (orientation(previous(vertices, i), a, b) == 0) {
                throw new IllegalArgumentException("Polygon has a repeated or collinear corner");
            }
            var lowest = vertices.get(first);
            if (a.x() < lowest.x() || a.x() == lowest.x() && a.y() < lowest.y()) first = i;
            for (int j = i + 2; j < vertices.size(); j++) {
                if (i == 0 && j == vertices.size() - 1) continue;
                budget.use(1);
                if (intersects(a, b, vertices.get(j), next(vertices, j))) {
                    throw new IllegalArgumentException("Polygon edges intersect");
                }
            }
        }
        return first;
    }

    private static <S extends Space> int findEar(List<Point<S>> vertices, int winding, Budget budget) {
        for (int i = 0; i < vertices.size(); i++) {
            budget.use(1);
            var a = previous(vertices, i);
            var b = vertices.get(i);
            var c = next(vertices, i);
            if (orientation(a, b, c) != winding) continue;
            boolean clear = true;
            for (var point : vertices) {
                if (point == a || point == b || point == c) continue;
                budget.use(1);
                if (inside(a, b, c, point, winding)) {
                    clear = false;
                    break;
                }
            }
            if (clear) return i;
        }
        return -1;
    }

    private static boolean inside(Point<?> a, Point<?> b, Point<?> c, Point<?> point, int winding) {
        return orientation(a, b, point) * winding >= 0
                && orientation(b, c, point) * winding >= 0
                && orientation(c, a, point) * winding >= 0;
    }

    private static boolean intersects(Point<?> a, Point<?> b, Point<?> c, Point<?> d) {
        int abc = orientation(a, b, c), abd = orientation(a, b, d);
        int cda = orientation(c, d, a), cdb = orientation(c, d, b);
        return abc * abd < 0 && cda * cdb < 0
                || abc == 0 && onSegment(a, b, c) || abd == 0 && onSegment(a, b, d)
                || cda == 0 && onSegment(c, d, a) || cdb == 0 && onSegment(c, d, b);
    }

    private static boolean onSegment(Point<?> a, Point<?> b, Point<?> p) {
        return p.x() >= Math.min(a.x(), b.x()) && p.x() <= Math.max(a.x(), b.x())
                && p.y() >= Math.min(a.y(), b.y()) && p.y() <= Math.max(a.y(), b.y());
    }

    private static <T> T previous(List<T> list, int i) {
        return list.get(i == 0 ? list.size() - 1 : i - 1);
    }

    private static <T> T next(List<T> list, int i) {
        return list.get(i + 1 == list.size() ? 0 : i + 1);
    }

    private static <S extends Space> List<Point<S>> cut(List<Point<S>> input, boolean xAxis, double edge,
                                                       boolean keepAbove, Budget budget) {
        if (input.isEmpty()) return input;
        budget.use(input.size());
        var output = new ArrayList<Point<S>>(input.size() + 1);
        var previous = input.get(input.size() - 1);
        double from = xAxis ? previous.x() : previous.y();
        boolean wasInside = keepAbove ? from >= edge : from <= edge;
        for (var current : input) {
            double to = xAxis ? current.x() : current.y();
            boolean isInside = keepAbove ? to >= edge : to <= edge;
            if (wasInside != isInside && from != edge && to != edge) {
                if (xAxis) output.add(new Point<>(edge, interpolate(previous.y(), current.y(), from, to, edge)));
                else output.add(new Point<>(interpolate(previous.x(), current.x(), from, to, edge), edge));
            }
            if (isInside) output.add(current);
            previous = current;
            from = to;
            wasInside = isInside;
        }
        return output;
    }

    private static double interpolate(double a, double b, double from, double to, double edge) {
        // Adjacent triangles visit the shared edge in opposite directions. Use the same endpoint
        // order in both cases so their intersection vertices are identical.
        double start = from < to ? a : b, end = from < to ? b : a;
        double fraction = (edge - Math.min(from, to)) / Math.abs(to - from);
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), start + fraction * (end - start)));
    }

    private static <S extends Space> void append(List<Triangle<S>> output, Point<S> a, Point<S> b, Point<S> c) {
        int winding = orientation(a, b, c);
        if (winding == 0) return;
        if (output.size() == MAX_TRIANGLES) throw new BudgetExceededException("Triangle limit exceeded");
        output.add(winding > 0 ? new Triangle<>(a, b, c) : new Triangle<>(a, c, b));
    }

    private static <S extends Space> Optional<Bounds<S>> computeBounds(List<Triangle<S>> triangles) {
        if (triangles.isEmpty()) return Optional.empty();
        double left = Double.POSITIVE_INFINITY, top = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
        for (var t : triangles) {
            left = Math.min(left, Math.min(t.a.x(), Math.min(t.b.x(), t.c.x())));
            top = Math.min(top, Math.min(t.a.y(), Math.min(t.b.y(), t.c.y())));
            right = Math.max(right, Math.max(t.a.x(), Math.max(t.b.x(), t.c.x())));
            bottom = Math.max(bottom, Math.max(t.a.y(), Math.max(t.b.y(), t.c.y())));
        }
        return Optional.of(new Bounds<>(left, top, right, bottom));
    }

    private static final class Budget {
        private int remaining;

        private Budget(int maxChecks) {
            if (maxChecks < 1 || maxChecks > MAX_CHECKS) throw new IllegalArgumentException("Invalid geometry budget");
            remaining = maxChecks;
        }

        private void use(int count) {
            if (count > remaining) throw new BudgetExceededException("Geometry work budget exceeded");
            remaining -= count;
        }
    }
}
