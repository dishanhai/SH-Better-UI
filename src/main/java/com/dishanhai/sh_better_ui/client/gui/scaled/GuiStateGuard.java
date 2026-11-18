// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.kernel.internal.GuiRenderBindings;
import java.util.function.Supplier;

/** Restores the GUI pipeline and native framebuffer/scissor after scoped rendering. */
public final class GuiStateGuard {
    private GuiStateGuard() {}

    public static void draw(Runnable draw) {
        call(() -> { draw.run(); return null; });
    }

    public static <T> T call(Supplier<T> draw) {
        var pipeline = WaveRenderState.capture();
        try (var bindings = new GuiRenderBindings()) {
            return draw.get();
        } finally {
            pipeline.restore();
        }
    }
}
