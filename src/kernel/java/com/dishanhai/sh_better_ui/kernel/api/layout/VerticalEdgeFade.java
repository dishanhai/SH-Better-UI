// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

/** Transparent viewport edge mask. Distances are viewport-local GUI units. */
public record VerticalEdgeFade(double top, double bottom) {
    public VerticalEdgeFade {
        if (!Double.isFinite(top) || !Double.isFinite(bottom) || top < 0 || bottom < 0)
            throw new IllegalArgumentException("Invalid fade distances");
    }

    public static VerticalEdgeFade of(double offset, double content, double viewport, double size, boolean enabled) {
        if (!Double.isFinite(offset) || !Double.isFinite(content) || !Double.isFinite(viewport)
                || !Double.isFinite(size) || content < 0 || viewport < 0 || size < 0)
            throw new IllegalArgumentException("Invalid viewport dimensions");
        double maximum = Math.max(0, content - viewport);
        double position = Math.max(0, Math.min(maximum, offset));
        double span = enabled ? size : 0;
        return new VerticalEdgeFade(Math.min(span, position), Math.min(span, maximum - position));
    }

    /** Sample this alpha on the content itself, rather than drawing an opaque overlay. */
    public double opacity(double y, double viewport) {
        if (!Double.isFinite(y) || !Double.isFinite(viewport) || y < 0 || y > viewport) return 0;
        return Math.min(top == 0 ? 1 : smooth(y / top), bottom == 0 ? 1 : smooth((viewport - y) / bottom));
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
}
