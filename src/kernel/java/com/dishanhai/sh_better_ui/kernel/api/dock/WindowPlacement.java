// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.Objects;

/**
 * Immutable user placement preferences. The edge is retained while floating or detached.
 * The position is the preferred floating top-left and the dimensions are the preferred
 * expanded size in GUI-scaled coordinates. The solver clamps its output to the current safe area.
 * A null group places the window independently.
 */
public record WindowPlacement(DockEdge edge, DockMode mode, double x, double y,
                              double width, double height, boolean pinned, UiId group) {
    public WindowPlacement {
        Objects.requireNonNull(edge, "edge");
        Objects.requireNonNull(mode, "mode");
        if (!Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(width) || width <= 0 || !Double.isFinite(height) || height <= 0) {
            throw new IllegalArgumentException("Window placement requires finite coordinates and positive dimensions");
        }
    }

    public static WindowPlacement docked(DockEdge edge, DockMode mode, double width, double height) {
        if (!Objects.requireNonNull(mode, "mode").onEdge()) {
            throw new IllegalArgumentException("The mode must place the window on an edge");
        }
        return new WindowPlacement(edge, mode, 0, 0, width, height, mode == DockMode.PINNED, null);
    }

    public WindowPlacement withRequest(DockRequest request) {
        DockEdge nextEdge = request.edge() == null ? edge : request.edge();
        return new WindowPlacement(nextEdge, request.mode(), request.mode().onEdge() ? x : request.x(),
                request.mode().onEdge() ? y : request.y(), width, height,
                pinned || request.mode() == DockMode.PINNED, group);
    }

    public WindowPlacement withSize(double width, double height) {
        return new WindowPlacement(edge, mode, x, y, width, height, pinned, group);
    }

    public WindowPlacement withPinned(boolean pinned) {
        return new WindowPlacement(edge, mode, x, y, width, height, pinned, group);
    }

    public WindowPlacement withGroup(UiId group) {
        return new WindowPlacement(edge, mode, x, y, width, height, pinned, group);
    }
}
