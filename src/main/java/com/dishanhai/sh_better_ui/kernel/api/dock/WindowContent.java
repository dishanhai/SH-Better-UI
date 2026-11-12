// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock;

import com.dishanhai.sh_better_ui.kernel.api.NativeInput;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Rect;
import net.minecraft.network.chat.Component;

/**
 * Expanded content owned by one window. Constructed on first expansion and retained until close.
 * Callbacks run on the client thread. Content is clipped to the current presented bounds.
 */
@FunctionalInterface
public interface WindowContent extends AutoCloseable {
    void render(RenderService.Context draw, View view);

    /** Returning CAPTURE on pointer press reserves subsequent drag/release events for this content. */
    default Response input(NativeInput input, View view) { return Response.PASS; }
    default void captureCancelled() {}
    default void focusChanged(boolean focused) {}
    default Component narration() { return Component.empty(); }
    @Override default void close() {}

    enum Response { PASS, HANDLED, FOCUS, CAPTURE }
    record View(Rect bounds, float progress, boolean focused, int foregroundArgb) {}
}
