<!-- Author: dishanhai -->
# SH-Better-UI API Reference

[简体中文](API_REFERENCE.md) · **English**

SH-Better-UI provides native Minecraft controls and rendering helpers through `ShBetterUiApi`, and scoped services through the Unified UI Kernel. Use the widget and material APIs when a screen owns its own state. Use the kernel when a mod needs to discover a provider, coordinate work across services, or cancel work when a screen closes.

Use `UIApis.COLLAPSE`, `UIApis.WINDOWS` and `UIApis.DOCK` for scoped fold animation, mini-windows and dock layout. The `/shbetterdock` client command opens the interactive monitor. [Adaptive Dock API and verification](ADAPTIVE_DOCK.md)

## Dependencies and mappings

The paths below use the version declared in each platform's `gradle.properties`. The current source tree builds version `1.0.6`. Match your source imports to the JAR you actually compile against.

| Target | Java | Compile against | Install in `mods/` |
| --- | --- | --- | --- |
| Forge 1.20.1, Forge 47.4.16+ | 17 for the game and production classes. The repository's general tests use JDK 21. | `build/libs/sh_better_ui-<version>-dev.jar` | `build/libs/sh_better_ui-<version>.jar` and KotlinForForge `[4.12.0,5.0.0)` |
| NeoForge 1.21.1, NeoForge 21.1.251+ | 21 for the game and platform classes | `neoforge/build/libs/sh_better_ui-neoforge-1.21.1-<version>.jar` | The same NeoForge JAR and KotlinForForge `[5.10.0,6)` |

Declare `sh_better_ui` in your loader metadata. Compile against the library as an external dependency and install its runtime JAR separately. JEI, LDLib and KubeJS are optional. Choose the loader-specific adapter when using an integration. [Forge metadata](../src/main/resources/META-INF/mods.toml) · [NeoForge metadata](../neoforge/src/main/resources/META-INF/neoforge.mods.toml) · [Platform details in Chinese](platform-compatibility.md)

## Native screens and reusable controls

Register widgets in `Screen.init()` or a `UiScreenBuilder.onInit` callback. Draw the background before the widgets and overlays afterward. Use the same GUI bounds for rendering and hit testing. Pass localized `Component` values for labels, tooltips and narration.

| API | Use |
| --- | --- |
| `ShBetterUiApi.screen(...)` | Create a `UiScreen` with native controls, a content viewport, back navigation and focus restoration. Provide `returnFocus(...)` when the parent rebuilds the original control. |
| `controls()`, `interactions()` | Create buttons, sliders, tabs, steppers, overlays, dialogs, sheets, pull-to-refresh and swipe confirmation. Keep gesture state on the screen or control that owns it. |
| `links()`, `markdown()` | Open external web links and parse Markdown into native text components. |
| `search()`, `ingredients()` | Build a `SearchIndex` and `SearchField` for text, IDs and aliases. Query `JeiIntegration.isAvailable()` before calling JEI operations. `automaticCatalog()` falls back to the vanilla registry when JEI is unavailable. |
| `settings()`, `themes()`, `materials()` | Read client preferences, use registered themes and choose glass, Slate or liquid-glass surfaces. Read a theme by stable ID without changing the global selection. |
| `backgrounds()` | Register a `BackgroundSpec` under your namespace. Keep one background layer per screen and close it when the screen is removed. Close the registration when your provider unloads. |
| `graphics()`, `layout()`, `blur()` | Draw icons and text, align controls, and submit blur or reveal effects. Immediate GUI effects use GUI units. Low-level `GaussBlur` pixel operations use framebuffer coordinates. |
| `motion()` | Build timelines, springs, reversible transitions and animated backgrounds. Use the supplied frame time to keep transitions consistent within a frame. |
| `audio()`, `lyrics()` | Analyze PCM, opt into Windows output loopback, or render synchronized lyrics using a supported feed. |
| `client()`, `extensions()` | Dispatch short callbacks to the client thread or register typed, per-context factories. Track disposable resources with `UiApiContext.manage(...)`. |
| `platform()` | Access version-specific fluid, tooltip and LDLib adapters. |

[Screen interactions](SCREEN_INTERACTIONS.md) · [Visual alignment](VISUAL_ALIGNMENT.md) · [Dynamic themes](DYNAMIC_THEMES.md) · [Music rhythm](music-rhythm.md) · [Lyrics](lyrics.md) · [KubeJS](KUBEJS.md)

## External links and Markdown

Call `ShBetterUiApi.links().open(owner, url)` from a user action on the active client screen. It accepts HTTP(S) URLs and respects Minecraft's Web Links and link confirmation options. The return value reports whether the request was accepted. Use `open(owner, url, () -> linkButton)` to restore focus when the parent rebuilds its widgets.

