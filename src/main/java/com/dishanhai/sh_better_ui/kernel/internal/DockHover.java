// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

/** Edge-triggered delay on the kernel clock. An explicit command holds until the next hover transition. */
final class DockHover {
    private boolean inside, explicit;
    private long since;

    boolean inside() { return inside; }

    void command() { explicit = true; }

    boolean update(long now, boolean hovered, boolean protectedInteraction, boolean target,
                   boolean canOpen, long openNanos, long closeNanos) {
        if (inside != hovered) {
            inside = hovered;
            since = now;
            explicit = false;
        }
        if (protectedInteraction) {
            since = now;
            return target;
        }
        if (explicit || inside == target) return target;
        if (inside && !canOpen) return target;
        return now - since >= (inside ? openNanos : closeNanos) ? inside : target;
    }

    void suspend(long now) {
        inside = false;
        since = now;
        explicit = false;
    }
}
