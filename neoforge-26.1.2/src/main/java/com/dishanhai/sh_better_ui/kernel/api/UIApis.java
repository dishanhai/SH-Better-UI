// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.kernel.api.material.MaterialCatalog;
import com.dishanhai.sh_better_ui.kernel.api.system.ComputerStatusService;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService;

/** Native service keys supported by the NeoForge 26 client port. */
public final class UIApis {
    public static final ServiceKey<com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicService> ONLINE_MUSIC =
            com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicService.KEY;
    public static final ServiceKey<com.dishanhai.sh_better_ui.kernel.api.dock.WindowService> WINDOWS =
            key("windows", com.dishanhai.sh_better_ui.kernel.api.dock.WindowService.class);
    public static final ServiceKey<com.dishanhai.sh_better_ui.kernel.api.dock.DockService> DOCK =
            key("dock", com.dishanhai.sh_better_ui.kernel.api.dock.DockService.class);
    public static final ServiceKey<UIPlatform> PLATFORM = UIPlatform.KEY;
    public static final ServiceKey<MaterialCatalog> MATERIAL_CATALOG = MaterialCatalog.KEY;
    public static final CapabilityKey MATERIAL_DISCOVERY = new CapabilityKey(MATERIAL_CATALOG.id());
    public static final ServiceKey<ScreenService> SCREENS = key("screens", ScreenService.class);
    public static final CapabilityKey NATIVE_SCREEN = new CapabilityKey(SCREENS.id());
    public static final ServiceKey<HudService> HUD = key("hud", HudService.class);
    public static final CapabilityKey NATIVE_HUD = new CapabilityKey(HUD.id());
    public static final ServiceKey<ComputerStatusService> COMPUTER_STATUS = ComputerStatusService.KEY;
    public static final CapabilityKey COMPUTER_STATUS_READ = ComputerStatusService.CAPABILITY;
    public static final ServiceKey<AccessibilityService> ACCESSIBILITY = key("accessibility",AccessibilityService.class);
    public static final CapabilityKey MINECRAFT_NARRATION = new CapabilityKey(new UiId("sh_better_ui","minecraft_narration"));
    /** Capability identifier only; no native system screen reader provider is registered. */
    public static final CapabilityKey SYSTEM_SCREEN_READER = new CapabilityKey(new UiId("sh_better_ui","system_screen_reader"));
    public static final ServiceKey<AnimationService> ANIMATION = key("animation", AnimationService.class);
    public static final CapabilityKey SCOPED_ANIMATION = new CapabilityKey(ANIMATION.id());
    public static final ServiceKey<com.dishanhai.sh_better_ui.kernel.api.dock.CollapseService> COLLAPSE =
            key("collapse", com.dishanhai.sh_better_ui.kernel.api.dock.CollapseService.class);
    public static final ServiceKey<TextReadabilityService> TEXT_READABILITY =
            key("text_readability", TextReadabilityService.class);
    public static final CapabilityKey TEXT_READABILITY_POLICY = new CapabilityKey(TEXT_READABILITY.id());

    public static final ServiceKey<com.dishanhai.sh_better_ui.kernel.api.live.ActivityService> ACTIVITIES = com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.KEY;
    public static final ServiceKey<com.dishanhai.sh_better_ui.kernel.api.live.NotificationService> NOTIFICATIONS = com.dishanhai.sh_better_ui.kernel.api.live.NotificationService.KEY;
    public static final ServiceKey<com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceService> LIVE_SURFACES = com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceService.KEY;

    private UIApis() {}

    private static <T extends UIService> ServiceKey<T> key(String path, Class<T> type) {
        return new ServiceKey<>(new UiId("sh_better_ui", path), type, 1);
    }
}
