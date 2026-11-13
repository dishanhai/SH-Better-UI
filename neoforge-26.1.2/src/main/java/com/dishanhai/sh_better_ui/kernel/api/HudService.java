// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Consumer;

/** Scoped native HUD layers on the Unified UI Kernel. */
public interface HudService extends UIService {
    /**
     * Draws during the native HUD pass until the handle or the module scope closes.
     * Register and close on the UI thread. Callbacks run in registration order.
     */
    AutoCloseable mount(ModuleContext module, Consumer<Frame> draw);

    /**
     * Updates the stable palette for one panel in the current mounted callback. Call after painting
     * its background and before painting its text. The region uses the current graphics pose's local
     * coordinates. Use a distinct panel ID within each mount and keep it across panel rebuilds.
     * Supply the composited backdrop's darkest and lightest opaque colors for pending or unavailable samples.
     * The returned palette is for painting this callback. Its observation is available via
     * {@link TextReadabilityPalette#backgroundSample()}.
     *
     * @throws IllegalStateException if the frame is outside its own mounted callback
     * @throws SecurityException if the module does not own that callback
     */
    default TextReadabilityPalette textPalette(ModuleContext module, Frame frame, UiId panelId, Rect local,
                                                int darkestArgb, int lightestArgb) {
        throw new UnsupportedOperationException("Native HUD text readability is unavailable");
    }

    /** Graphics are valid only for the current HUD callback. Dimensions are GUI units. */
    record Frame(GuiGraphicsExtractor graphics, UiPreferences preferences, float partialTick) {
        public int width() { return graphics.guiWidth(); }
        public int height() { return graphics.guiHeight(); }
    }
}
