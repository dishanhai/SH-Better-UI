// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelSettings;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/** Applied client preferences and the native control panel. */
public final class SettingsApi {
    SettingsApi() {}

    public ControlPanelSettings current() { return ControlPanelSettings.current(); }

    public UiPreferences preferences() { return UiPreferences.configured(); }

    public ControlPanelScreen controlPanel(@Nullable Screen parent) { return new ControlPanelScreen(parent); }

    public void openControlPanel() { ControlPanelScreen.open(Minecraft.getInstance()); }
}
