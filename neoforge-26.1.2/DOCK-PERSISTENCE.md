# Native Screen dock placement persistence

`MiniWindowSpec.persistent(true)` now stores committed layout through the consumer module's
`UIConfigCenter` and the host's existing atomic `ConfigDiskStore`. It requires the normal window
permissions plus `MODIFY_GLOBAL_CONFIG`, and configured host persistence. Missing permission/storage
fails before a window or its content is allocated.

Storage keys include the full module ID and window ID. They are not shared across modules that happen
to use the same namespace and window path. The schema/persistence binding lives in the owning module
scope; the actual window still lives only in its Screen UI tree/component scope. Closing a window or
screen never keeps the window alive, but creating the same window again restores its layout.

Position, size, edge/mode and pin state are committed after programmatic changes or a completed pointer
gesture. Drag/resize preview and cancellation (Escape, focus loss, screen switch, scope close) do not
write. Reset stores the current specification's default placement. Restored sizes obey the current
specification's limits; groups come from the current specification rather than stale stored data. The
layout solver clamps presentation to smaller screens without overwriting preferred larger-screen size.

This does not implement HUD windows, glass materials or spring-collapse rendering. It does not reuse
the older standalone global dock-layout file and performs no ad-hoc path writes. Configuration flush
and shutdown retain their existing host lifecycle. Tests use temporary real configuration storage and
independent host instances, not a static process-local map.