`ShBetterUiApi.markdown().parse(source)` returns a native `MutableComponent`. It supports hash headings, lists, quotes, bold, italic, strikethrough, inline and fenced code, inline links and angle-bracket HTTP(S) links. Headings render in bold and images render as alt text. Input is limited to 65,536 UTF-16 code units. Nested inline formatting works with alternating delimiters, such as `**bold _italic_**`.

For custom text rendering, use the font layout to resolve the `Style` at the clicked position and pass it to `Screen.handleComponentClicked(style)`. A button can call `links().open(owner, url)` directly.

## Get the kernel and look up services

`UnifiedUI.isAvailable()` tells you whether the client has published its engine. Before startup, after shutdown and on a dedicated server, `UnifiedUI.get()` throws `IllegalStateException`. During `UIModule.start` and in later callbacks owned by the module, use `context.engine()` for operations that need the module's identity. Use the public engine view to query visible services and capabilities.

```java
import com.dishanhai.sh_better_ui.kernel.api.ThemeService;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import java.util.Optional;

final class ThemeQuery {
    static Optional<ThemeService> currentTheme() {
        if (!UnifiedUI.isAvailable()) return Optional.empty();
        return UnifiedUI.get().find(UIApis.THEME);
    }
}
```

`find(key)` returns an empty `Optional` when the service is missing from the current view. `require(key)` throws `NoSuchElementException`. A service key matches by ID, Java type and contract version. A capability describes available behavior independently of the service you call. Use `capabilities().supports(UIApis.DYNAMIC_THEME)` for a simple check, or `resolve(...)` for version constraints and required or optional capabilities. `supports` includes a compatible fallback. Re-resolve a service when its owning module or the target scope changes. [Capability system in Chinese](capability-system.md)

### Built-in services

The `UIApis` keys refer to the client services below. The portable contracts and service keys under `src/kernel/java` use Java 17. Client service signatures can use native Minecraft types from the corresponding game version. [Key declarations](../src/main/java/com/dishanhai/sh_better_ui/kernel/api/UIApis.java)

| Key and service | Operations and lifetime |
| --- | --- |
| `THEME` · `ThemeService` | Read the shared theme snapshot and registered themes. `scope(context, node)` inherits tokens and supports local overrides that end with the node. |
| `ANIMATION` · `AnimationService` | Use `motion()` for timelines and composition. `bind(...)` leases a declared property to a scoped track for playback, pause, resume and cancellation. |
| `LAYOUT` · `LayoutService` | Use `layout()` for direct measurement and alignment. Bind measure and arrange callbacks to a node and connect style changes to incremental invalidation. |
| `INPUT` · `InputService` | Route native input to active targets. Observe pointer motion through `POINTER_MOVED`. Native capture requires `CAPTURE_INPUT` and `BLOCK_VANILLA_INPUT`. |
| `FOCUS` · `FocusService` | Request focus for a native `GuiEventListener`. Keyboard and character input follow the actual focused target. |
| `COLLISION` · `CollisionService` | Mount a `UiCollisionLayout` and resolve hits using presented geometry, clipping and per-body hit slop. |
| `RENDER` · `RenderService` | Submit a one-shot `SCREEN` or `HUD` pass. Use its drawing context inside the callback for fills, clips, transforms and spatial scenes. |
| `RESOURCES` · `ResourcesService` | Read or observe the committed reload generation. Scoped alignment assets release their cache with the scope. |
| `ACCESSIBILITY` · `AccessibilityService` | Read preferences and supply semantic roles, labels and hints to Minecraft narration. Check `SYSTEM_SCREEN_READER` separately when you need system-level support. |
| `SCREENS` · `ScreenService` | Create a module-owned `UiScreen`, mount your active native screen, or mount a scope tied to the current client session. |
| `PLATFORM` · `UIPlatform` | Read platform information, GUI viewport, window focus and session status. `ui.platform()` resolves the same service. |
| `APPEARANCE` · `AppearanceService` | Query the native registry, profile templates and named presets. Use `applyPreset` for a validated local override, `draft` and `preview` to draw it, or `edit` to return the confirmed local group to the host. |
| `SPATIAL` · `SpatialUI` | Mount a scene on an owned scope, query feature support and bind spring motion. Draw the scene within a render pass through `RenderService.Context.spatial(...)`. |
| `ACTIVITIES` · `ActivityService` | Publish a scope-owned activity with immutable content, progress and optional controls. Its handle coalesces queued content updates and exposes the latest snapshot. |
| `NOTIFICATIONS` · `NotificationService` | Publish a bounded notification. Use a deduplication key and merge policy to combine notifications from the same owner and scope. |
| `LIVE_SURFACES` · `LiveSurfaceService` | Read the current presentation plan and geometry or reserve GUI space until the reservation or owning scope closes. |

### Scheduling, settings and events

