// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

/** How a docked window contributes to the usable area of Kernel-managed layouts. */
public enum ExpansionLayoutMode {
    /** The window occupies its own layer above other content. */
    OVERLAY,
    /** The occupied edge thickness is published as a Kernel layout inset. */
    PUSH
}
