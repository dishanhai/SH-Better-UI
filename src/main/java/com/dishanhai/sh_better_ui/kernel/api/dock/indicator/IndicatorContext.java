// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock.indicator;

/** Immutable frame data from the owning window, independent of the native rendering pass. */
public record IndicatorContext(long nowNanos, float expandProgress, boolean reducedMotion, boolean lowPower) {
    /** The timestamp uses the same monotonic clock as the owning window's transition. */
    public IndicatorContext {
        expandProgress = Float.isFinite(expandProgress) ? Math.max(0, Math.min(1, expandProgress)) : 0;
    }
}
