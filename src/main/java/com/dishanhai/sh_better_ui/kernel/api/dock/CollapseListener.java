// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

/** Receives distinct collapse state changes on the kernel UI thread. */
@FunctionalInterface
public interface CollapseListener {
    void onStateChanged(CollapseState state);
}
