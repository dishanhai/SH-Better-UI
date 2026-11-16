# Original ambient backgrounds / 原创氛围背景

Five lightweight, independent SHBUI backgrounds are available through the normal
background selector and UUK `AppearanceService.backgrounds()`:

| ID | Appearance | Suggested use |
| --- | --- | --- |
| `sh_better_ui:soft_aurora` | Soft, broad colored light curtains | Quiet dark navigation/settings surfaces |
| `sh_better_ui:silk_ripples` | Diagonal satin folds | Restrained purple/blue decorative panels |
| `sh_better_ui:iridescent_flow` | Smooth overlapping color fields | Larger previews and accent surfaces |
| `sh_better_ui:contour_flow` | Fine, slowly deforming topographic contours | Quiet blue-green technical/navigation panels |
| `sh_better_ui:mist_glow` | Two broad, overlapping pastel mist pools | Subtle atmospheric backing behind content |

These are continuous analytic surfaces, not variants of the existing particle,
light-ray, glyph-grid or shape-wave backgrounds. They are not coupled to Crystal
Drift and can be used without that feature or its PR.

## Provenance

The user requested background categories from [React Bits](https://reactbits.dev/c/backgrounds).
Its [current license](https://github.com/DavidHDev/react-bits/blob/cf2801031f07f53d4684e65b961bde7c2e2a6921/LICENSE.md)
restricts redistribution of components, including ports. Accordingly, these Java
classes and GLSL formulas are independently authored for SHBUI, under its existing
MIT license. No React Bits JavaScript/component/shader source was copied, and no
third-party component is bundled. This reference credits the requested visual
categories, not an incorporated dependency. The results are original stylizations,
not pixel-identical recreations of upstream demos.

## UUK and configuration

Each background has its own `AmbientBackdropProfile` registered with the existing
record-based theme editor. Override groups use the exact namespaced IDs above.
The existing preview/apply/cancel flow and persistence are unchanged.

| Parameter | Range | Meaning |
| --- | --- | --- |
| `speed` | 0–2, default 0.25 | Radians per second; zero holds the current phase |
| `scale` | 0.25–4, default 1 | Spatial frequency |
| `intensity` | 0–1, default 0.65 | Colored effect strength; zero leaves the base color |
| `opacity` | 0–1, default 1 | Whole-background alpha |
| `primaryColor`, `secondaryColor` | ARGB | Preset-specific effect colors |
| `backgroundColor` | ARGB | Base/fallback color; preset defaults are opaque dark colors |

Finite out-of-range values clamp; NaN/infinity are rejected. Colors retain their
alpha channels, including a fully transparent base. Each layer owns its animation
clock; separate screens do not advance one another's effects.

## Accessibility, fallback and cost

- Reduced motion freezes the phase and resumes without catching up paused time.
- High contrast suppresses decorative drawing through the existing background layer.
- Invalid/empty bounds and zero opacity do not submit the effect.
- Missing shader resources fall back to the configured base color with opacity.
- No pointer interaction: the background cannot intercept or react to controls.
- All five styles share a single shader resource, with one quad/pass per layer.
  No textures, loops, offscreen targets, per-frame pixel readback, or screen-owned
  GPU allocations are used. Runtime cost scales with visible pixels; rendering a
  frozen background still rasterizes it. These are bounded design properties,
  not a measured FPS guarantee. Contours use derivative-based edge coverage and
  fade unresolved lines; mist uses broad analytic falloff with pixel-footprint
  softening. Neither new mode adds a loop, CPU bitmap/cache or GPU allocation.
- Normal GUI pose and host scissor clipping apply. The renderer restores the host's
  blend/depth/cull/write state after submission.

Both Forge 1.20.1 and NeoForge 1.21.1 register the shared shader through their
existing reload hooks. Minecraft's shader manager owns its resource lifetime.

## Acceptance checklist

Automated profile/clock/registry tests and software OpenGL offscreen validation do
not replace testing in Minecraft on real GPUs. Check all five styles on both
loaders: theme selection and saved parameter edits; wide/tall windows; GUI scale,
pose transforms and scissor; reduced motion and high contrast; transparent colors;
zero intensity/opacity; resource reload and shader failure fallback; screen close
and reopen. Verify content readability and frame time on the intended hardware.
