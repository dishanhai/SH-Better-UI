# Black hole / 黑洞背景

An original, lightweight SHBUI interpretation of the user's two visual references:
a dark circular shadow, broad white-grey accretion band, bent upper/lower light
arcs and soft glow. The default is monochrome; the light and base colors are editable.
This is a stylized background, not a general-relativistic simulation or an exact
recreation of a third-party demo.

## Research and provenance

Reviewed on 2026-11-16:

- [NASA: Anatomy of a Black Hole](https://science.nasa.gov/universe/black-holes/anatomy/)
  explains how the far side of an accretion disk appears above/below the shadow
  through gravitational lensing, and why thin light rings surround the shadow.
  Used for visual concepts only. No NASA image is bundled.
- [Kartik Kabadi's WebGL black hole](https://github.com/kartikkabadi/blackhole)
  documents a real-time geodesic implementation with 160 integration steps per
  pixel. Its [license](https://github.com/kartikkabadi/blackhole/blob/main/LICENSE)
  is MIT (copyright 2026 Kartik Kabadi). We inspected its documentation and license,
  not its shader implementation. Its per-pixel integration approach was deliberately
  not adopted for an always-on UI backdrop.

The SHBUI shader and parameters are independently authored under this repository's
MIT license. No third-party shader/component code or reference image is redistributed.
The visual references are inspiration, not an assertion of their origin or license.

## Configuration and behavior

Select `sh_better_ui:black_hole` in the standard background selector or through UUK
`AppearanceService.backgrounds()`. It uses the existing theme parameter editor,
preview/apply/cancel, persistence and reset behavior.

| Field | Range | Default | Meaning |
| --- | --- | --- | --- |
| speed | 0–2 | 0.18 | Animation radians per second; zero freezes phase |
| size | 0.5–1.8 | 1 | Shadow and disk scale |
| tilt | -0.7–0.7 | 0.08 | Disk rotation in radians |
| diskWidth | 0.05–0.35 | 0.16 | Accretion band thickness |
| lensing | 0–1.5 | 1 | Stylized bending strength |
| brightness | 0–2 | 1 | Light intensity; zero leaves base color |
| glow | 0–1 | 0.55 | Broad halo strength |
| opacity | 0–1 | 1 | Whole-background opacity |
| color | ARGB | FFF5F5F5 | Light color |
| backgroundColor | ARGB | FF080808 | Base and shader-failure fallback |

Finite values clamp to supported ranges; non-finite values are rejected. Colors
retain alpha for the light and base layers. The central shadow intentionally remains
black and opaque even with transparent light/base colors; whole-background opacity
also fades that shadow. At zero brightness the entire effect, including its shadow,
is disabled and only the base remains. Each background instance owns its clock. Reduced motion freezes it
without catching up on resume. High contrast suppresses decorative drawing. No
mouse interaction is installed, so controls retain input focus and hit testing.
Missing shaders fall back to the configured base color.

## Performance and acceptance

One quad and one fragment pass; analytic shapes rather than ray marching. No image
decode, per-frame CPU bitmap generation, textures, FBOs or screen-owned GPU buffers.
Cost still scales with visible pixels, even with animation frozen. Soft edges use
pixel-footprint smoothing; zero glow suppresses the broad halo. This is a bounded
rendering design, not a frame-rate guarantee. Minecraft owns shader reload/lifetime.

Before release on both Forge 1.20.1 and NeoForge 1.21.1, check selection and edited
parameters, saved/reopened settings, preview cancellation, fast switching, landscape
and portrait sizes, GUI scaling, pose/scissor clipping, reduced motion, high contrast,
transparent colors, resource reload, missing-shader fallback and frame times on the
actual target GPU. Automated and offscreen software OpenGL validation do not replace
that in-game acceptance.

### Reproducible offscreen check

Run `python3 scripts/verify-black-hole.py --output /tmp/black-hole-check` with
Python 3, Pillow, libEGL/libGL and Mesa surfaceless support. It compiles the actual
shader pair, renders to a floating-point RGBA32F test attachment, checks every
read-back channel is finite, and writes PNG previews plus a JSON results file.
Coverage includes landscape/portrait/square/tiny/odd and one-pixel-wide sizes,
parameter endpoints and combined maxima, opacity, transparent layers, zero
brightness and 0/2π phase continuity. The test attachment is test-only; the
production effect does not allocate an FBO.

On the development cloud's Mesa llvmpipe renderer, 720×360 RGBA32F, 30 draw+glFinish
samples after 12 warmups, the default median was about 1.04 ms (glow disabled:
0.99 ms; brightness zero: 0.42 ms). These shader-only software measurements exclude
the game renderer and do not predict hardware GPU frame times. Resource reload,
pose/scissor state and in-game interaction still require real-client validation.
