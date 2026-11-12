// Author: dishanhai
package com.dishanhai.sh_better_ui.spatial;

import com.dishanhai.sh_better_ui.kernel.api.geometry.FilledGeometry;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Geometry in local GUI units. Transform matrices use column vectors. */
public final class SpatialGeometry {
    private SpatialGeometry() {}

    static double range(double value, double min, double max, String name) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("Invalid spatial " + name + ": " + value);
        }
        return value;
    }

    public record Point(double x, double y) {}

    public record Matrix(double a, double b, double c, double d, double x, double y) {
        public static final Matrix IDENTITY = new Matrix(1, 0, 0, 1, 0, 0);

        public Point point(double u, double v) {
            return new Point(a * u + c * v + x, b * u + d * v + y);
        }

        public Matrix compose(Matrix local) {
            var origin = point(local.x, local.y);
            return new Matrix(a * local.a + c * local.b, b * local.a + d * local.b,
                    a * local.c + c * local.d, b * local.c + d * local.d, origin.x, origin.y);
        }

        public Matrix inverse() {
            double det = a * d - b * c;
            if (det == 0 || !Double.isFinite(det)) throw new IllegalStateException("Singular spatial transform");
            return new Matrix(d / det, -b / det, -c / det, a / det,
                    (c * y - d * x) / det, (b * x - a * y) / det);
        }
    }

    public record Transform(double x, double y, double scaleX, double scaleY,
                            double radians, double originX, double originY) {
        public static final Transform IDENTITY = new Transform(0, 0, 1, 1, 0, 0, 0);

        public Transform {
            range(x, -32768, 32768, "x");
            range(y, -32768, 32768, "y");
            range(Math.abs(scaleX), 0.01, 16, "scaleX");
            range(Math.abs(scaleY), 0.01, 16, "scaleY");
            range(radians, -1e6, 1e6, "rotation");
            range(originX, -32768, 32768, "originX");
            range(originY, -32768, 32768, "originY");
        }

        public Matrix matrix() {
            double cos = Math.cos(radians), sin = Math.sin(radians);
            double a = cos * scaleX, b = sin * scaleX, c = -sin * scaleY, d = cos * scaleY;
            return new Matrix(a, b, c, d, x + originX - a * originX - c * originY,
                    y + originY - b * originX - d * originY);
        }
    }

    public enum Kind { BOX, ELLIPSE }

    public record Shape(Kind kind, double width, double height, double topLeft,
                        double topRight, double bottomRight, double bottomLeft) {
        public Shape {
            Objects.requireNonNull(kind);
            range(width, .001, 32768, "width");
            range(height, .001, 32768, "height");
            double limit = Math.min(width, height) / 2;
            topLeft = Math.min(limit, range(topLeft, 0, 32768, "topLeft"));
            topRight = Math.min(limit, range(topRight, 0, 32768, "topRight"));
            bottomRight = Math.min(limit, range(bottomRight, 0, 32768, "bottomRight"));
            bottomLeft = Math.min(limit, range(bottomLeft, 0, 32768, "bottomLeft"));
        }

        public static Shape rectangle(double width, double height) { return rounded(width, height, 0); }

        public static Shape rounded(double width, double height, double radius) {
            return new Shape(Kind.BOX, width, height, radius, radius, radius, radius);
        }

        public static Shape capsule(double width, double height) {
            return rounded(width, height, Math.min(width, height) / 2);
        }

        public static Shape ellipse(double width, double height) {
            return new Shape(Kind.ELLIPSE, width, height, 0, 0, 0, 0);
        }

        public static Shape circle(double diameter) { return ellipse(diameter, diameter); }

        /** Exact box/circle distance. For non-circular ellipses this estimates distance with the correct sign. */
        public double distance(double x, double y) {
            double px = x - width / 2, py = y - height / 2;
            if (kind == Kind.ELLIPSE) {
                return (Math.hypot(px / (width / 2), py / (height / 2)) - 1) * Math.min(width, height) / 2;
            }
            double radius = py < 0 ? (px < 0 ? topLeft : topRight) : (px < 0 ? bottomLeft : bottomRight);
            double qx = Math.abs(px) - width / 2 + radius, qy = Math.abs(py) - height / 2 + radius;
            return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - radius;
        }
    }

    /**
     * Closed, flattened contours in local GUI units. Even-odd fill supports holes and
     * disconnected outlines. Contours must be simple, with no shared or crossing edges.
     */
    public static final class ShadowPath {
        public static final int MAX_EDGES = 64;
        public static final double MAX_COORDINATE = 32768;

        private final List<List<Geometry2.Point<Geometry2.Local>>> contours;
        private final Geometry2.Bounds<Geometry2.Local> bounds;

        public ShadowPath(List<? extends List<Geometry2.Point<Geometry2.Local>>> contours) {
            if (contours.isEmpty() || contours.size() > MAX_EDGES / 3) {
                throw new IllegalArgumentException("Invalid shadow contour count");
            }
            var copy = new ArrayList<List<Geometry2.Point<Geometry2.Local>>>(contours.size());
            var edges = new ArrayList<Edge>();
            double left = Double.POSITIVE_INFINITY, top = Double.POSITIVE_INFINITY;
            double right = Double.NEGATIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
            for (var contour : contours) {
                if (contour.size() < 3 || contour.size() > MAX_EDGES - edges.size()) {
                    throw new IllegalArgumentException("Invalid shadow path edge count");
                }
                var points = contour.stream().map(ShadowPath::renderPoint).toList();
                var box = FilledGeometry.polygon(points, FilledGeometry.MAX_CHECKS).bounds().orElseThrow();
                var contourEdges = new ArrayList<Edge>(points.size());
                for (int i = 0; i < points.size(); i++) {
                    var edge = new Edge(points.get(i), points.get((i + 1) % points.size()));
                    for (var previous : edges) {
                        if (edge.intersects(previous)) {
                            throw new IllegalArgumentException("Shadow contours must not touch or intersect");
                        }
                    }
                    contourEdges.add(edge);
                }
                edges.addAll(contourEdges);
                left = Math.min(left, box.left());
                top = Math.min(top, box.top());
                right = Math.max(right, box.right());
                bottom = Math.max(bottom, box.bottom());
                copy.add(points);
            }
            this.contours = List.copyOf(copy);
            bounds = new Geometry2.Bounds<>(left, top, right, bottom);
        }

        public List<List<Geometry2.Point<Geometry2.Local>>> contours() { return contours; }
        public Geometry2.Bounds<Geometry2.Local> bounds() { return bounds; }

        @Override public boolean equals(Object other) {
            return other instanceof ShadowPath path && contours.equals(path.contours);
        }

        @Override public int hashCode() { return contours.hashCode(); }

        private static Geometry2.Point<Geometry2.Local> renderPoint(Geometry2.Point<Geometry2.Local> point) {
            return new Geometry2.Point<>((float) range(point.x(), -MAX_COORDINATE, MAX_COORDINATE, "path x"),
                    (float) range(point.y(), -MAX_COORDINATE, MAX_COORDINATE, "path y"));
        }

        private record Edge(Geometry2.Point<Geometry2.Local> a, Geometry2.Point<Geometry2.Local> b) {
            boolean intersects(Edge other) {
                double c = side(other.a), d = side(other.b);
                double from = other.side(a), to = other.side(b);
                return (c < 0 && d > 0 || c > 0 && d < 0) && (from < 0 && to > 0 || from > 0 && to < 0)
                        || c == 0 && contains(other.a) || d == 0 && contains(other.b)
                        || from == 0 && other.contains(a) || to == 0 && other.contains(b);
            }

            private double side(Geometry2.Point<Geometry2.Local> point) {
                return (b.x() - a.x()) * (point.y() - a.y()) - (b.y() - a.y()) * (point.x() - a.x());
            }

            private boolean contains(Geometry2.Point<Geometry2.Local> point) {
                return point.x() >= Math.min(a.x(), b.x()) && point.x() <= Math.max(a.x(), b.x())
                        && point.y() >= Math.min(a.y(), b.y()) && point.y() <= Math.max(a.y(), b.y());
            }
        }
    }
}