- `scheduler().submit(...)` accepts scope-bound `UITask`s. Use a short UI callback for state changes and an explicit background task for blocking or CPU-heavy work. Cancel through the returned `TaskHandle`. Closing the scope prevents pending work or late results from reaching that generation. `scheduler().snapshot()` reports queue and phase statistics. [Scheduler in Chinese](scheduler.md)
- `configs()` and `states()` expose immutable snapshots and validated transactions. Use a `ConfigKey` with a declared type and allowed layers. Use a `StateKey` for scope-owned data. Define keys under your namespace and use the module's engine view for writes and observations. Third-party configuration persistence is wired by the consumer. [Configuration in Chinese](configuration.md)
- `commands()` executes typed requests. A command transaction can stage state changes, invalidations and later tasks together. After-commit work has its own task result and can fail after state has been published. [Commands in Chinese](configuration.md#命令與副作用)
- `events().subscribe(type, scope, phase, priority, listener)` ties delivery to a scope. `emit` delivers synchronously on the UI thread. `post` queues an immutable payload. Propagation runs through capture, target and bubble. `stopPropagation()` ends propagation after the current node and `stopImmediatePropagation()` also skips its remaining listeners. `markHandled()` records handling while delivery continues. `preventDefault()` sets the cancelable event's default flag for the sender to interpret. `consume()` ends propagation. The native bridge checks the active scope and permission before blocking vanilla input. [Event reference in Chinese](public-api.md#事件傳播)
- `ownership()` arbitrates leases for exclusive resources. Declare the resource in the module descriptor and request it using an owned scope. Priority affects arbitration after permissions are checked. Scope closure releases its leases and subscriptions. [Permissions and leases in Chinese](permissions.md)

### Register a third-party module

Add `META-INF/sh_better_ui.modules` to your mod's own JAR. Each entry pairs the loader mod ID with a public `UIModule` implementation that has a public no-argument constructor. For a mod named `example_mod` the entry can be `example_mod com.example.client.DemoModule`.

1. `descriptor()` declares the module ID under the loader mod ID's namespace, its version, dependencies, capabilities, requested permissions and resources.
2. `register(context)` uses `context.services()` to register typed services. The registrar expires at the end of the callback. Registry snapshots are read-only.
3. `start(context)` resolves services and defines state or configuration using the module-bound engine view. Create disposable work under the relevant module, session, screen or component scope.
4. `stop(context)` releases external resources and clears retained references. Resources tracked with `ScopeHandle.track(...)` are released with the scope.

The runnable example registers a typed counter service and a custom capability, mounts a native screen, and uses a scoped configuration value. It falls back to Minecraft narration when system-level screen reading is unavailable. [Third-party example project](../examples/kernel-extension/README.md) · [Complete registration example in Chinese](module-development.md)

## Migrate an existing screen

Keep the native controls and rendering callbacks. Start by moving the work that needs lifecycle coordination into a scope. Use `ScreenService.create(...)` for a module-owned builder, `mount(...)` for the active screen, and the mounted scope for tasks, subscriptions and animations. Let the host retain business data that must survive a screen change. Use a fresh scope when the screen is reopened.

This example turns a native button callback into a scoped refresh task. Keep your `ModuleContext` from `UIModule.start` and call `open` on the client thread. Supply translations for the two `example_mod.ui.*` keys and keep `refresh` short enough for a UI callback.

```java
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIPhase;
import com.dishanhai.sh_better_ui.kernel.api.UIPriority;
import com.dishanhai.sh_better_ui.kernel.api.UITask;
import com.dishanhai.sh_better_ui.kernel.api.UiId;

final class RefreshPage {
    private static final UiId TREE = UiId.parse("example_mod:refresh_page");
    private static final UiId REFRESH = UiId.parse("example_mod:refresh");
    private ScopeHandle tree;

    void open(ModuleContext module, Runnable refresh) {
        var ui = module.engine();
        var screens = ui.require(UIApis.SCREENS);
        var builder = ShBetterUiApi.screen("example_mod.ui.title").onInit(page -> {
            if (tree == null || !tree.isOpen()) tree = screens.mount(module, page, TREE);
            var target = tree;
            var area = page.viewport();
            page.button(area.x() + 12, area.y() + 12, 100, 20, "example_mod.ui.refresh",
                    () -> ui.scheduler().submit(UITask.ui(target, UIPhase.STATE_MUTATION,
                            UIPriority.USER_INTERACTION, refresh).replacing(REFRESH)));
        });
        screens.create(module, builder).open();
    }
}
```

Create one `RefreshPage` per screen instance. Resizing rebuilds the button while keeping the active tree. Repeated clicks replace pending refresh work for that tree. Leaving the screen closes the tree and cancels its pending refresh.

Existing KubeJS client scripts can keep `SHUI.screen(...)` and `SHUI.registerExtension(...)`. The script integration cleans up its screens and registered extensions on script reload. Use a loader-discovered Java `UIModule` when publishing a typed kernel service for Java consumers. [Migration example in Chinese](migration-guide.md) · [KubeJS integration](KUBEJS.md)
