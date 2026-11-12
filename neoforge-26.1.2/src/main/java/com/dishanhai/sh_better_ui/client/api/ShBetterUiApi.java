// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

/** Entry point for the native GUI features available on NeoForge 26. */
public final class ShBetterUiApi {
    private static final ControlsApi CONTROLS = new ControlsApi();
    private static final SettingsApi SETTINGS = new SettingsApi();
    private static final BlurApi BLUR = new BlurApi();

    private ShBetterUiApi() {}

    public static ControlsApi controls() {
        return CONTROLS;
    }

    public static SettingsApi settings() { return SETTINGS; }

    public static BlurApi blur() { return BLUR; }
}
