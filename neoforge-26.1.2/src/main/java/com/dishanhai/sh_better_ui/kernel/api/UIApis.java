// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.kernel.api.material.MaterialCatalog;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService;

/** Native service keys supported by the NeoForge 26 client port. */
public final class UIApis {
    public static final ServiceKey<UIPlatform> PLATFORM = UIPlatform.KEY;
    public static final ServiceKey<MaterialCatalog> MATERIAL_CATALOG = MaterialCatalog.KEY;
    public static final CapabilityKey MATERIAL_DISCOVERY = new CapabilityKey(MATERIAL_CATALOG.id());
    public static final ServiceKey<ScreenService> SCREENS = key("screens", ScreenService.class);
    public static final CapabilityKey NATIVE_SCREEN = new CapabilityKey(SCREENS.id());
    public static final ServiceKey<HudService> HUD = key("hud", HudService.class);
    public static final CapabilityKey NATIVE_HUD = new CapabilityKey(HUD.id());
    public static final ServiceKey<TextReadabilityService> TEXT_READABILITY =
            key("text_readability", TextReadabilityService.class);
    public static final CapabilityKey TEXT_READABILITY_POLICY = new CapabilityKey(TEXT_READABILITY.id());

    private UIApis() {}

    private static <T extends UIService> ServiceKey<T> key(String path, Class<T> type) {
        return new ServiceKey<>(new UiId("sh_better_ui", path), type, 1);
    }
}
