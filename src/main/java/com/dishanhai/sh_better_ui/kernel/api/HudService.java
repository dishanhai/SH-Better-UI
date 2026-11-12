// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/** Scoped native HUD layers on the Unified UI Kernel. */
public interface HudService extends UIService {
    /**
     * Draws during the native HUD pass until the handle or the module scope closes.
     * Register and close on the UI thread. Callbacks run in registration order.
     */
    AutoCloseable mount(ModuleContext module, Consumer<Frame> draw);

    /** Graphics are valid only for the current HUD callback. Dimensions are GUI units. */
    record Frame(GuiGraphics graphics, UiPreferences preferences, float partialTick) {
        public int width() { return graphics.guiWidth(); }
        public int height() { return graphics.guiHeight(); }
    }
}
