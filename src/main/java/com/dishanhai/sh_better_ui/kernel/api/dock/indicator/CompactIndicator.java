// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock.indicator;

/**
 * Small, window-owned visual surface. Implementations are used on the client UI thread and
 * should read ready-to-use snapshots without blocking. Create a fresh instance per window.
 */
public interface CompactIndicator {
    /** Returns a preferred size for the supplied constraints. Keep measurement independent of sampled data. */
    IndicatorSize measure(IndicatorConstraints constraints);

    /** Refreshes cached display data at the configured cadence while the indicator is visible. */
    default void update(IndicatorContext context) { }

    /** Draws using local GUI coordinates and only the operations provided by this context. */
    void render(IndicatorRenderContext context);

    /** Receives changes to the current transition progress while drawn, independently of data refresh. */
    default void onExpandProgress(float progress) { }

    /** Declares the minimum nanoseconds between samples at attachment. Zero permits one sample each frame. */
    default long minimumUpdateIntervalNanos() { return 0; }
}
