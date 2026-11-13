// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api.preview;

import com.dishanhai.sh_better_ui.client.api.UiFrame;

import java.util.function.Supplier;

/** State owned by one preview session; layout may be called repeatedly on the same instance. */
@FunctionalInterface
public interface PreviewScene extends AutoCloseable {
    void layout(PreviewContext host);
    default void render(UiFrame frame) {}
    default void suspend() {}
    default Supplier<? extends PreviewScene> saveForRemount() { return null; }
    @Override default void close() { suspend(); }
}
