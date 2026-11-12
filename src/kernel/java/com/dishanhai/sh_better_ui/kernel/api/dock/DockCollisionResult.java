// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Rect;

import java.util.List;
import java.util.Objects;

/** Diagnostics for a solved layout, including deliberate overlays and skipped compact windows. */
public record DockCollisionResult(List<Overlap> overlaps, List<UiId> overflowed) {
    public DockCollisionResult {
        overlaps = List.copyOf(overlaps);
        overflowed = List.copyOf(overflowed);
    }

    /** Intersection of two visible bounds in GUI-scaled coordinates. */
    public record Overlap(UiId first, UiId second, Rect bounds) {
        public Overlap {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            Objects.requireNonNull(bounds, "bounds");
        }
    }
}
