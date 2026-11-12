// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

import com.dishanhai.sh_better_ui.kernel.api.UIService;

/** Client-thread docking and read-only geometry for the last presented surface. */
public interface DockService extends UIService {
    DockResult dock(MiniWindowHandle window, DockRequest request);
    DockLayoutSnapshot layout();
    default DockCollisionResult inspectCollisions() { return layout().collisions(); }
    DockDiagnostics diagnostics();

    /** Duration of the latest solve, paint and routed event in nanoseconds, plus cumulative solves. */
    record DockDiagnostics(long layoutNanos, long paintNanos, long inputNanos, long solves, int visibleWindows) {}
}
