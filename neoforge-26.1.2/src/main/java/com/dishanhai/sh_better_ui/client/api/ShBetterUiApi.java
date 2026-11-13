// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperGalleryScreen;

/** Entry point for the native GUI features available on NeoForge 26. */
public final class ShBetterUiApi {
    private static final ControlsApi CONTROLS = new ControlsApi();
    private static final SettingsApi SETTINGS = new SettingsApi();
    private static final BlurApi BLUR = new BlurApi();
    private static final UiClientApi CLIENT = new UiClientApi();

    private ShBetterUiApi() {}

    public static ControlsApi controls() {
        return CONTROLS;
    }

    public static SettingsApi settings() { return SETTINGS; }

    public static BlurApi blur() { return BLUR; }

    public static UiClientApi client() { return CLIENT; }

    public static PreviewRegistry developerPreviews() { return Previews.REGISTRY; }

    private static final class Previews {
        private static final PreviewRegistry REGISTRY = create();

        private static PreviewRegistry create() {
            var registry = new PreviewRegistry();
            DeveloperGalleryScreen.registerBuiltins(registry);
            return registry;
        }
    }
}
