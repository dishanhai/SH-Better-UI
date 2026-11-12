// Author: dishanhai
package com.dishanhai.sh_better_ui.client.blur;

import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelSettings;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Schedules the native menu blur between already extracted background and upcoming foreground. */
public final class BackgroundBlur {
    private BackgroundBlur() {}

    public static boolean isEnabled() {
        return ControlPanelSettings.current().backgroundBlur()
                && !UiPreferences.configured().highContrast() && menuBlurEnabled();
    }

    public static boolean isEnabled(UiPreferences preferences) {
        return preferences.useBackdropBlur() && menuBlurEnabled();
    }

    private static boolean menuBlurEnabled() {
        var minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.options != null && minecraft.options.getMenuBackgroundBlurriness() >= 1;
    }

    public static boolean request(GuiGraphicsExtractor graphics) {
        return isEnabled() && schedule(graphics);
    }

    /**
     * Uses the menu blur strength set by Minecraft. Each frame can use one shared boundary.
     * Extract the background first, then request blur before submitting any clear foreground.
     */
    public static boolean request(GuiGraphicsExtractor graphics, UiPreferences preferences) {
        return isEnabled(preferences) && schedule(graphics);
    }

    private static boolean schedule(GuiGraphicsExtractor graphics) {
        var frame = BlurFrame.from(graphics);
        if (frame.shbui$hasBoundary()) return !frame.shbui$captureOnly();
        graphics.nextStratum();
        graphics.blurBeforeThisStratum();
        return true;
    }
}
