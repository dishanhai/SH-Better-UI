// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

/** Physical GUI edges. Left and right do not depend on the language direction. */
public enum DockEdge {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM;

    /** Whether windows on this edge are stacked vertically. */
    public boolean vertical() {
        return this == LEFT || this == RIGHT;
    }
}
