// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import java.util.Optional;

/** Client-thread handle. Mutations require an open lifetime. Close is idempotent. */
public interface MiniWindowHandle extends AutoCloseable {
    UiId id();
    boolean isOpen();
    void expand();
    void collapse();
    void toggle();
    void pin(boolean pinned);
    void dock(DockEdge edge);
    void floatAt(double x, double y);
    void detach(double x, double y);
    void resize(double width, double height);
    /** Removes the saved placement and returns to the specification defaults. */
    void resetPlacement();
    Optional<WindowSnapshot> snapshot();
    @Override void close();
}
