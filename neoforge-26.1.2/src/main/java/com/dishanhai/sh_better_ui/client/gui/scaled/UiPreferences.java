// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelSettings;
import com.dishanhai.sh_better_ui.config.ControlFeedbackSettings;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import net.minecraft.client.Minecraft;

import java.util.Objects;

/** Display and interaction preferences for the native controls. */
public record UiPreferences(boolean highContrast, boolean reducedMotion, boolean backgroundBlur,
                            ControlFeedbackSettings feedback) {
    public static final UiPreferences DEFAULT = new UiPreferences(false, false, true);

    public UiPreferences {
        Objects.requireNonNull(feedback);
    }

    public UiPreferences(boolean highContrast, boolean reducedMotion, boolean backgroundBlur) {
        this(highContrast, reducedMotion, backgroundBlur, ControlFeedbackSettings.DEFAULT);
    }

    public static UiPreferences configured() {
        var minecraft = Minecraft.getInstance();
        boolean contrast = minecraft != null && minecraft.options != null && minecraft.options.highContrast().get();
        return ControlPanelSettings.current().preferences(contrast);
    }

    public boolean useBackdropBlur() {
        return backgroundBlur && !highContrast;
    }

    public UiPreferences withTheme(ThemeScope scope) {
        if (scope == null || scope.isClosed()) return this;
        var dynamic = scope.preferences();
        return new UiPreferences(highContrast || dynamic.highContrast(), reducedMotion || dynamic.reducedMotion(),
                backgroundBlur && !dynamic.lowPerformance(), feedback);
    }
}
