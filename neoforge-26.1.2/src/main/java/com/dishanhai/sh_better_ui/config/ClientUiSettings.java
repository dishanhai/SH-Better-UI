// Author: dishanhai
package com.dishanhai.sh_better_ui.config;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelSettings;
/** Shared music views read the native 26.x persisted settings, not a parallel configuration. */
public final class ClientUiSettings {
    private ClientUiSettings() {}
    public static ControlPanelSettings current() { return ControlPanelSettings.current(); }
}
