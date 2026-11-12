// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

import com.dishanhai.sh_better_ui.kernel.api.Subscription;

/** One collapse controller. Access all methods on the kernel UI thread. */
public interface CollapseHandle extends AutoCloseable {
    /** Requests the expanded endpoint. */
    void expand();

    /** Requests the collapsed endpoint. */
    void collapse();

    /** Reverses the requested endpoint, including during a transition. */
    void toggle();

    /** Requests an endpoint. In controlled mode, the caller supplies progress separately. */
    void setExpanded(boolean expanded);

    /**
     * Sets progress for a controlled handle and clamps finite values to [0, 1].
     * The controller retains its current requested endpoint.
     *
     * @throws IllegalStateException if the handle uses automatic motion
     * @throws IllegalArgumentException if the progress is non-finite
     */
    void setProgress(float progress);

    CollapseState state();

    /** Current normalized progress in [0, 1]. */
    float progress();

    /** The requested endpoint, which can differ from the currently visible endpoint. */
    boolean targetExpanded();

    /** While locked, target and progress commands are ignored. Active motion can settle. */
    void setLocked(boolean locked);

    /** Subscribes to subsequent changes until the subscription or controller closes. */
    Subscription onStateChanged(CollapseListener listener);

    /** Releases animation and listeners. Repeated calls have no effect. */
    @Override
    void close();
}
