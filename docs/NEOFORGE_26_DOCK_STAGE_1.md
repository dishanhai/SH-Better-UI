<!-- Author: dishanhai -->
# NeoForge 26.x dock/windows: Screen-first migration

The first native adapter implements `WindowService` and `DockService` on an active,
module-owned Screen tree. It uses the shared deterministic DockLayout solver and
26.x render-state extraction; Forge/1.21.1 adapters are not changed.

Supported: ordinary content callbacks, scoped handles, floating/detached/edge
placement, clamped resizing, drag grip, close control, pointer capture, cancellation,
EASE/LINEAR expansion, auto-hide hover delays, pinning and collision/layout snapshots.
Drag release snaps to a nearby safe edge. Escape cancels the current gesture before
collapsing a focused window. Ctrl+arrows move; Ctrl+Shift+arrows resize; Ctrl+W closes;
Tab cycles window focus. Resize and loss of the active Screen cancel gestures.
Closing the owning scope disposes content and handles. Content drawing is clipped and
its render context expires at the end of the native pass.

Creation requires REGISTER_RENDER_PASS, CAPTURE_INPUT and BLOCK_VANILLA_INPUT on
the owned tree. Namespace ownership is checked. Query results are module-local.

## Explicitly not migrated in this increment

Client-session/HUD windows, persisted placement, Liquid Glass surfaces, custom compact
indicators and spring collapse are rejected before a window is allocated. They are
not silently replaced by placeholders. General spatial rendering in content remains
subject to the native RenderService.Context capabilities. This is not full legacy
Dock parity, and native game/narrator acceptance remains pending.

`NativeDockWindowsTest` exercises real service scope ownership and disposal, layout,
drag cancellation/commit, resize cancellation, and unsupported-feature rejection.
The shared DockLayout kernel tests cover packing/collision math separately.
