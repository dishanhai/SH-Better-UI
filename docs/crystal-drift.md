# Crystal Drift / 悬浮晶片

`sh_better_ui:crystal_drift` is an original SHBUI background registered through
`BackgroundRegistry`, available from UUK `AppearanceService.backgrounds()` and the
existing background selector. The registry installs its editable record schema;
theme overrides use the same namespaced ID. No separate React runtime is required.

## Source and scope

The requested visual reference was React Bits AeroShards. Its current upstream
license restricts redistribution of components, including ports. This implementation
therefore **does not contain or port its JavaScript, WGSL, shader source, or algorithms**.
It uses independently authored seeded 2D crystal sprites, layered motion and an
analytic faceted shader under SHBUI's existing MIT license. The reference is a design
credit, not a claim that upstream code was incorporated or relicensed.

Reference: https://reactbits.dev/backgrounds/aero-shards
License inspected: https://github.com/DavidHDev/react-bits/blob/cf2801031f07f53d4684e65b961bde7c2e2a6921/LICENSE.md

This is intentionally a lightweight stylized alternative, not a pixel-identical
port: glass/chrome are visual finishes, not physical refraction/reflection. Depth is
suggested by size, opacity and layer order. `glow` is a local edge halo, not bloom.
There is no full-scene depth-of-field, ray tracing, texture sampling or offscreen target.

## Configuration

- `count`: 16–320 crystals (default 96).
- `size`: 0.25–3, `speed`: 0–2, `spread`: 0.2–2.
- `opacity`, `glow`, `parallax`: 0–1.
- `placement`: `FULL`, `LEFT`, `RIGHT`, `CENTER`.
- `material`: `GLASS`, `CHROME`.
- `color`, `accentColor`, `backgroundColor`: ARGB colors; alpha is respected.
- `mouseInteraction`, `pulseOnClick`, `holdToGather`: independent interaction switches.

Reduced motion freezes the arrangement and disables pointer effects. High contrast
suppresses the decorative layer. Missing shader resources or a non-invertible pose
fall back to the configured flat background (dark blue by default). Invalid finite
numeric values are clamped; non-finite values are rejected by the profile constructor.

The host owns one `BackgroundLayer` per surface and closes it with the screen.
Pass `mouse = null` when a modal layer covers the background, and forward only
unconsumed background clicks to `BackgroundLayer.click`. Decorative input never
consumes screen events. The pointer tracker rejects drags originating outside the
area and resets on focus loss. Theme selection/parameters use the existing preview,
apply and cancel workflow; no main configuration page was redesigned.

## Cost and verification boundary

One texture-free draw batch, bounded by 320 quads. The CPU field is O(count), with
arrays retained between frames. No screen-owned GPU resources or framebuffer
allocations are introduced. Selecting fewer crystals and reducing size lowers
CPU/overdraw cost. This is a cost bound, not a measured FPS guarantee.

Both Forge 1.20.1 and NeoForge 1.21.1 register the same shader through their existing
loader hooks; the batch uses their shared vertex bridge. Manual acceptance should
check both loaders: layout presets and glass/chrome; theme editing persistence;
resize and transformed/clipped UI; reduced motion/high contrast; mouse leave,
focus loss and modal overlays; click pulses and long holds; resource reload/missing
shader fallback; and close/reopen without input or GPU-state leakage.
