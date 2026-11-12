// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

import java.util.Objects;

/**
 * Immutable configuration for a collapse controller.
 *
 * @param direction layout axis for consumers of the progress
 * @param curve motion for automatic transitions
 * @param expandMillis full timed expansion duration in milliseconds
 * @param collapseMillis full timed collapse duration in milliseconds
 * @param initiallyExpanded initial endpoint
 * @param controlled whether the caller supplies progress independently of the target
 */
public record CollapseSpec(Direction direction, Curve curve, long expandMillis, long collapseMillis,
                           boolean initiallyExpanded, boolean controlled) {
    public CollapseSpec {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(curve, "curve");
        if (expandMillis < 0 || collapseMillis < 0) {
            throw new IllegalArgumentException("Collapse durations must be nonnegative");
        }
    }

    /** Starts collapsed and expands horizontally over 240 ms or collapses over 200 ms. */
    public CollapseSpec() {
        this(Direction.HORIZONTAL, Curve.EASE, 240, 200, false, false);
    }

    public enum Direction {
        HORIZONTAL, VERTICAL
    }

    /**
     * Spring motion settles according to the kernel spring preset. EASE uses smoothstep and
     * LINEAR uses constant speed. A timed transition interrupted by a reversal carries its
     * current velocity into the spring. A zero duration snaps directly to that endpoint.
     */
    public enum Curve {
        SPRING, EASE, LINEAR
    }
}
