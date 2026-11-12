// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

import java.util.Objects;

/** Result of a placement request. The placement is the effective preference after the request. */
public record DockResult(boolean accepted, WindowPlacement placement) {
    public DockResult {
        Objects.requireNonNull(placement, "placement");
    }
}
