// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.geometry;

import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Matrix;

import java.util.Objects;
import java.util.Optional;

/** Experimental, platform-neutral two-dimensional spaces with top-left axes. */
public final class Geometry2 {
    public static final double MAX_COORDINATE = 1_000_000;

    private Geometry2() {}

    /** Type parameter for a coordinate space. Individual mounts are identified by their caller. */
    public sealed interface Space permits Local, Gui, Framebuffer {}

    /** Logical units within a mounted object. */
    public static final class Local implements Space { private Local() {} }

    /** Logical units within a screen. */
    public static final class Gui implements Space { private Gui() {} }

    /** Physical pixels relative to the top-left of the render target. */
    public static final class Framebuffer implements Space { private Framebuffer() {} }

    public record Point<S extends Space>(double x, double y) {
        public Point {
            x = coordinate(x);
            y = coordinate(y);
        }
    }

    /** Closed axis-aligned bounds. Zero width or height has no drawable area. */
    public record Bounds<S extends Space>(double left, double top, double right, double bottom) {
        public Bounds {
            left = coordinate(left);
            top = coordinate(top);
            right = coordinate(right);
            bottom = coordinate(bottom);
            if (left > right || top > bottom) throw new IllegalArgumentException("Inverted geometry bounds");
        }

        public boolean empty() { return left == right || top == bottom; }

        public boolean contains(Point<S> point) {
            return !empty() && point.x >= left && point.x <= right && point.y >= top && point.y <= bottom;
        }
    }

    /** Column-vector affine transform between spaces. Finite singular transforms represent collapsed geometry. */
    public record Transform<F extends Space, T extends Space>(Matrix matrix) {
        public Transform {
            Objects.requireNonNull(matrix, "matrix");
            finite(matrix.a());
            finite(matrix.b());
            finite(matrix.c());
            finite(matrix.d());
            finite(matrix.x());
            finite(matrix.y());
        }

        public Point<T> map(Point<F> point) {
            var result = matrix.point(point.x, point.y);
            return new Point<>(result.x(), result.y());
        }

        /** Applies the child transform first. */
        public <S extends Space> Transform<S, T> compose(Transform<S, F> child) {
            return new Transform<>(matrix.compose(child.matrix));
        }

        /** Empty when the inverse is singular to double precision or cannot be represented. */
        public Optional<Transform<T, F>> inverse() {
            double first = matrix.a() * matrix.d();
            double second = matrix.b() * matrix.c();
            if (!Double.isFinite(first) || !Double.isFinite(second) || differenceSign(first, second) == 0) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Transform<>(matrix.inverse()));
            } catch (IllegalStateException | IllegalArgumentException unusable) {
                return Optional.empty();
            }
        }
    }

    static int orientation(Point<?> a, Point<?> b, Point<?> c) {
        return differenceSign((b.x - a.x) * (c.y - a.y), (b.y - a.y) * (c.x - a.x));
    }

    private static int differenceSign(double first, double second) {
        double difference = first - second;
        double tolerance = 4 * Math.ulp(Math.max(Math.abs(first), Math.abs(second)));
        if (difference > tolerance) return 1;
        if (difference < -tolerance) return -1;
        return 0;
    }

    private static double coordinate(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > MAX_COORDINATE) {
            throw new IllegalArgumentException("Coordinate is outside the supported geometry range");
        }
        return value == 0 ? 0 : value;
    }

    private static void finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite geometry transform");
    }
}
