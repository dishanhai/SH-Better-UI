// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.input;

import java.util.Objects;

/** Retains an existing focus through a secondary pointer gesture and its final native release. */
public final class GestureFocusRetention {
    private Object owner;
    private Object target;

    /** The host arms this only for a target that already has focus. This does not consume pointer input. */
    public void begin(Object owner, Object target) {
        this.owner = Objects.requireNonNull(owner);
        this.target = Objects.requireNonNull(target);
    }

    /**
     * Called after native input and before rendering. The host validates visibility, window focus and cancellation.
     * A released gesture restores once more, then relinquishes focus retention.
     */
    public boolean restore(Object owner, Object target, boolean usable, boolean held) {
        if (this.owner == null || this.owner != owner || this.target != target || !usable) {
            clear();
            return false;
        }
        if (!held) clear();
        return true;
    }

    public void clear() { owner = target = null; }
}
