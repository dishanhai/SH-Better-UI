// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.blur.BackdropCapture;
import com.dishanhai.sh_better_ui.client.blur.BackgroundBlur;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Backdrop effects for the Minecraft GUI extractor. */
public final class BlurApi {
    BlurApi() {}

    /** Blurs the extracted background using Minecraft's menu blur setting. */
    public boolean background(GuiGraphicsExtractor graphics) { return BackgroundBlur.request(graphics); }

    public boolean background(GuiGraphicsExtractor graphics, UiPreferences preferences) {
        return BackgroundBlur.request(graphics, preferences);
    }

    /**
     * Call once after the background and before extracting any glass controls.
     * A frame shares one captured backdrop. If a menu blur is also desired, request it first.
     */
    public boolean prepareBackdrop(GuiGraphicsExtractor graphics) {
        return ShBetterUiApi.settings().current().glassEnabled() && UiPreferences.configured().useBackdropBlur()
                && BackdropCapture.request(graphics) != null;
    }
}
