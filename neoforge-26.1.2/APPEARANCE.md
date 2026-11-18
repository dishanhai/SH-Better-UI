# Native player appearance settings

This migration slice provides a player-facing appearance editor for the NeoForge
26.1.2 control panel. It is not the full legacy background registry or an Apple
Music visual-parity implementation.

## Configuration and rendering

The editor works on a screen-local theme draft. Previewing or closing a discarded
draft must not change the saved client configuration. Applying a valid theme
preserves unrelated accessibility, blur, glass and music settings. Reset has an
undo path. Custom colors accept six-digit RGB values; empty values inherit the
selected light/dark base. Custom colors affect Custom mode, not the fixed glass
presets. The existing glass presets remain selectable.

The settings file now retains the complete ThemeSettings value, including dormant
color overrides, effect groups, registered theme ID and palette archive. Retaining
these fields is not a claim that every legacy renderer or registered theme is
available in this port. Unknown background IDs are kept rather than silently
rewritten, with a safe visible fallback.

Supported procedural backgrounds are solid, gradient and dot matrix. They use
native GUI extraction, own no textures, workers or remote resources, and bound the
dot draw count. Reduced-motion mode uses a fixed animation phase; high contrast
suppresses decoration. Original world/panorama backdrop behavior is preserved
unless the player chooses a procedural background.

## Scope and remaining work

The background applies to the native control panel and its appearance preview.
Other screens, arbitrary consumer pages, HUD background materials, custom image
loading, the shader-based black hole and the complete legacy effect registry are
not migrated by this slice. No external fonts or images are downloaded.

Verification and runtime acceptance results are recorded in the accompanying PR.
