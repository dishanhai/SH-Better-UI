# Screen Dock compact indicators

This migration slice builds on the persistent Screen Dock implementation. It
connects the shared compact-indicator API to native 26.1.2 drawing and restores
edge-triggered hover behavior: explicit expansion/collapse commands remain in
control until the next pointer boundary crossing, protected interactions renew
the close delay, and hidden windows cannot react through a window above them.

Indicators are per-window instances. Their existing renderer owns measurement,
clipping, callback failure handling and update cadence. Reduced-motion and
background/low-power flags are passed through rather than inventing animation
state in the native adapter. Timed collapse snaps directly to its target when
reduced motion is enabled. Failed or empty indicators fall back to the window
title without changing its hit rectangle. Example indicator values are labeled demonstrations,
not a connected player, a live download or fabricated media metadata.

## Retained boundaries

This is a Screen-owned Dock, not a HUD window host. Glass compositing and SPRING
collapse remain unsupported rather than silently falling back to a different
behavior. Existing persistence permissions, module ownership and cancelled-drag
semantics remain intact. The accompanying PR records the exact tested behaviors
and any actual-game limits; the example alone is not a performance benchmark.
