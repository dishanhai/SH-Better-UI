// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

/** Placement and interaction mode. Detached windows retain their last dock edge for restoration. */
public enum DockMode {
    EDGE_DOCKED,
    AUTO_HIDE,
    FLOATING,
    PINNED,
    DETACHED;

    public boolean onEdge() {
        return this == EDGE_DOCKED || this == AUTO_HIDE || this == PINNED;
    }
}
