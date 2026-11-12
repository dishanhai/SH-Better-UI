// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Rect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable snapshot of one solved layout in GUI-scaled coordinates. */
public record DockLayoutSnapshot(long version, Rect safeArea, Map<UiId, Entry> entries,
                                 Insets pushInsets, DockCollisionResult collisions) {
    public DockLayoutSnapshot {
        Objects.requireNonNull(safeArea, "safeArea");
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        Objects.requireNonNull(pushInsets, "pushInsets");
        Objects.requireNonNull(collisions, "collisions");
    }

    /** Area available to siblings that opt into PUSH. Use this rectangle for Kernel-managed layout. */
    public Rect contentArea() {
        double left = Math.min(safeArea.width(), pushInsets.left());
        double top = Math.min(safeArea.height(), pushInsets.top());
        return new Rect(safeArea.x() + left, safeArea.y() + top,
                Math.max(0, safeArea.width() - left - pushInsets.right()),
                Math.max(0, safeArea.height() - top - pushInsets.bottom()), safeArea.space());
    }

    /**
     * The bounds include the full visible panel at the sampled animation progress.
     * Compact bounds start at the dock edge, or the top-left for floating windows, and
     * are constrained to the visible bounds. Overflowed entries have empty bounds.
     */
    public record Entry(WindowPlacement placement, Rect bounds, Rect compactBounds, boolean overflowed, int zIndex) {
        public Entry {
            Objects.requireNonNull(placement, "placement");
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(compactBounds, "compactBounds");
        }

        public boolean visible() {
            return !bounds.empty();
        }
    }

    /** Insets for Kernel-managed siblings, measured from the safe-area edges in GUI pixels. */
    public record Insets(double top, double right, double bottom, double left) {
        public Insets {
            if (!valid(top) || !valid(right) || !valid(bottom) || !valid(left)) {
                throw new IllegalArgumentException("Insets must be finite and nonnegative");
            }
        }

        private static boolean valid(double value) {
            return Double.isFinite(value) && value >= 0;
        }
    }
}
