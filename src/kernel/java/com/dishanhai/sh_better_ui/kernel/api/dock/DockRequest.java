// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

import java.util.Objects;

/**
 * A placement change in GUI-scaled coordinates. Off-edge requests may omit the edge to
 * preserve the last dock edge. The coordinates are ignored for on-edge placement.
 */
public record DockRequest(DockEdge edge, DockMode mode, double x, double y) {
    public DockRequest {
        Objects.requireNonNull(mode, "mode");
        if (mode.onEdge()) Objects.requireNonNull(edge, "edge");
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Dock coordinates must be finite");
        }
    }

    public static DockRequest edge(DockEdge edge, DockMode mode) {
        if (!Objects.requireNonNull(mode, "mode").onEdge()) {
            throw new IllegalArgumentException("The mode must place the window on an edge");
        }
        return new DockRequest(edge, mode, 0, 0);
    }

    public static DockRequest floating(double x, double y) {
        return new DockRequest(null, DockMode.FLOATING, x, y);
    }

    public static DockRequest detached(double x, double y) {
        return new DockRequest(null, DockMode.DETACHED, x, y);
    }
}
