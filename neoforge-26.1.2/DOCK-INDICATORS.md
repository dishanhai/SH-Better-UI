# Screen Dock compact indicators

This migration slice builds on the persistent Screen Dock implementation. It connects the
shared compact-indicator API to native 26.1.2 drawing and restores edge-triggered hover
behavior: explicit expansion/collapse commands remain in control until the next pointer
boundary crossing, protected interactions renew the close delay, and hidden windows cannot
react through a window above them.

Indicators are per-window instances. Their existing renderer owns measurement, clipping,
callback failure handling and update cadence. Reduced-motion and background/low-power flags
are passed through rather than inventing animation state in the native adapter. Both timed
and spring collapse snap directly to their target when reduced motion is enabled. Failed or
empty indicators fall back to the window title without changing its hit rectangle. Example
indicator values are labeled demonstrations, not a connected player, a live download or
fabricated media metadata.

## Retained boundaries

This is a Screen-owned Dock, not a HUD window host. Screen glass plates are supported as described below; HUD glass is not.
SPRING now uses a private per-window `InterruptibleAnimation(Spring.STAGGER)` track with
continuous RETARGET reversal. Progress is bounded to 0..1; completion waits for the spring
to settle. A zero duration bypasses motion; changing reduced-motion during a transition
settles it on the next presentation. Existing LINEAR/EASE timing is unchanged.

The track belongs to the existing window lifetime and closes with its Screen tree; no new
animation-property claim is required from WindowService consumers. This is an internal Dock
animation, not a bypass of the public CollapseService claim contract. MiniWindowSpec
continues to reject controlled progress because the window owns its expansion. The Tools
Dock example now demonstrates SPRING. Existing persistence permissions, module ownership and
cancelled-drag semantics remain intact. The accompanying PR records the exact tested
behaviors and any actual-game limits; the example alone is not a performance benchmark.

## Screen glass plates

Screen mini-windows accept `MiniWindowSpec.glass`. The host requests one shared
backdrop before any Dock foreground submission, then each rounded plate uses
`LiquidGlassSurface.drawPrepared`. No window owns or retains a GPU snapshot.
The same-frame capture is reused, never recursively captured between windows.

Material optics respect the control panel optical settings. Optical glass off,
blur off, high contrast, zero transmission, quality FALLBACK, or an unavailable
snapshot use an opaque plate paired with the material foreground. An optical
plate also has a readability scrim; content text colors are constrained to the
declared ink, retaining bold/italic formatting. Plain windows are unchanged.
ULTRA/BALANCED/PERFORMANCE share the existing native shader budget, not three
quality-equivalent implementations. This is Screen Dock rendering, not a full
GlassService, HUD or Spatial implementation.

Tools → Dock example uses regular glass and spring motion over synthetic colored
reference bars. These bars and progress are demonstration data, not music state.
