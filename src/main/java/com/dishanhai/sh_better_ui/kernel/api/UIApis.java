// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.dock.CollapseService;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockService;
import com.dishanhai.sh_better_ui.kernel.api.dock.WindowService;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassService;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceService;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceSettings;
import com.dishanhai.sh_better_ui.kernel.api.live.NotificationService;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialCatalog;
import com.dishanhai.sh_better_ui.kernel.api.system.ComputerStatusService;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService;
import com.dishanhai.sh_better_ui.spatial.SpatialUI;

import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Keys for built-in UI services available through {@link UnifiedUI}. */
public final class UIApis {
    private static final UiId THEME_ID = UiId.parse("sh_better_ui:theme");
    public static final ServiceKey<ThemeService> THEME = new ServiceKey<>(THEME_ID, ThemeService.class, 1);
    public static final CapabilityKey DYNAMIC_THEME = new CapabilityKey(THEME_ID);
    public static final ServiceKey<AnimationService> ANIMATION = key("animation", AnimationService.class);
    public static final CapabilityKey SCOPED_ANIMATION = new CapabilityKey(ANIMATION.id());
    public static final ServiceKey<LayoutService> LAYOUT = key("layout", LayoutService.class);
    public static final CapabilityKey INCREMENTAL_LAYOUT = new CapabilityKey(LAYOUT.id());
    public static final ServiceKey<InputService> INPUT = key("input", InputService.class);
    public static final CapabilityKey INPUT_ROUTING = new CapabilityKey(INPUT.id());
    public static final ServiceKey<CollapseService> COLLAPSE = key("collapse", CollapseService.class);
    public static final ServiceKey<WindowService> WINDOWS = key("windows", WindowService.class);
    public static final ServiceKey<DockService> DOCK = key("dock", DockService.class);
    public static final ServiceKey<FocusService> FOCUS = key("focus", FocusService.class);
    public static final CapabilityKey SCREEN_FOCUS = new CapabilityKey(FOCUS.id());
    public static final ServiceKey<CollisionService> COLLISION = key("collision", CollisionService.class);
    public static final CapabilityKey PRESENTED_HIT_TEST = new CapabilityKey(COLLISION.id());
    public static final ServiceKey<RenderService> RENDER = key("render", RenderService.class);
    public static final ServiceKey<TextReadabilityService> TEXT_READABILITY =
            key("text_readability", TextReadabilityService.class);
    public static final CapabilityKey TEXT_READABILITY_POLICY = new CapabilityKey(TEXT_READABILITY.id());
    public static final ServiceKey<HudService> HUD = key("hud", HudService.class);
    public static final CapabilityKey NATIVE_HUD = new CapabilityKey(HUD.id());
    public static final ServiceKey<GlassService> GLASS = GlassService.KEY;
    public static final ServiceKey<MaterialCatalog> MATERIAL_CATALOG = MaterialCatalog.KEY;
    public static final CapabilityKey MATERIAL_DISCOVERY = new CapabilityKey(MATERIAL_CATALOG.id());
    public static final ServiceKey<com.dishanhai.sh_better_ui.kernel.api.intelligence.UICorrectnessEngine> CORRECTNESS =
            com.dishanhai.sh_better_ui.kernel.api.intelligence.UICorrectnessEngine.KEY;
    public static final CapabilityKey UI_CORRECTNESS = new CapabilityKey(CORRECTNESS.id());
    public static final ServiceKey<SpatialUI> SPATIAL = SpatialUI.KEY;
    public static final CapabilityKey SPATIAL_SCENE = new CapabilityKey(SPATIAL.id());
    public static final ServiceKey<ResourcesService> RESOURCES = key("resources", ResourcesService.class);
    public static final CapabilityKey RELOAD_GENERATIONS = new CapabilityKey(RESOURCES.id());
    public static final ServiceKey<AccessibilityService> ACCESSIBILITY = key("accessibility", AccessibilityService.class);
    public static final CapabilityKey MINECRAFT_NARRATION = new CapabilityKey(new UiId("sh_better_ui", "minecraft_narration"));
    public static final CapabilityKey SYSTEM_SCREEN_READER = new CapabilityKey(new UiId("sh_better_ui", "system_screen_reader"));
    public static final ServiceKey<UIPlatform> PLATFORM = UIPlatform.KEY;
    public static final ServiceKey<ComputerStatusService> COMPUTER_STATUS = ComputerStatusService.KEY;
    public static final CapabilityKey COMPUTER_STATUS_READ = ComputerStatusService.CAPABILITY;
    public static final ServiceKey<AppearanceService> APPEARANCE = key("appearance", AppearanceService.class);
    public static final ServiceKey<ScreenService> SCREENS = key("screens", ScreenService.class);
    public static final CapabilityKey NATIVE_SCREEN = new CapabilityKey(SCREENS.id());
    public static final CapabilityKey NATIVE_RENDER = new CapabilityKey(RENDER.id());
    public static final ServiceKey<ActivityService> ACTIVITIES = ActivityService.KEY;
    public static final ServiceKey<NotificationService> NOTIFICATIONS = NotificationService.KEY;
    public static final ServiceKey<LiveSurfaceService> LIVE_SURFACES = LiveSurfaceService.KEY;

    /** Authoritative client UI preferences at the persisted User Global layer. */
    public static final ConfigKey<ClientUiSettings> CLIENT_SETTINGS = new ConfigKey<>(
            UiId.parse("sh_better_ui:client_settings"), ClientUiSettings.class, ClientUiSettings.DEFAULT,
            Set.of(ConfigLayer.USER_GLOBAL), ConfigKey.Mutability.RUNTIME, ConfigKey.Persistence.USER_GLOBAL, 1,
            "SH-Better-UI client configuration", ClientUiSettings::validForGlobalConfig, UnaryOperator.identity(), Map.of());
    /** Validated local live surface configuration, mirrored after startup and resource reload. */
    public static final ConfigKey<LiveSurfaceSettings> LIVE_SURFACE_SETTINGS = new ConfigKey<>(
            UiId.parse("sh_better_ui:live_surface_settings"), LiveSurfaceSettings.class, LiveSurfaceSettings.DEFAULT,
            Set.of(ConfigLayer.USER_GLOBAL), ConfigKey.Mutability.RUNTIME, ConfigKey.Persistence.USER_GLOBAL, 1,
            "Adaptive live surface configuration", ignored -> true, UnaryOperator.identity(), Map.of());

    private UIApis() {}

    private static <T extends UIService> ServiceKey<T> key(String path, Class<T> type) {
        return new ServiceKey<>(new UiId("sh_better_ui", path), type, 1);
    }
}
