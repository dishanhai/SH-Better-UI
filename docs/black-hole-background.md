# Schwarzschild black-hole background / 物理黑洞背景

`sh_better_ui:black_hole` now traces null geodesics of a non-rotating Schwarzschild
black hole, instead of the previous hand-drawn light-band approximation. A real
texture-space bloom pass spreads highlights. The physical hit cache is RGBA16F. Per-frame material shading is tone-mapped into
RGBA8 before bloom, so bloom remains an LDR presentation effect, not an
HDR energy-conserving optical point-spread simulation. The optional artificial outline
starts at zero. Turning the outline off does **not** remove the physical dark
shadow formed by captured rays.

This is a numerically approximated optical model, not a complete black-hole,
plasma or magnetohydrodynamics simulator. It does not simulate Kerr spin/frame
dragging, evolving matter, a spectral camera, or infinitely many photon images.
The monochrome/default color, procedural disk brightness and bloom are artistic
presentation controls, not predictions of a measured astrophysical spectrum.

## Physical model and provenance

Units are `G = c = M = 1`. The event horizon is at `r = 2`, the unstable circular
photon orbit at `r = 3`, the critical impact parameter at infinity is
`b = 3 sqrt(3)`, and the disk's inner edge is the Schwarzschild ISCO at `r = 6`.
The static camera is at `r = 30`. With local angle alpha measured from the inward
radial direction, `b = r sin(alpha) / sqrt(1 - 2/r)`. In the ray's orbital plane,
`u = 1/r`, `v = du/dphi`, the equations are:

```
du/dphi = v
dv/dphi = 3*u*u - u
v*v + u*u - 2*u*u*u = 1/(b*b)
```

Incoming initialization uses `u = 1/30`, `v = cos(alpha)/b`. Radial rays require a
separate safe path. RK4 integrates the orbit; disk emission is evaluated at
interpolated crossings of its actual plane, not at a painted screen-space arc.
Finite step size, angular range, resolution and crossing interpolation bound
accuracy. Rays that exhaust the tracing budget are unresolved, not proof of
capture. Quality modes use the same metric, not different gravitational strengths.

The disk is prescribed, infinitesimally thin and opaque at the first valid ray
crossing. Its radial emissivity follows `r^-3 * (1 - sqrt(6/r))`, multiplied
by prescribed moving emissivity filaments and exposure. Circular Keplerian angular velocity
`Omega = r^-3/2` supplies a redshift/beaming factor
`g = sqrt((1 - 3/r)/(1 - 2/30)) / (1 - Omega*lambda)`; bolometric emission
scales by `g^4`. The tint and tone mapping are display choices, not a full
blackbody/spectral calculation. No gravitational travel-time variation or fluid
evolution is integrated.

Primary references reviewed on 2026-11-16:

- [David Tong, General Relativity, section 1.3.5, equation 1.54](https://www.damtp.cam.ac.uk/user/tong/gr/grhtml/S1.html)
  for the null-orbit equation and first integral.
- [Eric Bruneton, Real-time High-Quality Rendering of Non-Rotating Black Holes](https://ebruneton.github.io/black_hole_shader/paper.pdf)
  for the optical-model/background-rendering context and distinction between
  geodesic transport and camera lens glare. His implementation is
  [BSD-3-Clause licensed](https://github.com/ebruneton/black_hole_shader/blob/master/LICENSE).
  This implementation does not copy its code or use its precomputed tables.
- [NASA: Anatomy of a Black Hole](https://science.nasa.gov/universe/black-holes/anatomy/)
  for the observable shadow and multiple images of the disk.

The shader, Java integration and numerical tests are independently authored under
SHBUI's existing license. No reference screenshots or third-party shader source
are bundled. Mathematical equations are implemented independently; the cited
articles and their prose are not redistributed.

## Configuration and compatibility

The standard UUK background registry/theme editor supplies preview/apply/cancel,
persistence and reset. Existing saved records remain readable; absent new fields
receive defaults, and the old ten-argument Java constructor remains supported.

| Field | Range/default | Meaning |
| --- | --- | --- |
| speed | 0–2 / 0.18 | Disk pattern phase rate; zero freezes animation |
| size | 0.5–1.8 / 1 | Image scale / field of view, not gravitational mass |
| tilt | -0.7–0.7 / 0.08 | Image roll, radians |
| diskWidth | legacy | Retained for compatibility; no effect in physical mode |
| lensing | legacy | Retained for compatibility; never alters the metric |
| brightness | 0–2 / 1 | Exposure/presentation strength |
| glow | 0–3 / 1.2 | Texture-space bloom strength; zero disables bloom |
| opacity | 0–1 / 1 | Opacity of the whole composite |
| color | ARGB / FFF5F5F5 | Disk light tint |
| backgroundColor | ARGB / FF080808 | Base/fallback color |
| inclination | 0.1–1.4 / 0.25 | Camera elevation above disk, radians |
| outline | 0–1 / 0 | Optional artificial shadow contour; zero hides it |
| quality | LOW / BALANCED / HIGH; BALANCED | Resolution and integration budget |
| diskOuterRadius | 6.5–24 / 18 | Outer radius in M; inner radius remains 6 M |

Finite values clamp, non-finite values reject. Reduced motion pauses animation
without catching up later, and high contrast suppresses decoration. No pointer
input is captured. Missing shader or unsupported rendering resources fall back to
the configured base color. Physically dark pixels and optional outline are distinct;
opacity fades the whole composite. The shader still has cost when animated; reduced
motion is not a claim that all GPU work disappears.

## Validation and limitations

A test-only double-precision reference checks critical capture/escape, invariant
drift, RK4 convergence, weak-field deflection and finite-camera initialization.
The EGL validation script compiles the actual production shaders and reads back
floating-point output; it checks finite pixels, boundary dimensions/parameters,
phase continuity, alpha and diagnostic physical results against CPU references.

Run `python3 scripts/verify-black-hole.py --output /tmp/black-hole-check` with
Python 3, Pillow, libEGL/libGL and Mesa surfaceless support. Software GL timings
are development measurements, not real GPU or Minecraft frame-time guarantees.
The trace/composite resource budgets and measured values are recorded with the
validation output. Initial allocation/shader compilation can still cost time.

Physical geometry is built only when size, roll, inclination, disk radius, quality
or actual framebuffer extent changes. Phase, color, exposure, opacity and glow do
not retrace light paths. Two bounded 2048×2048 RGBA16F slots hold geometry, with replacement built in strips, with a shared limit of 16,384 traced pixels
per host-frame timestamp. Geometry is published atomically, never as half-built
stripes. A non-preemptive build is allowed to finish before a newer key takes over.

LOW/BALANCED/HIGH cap the longest edge at 384/1536/2048 physical framebuffer pixels
(including GUI and pose scale), with unchanged 192/320/512 RK4 steps. Every display
frame shades the cached disk coordinates with moving material detail; the scene
body is no longer globally low-pass filtered. Highlight bloom uses two small
separable blur passes instead of 49 samples on every full-screen output pixel.
Two exact-size rectangular geometry targets, one grow-only RGBA8 shading target
and two 256² bloom targets bound image storage to approximately 80.5 MiB at the
2048² worst case, excluding driver overhead. Targets are allocated lazily; two
1536×864 geometry slots plus a matching scene and bloom use about 25.8 MiB.
A LOW-only session does not allocate maximum-size targets.

The black-hole-specific clock advances ordinary frame intervals up to 250 ms in
real time, so 10 FPS does not run at half speed. Longer stalls do not catch up;
reduce-motion, speed zero and close/resume preserve a frozen phase. Other ambient
background clocks are unchanged. HUD-glass blur remains the user's separate
material setting; this fix does not silently override it.

First shader compilation and target allocation can still cause first-use cost.
The initial or changed geometry appears after several bounded build frames; before
the first complete result the configured base is shown. Multiple distinct geometry
keys can compete for the bounded cache. More than two distinct visible keys may
repeatedly evict each other and show the base fallback while rebuilding; no
per-screen unbounded GPU allocation is introduced. Actual UI and GPU frame time still requires in-game acceptance.

Before release, test both Forge 1.20.1 and NeoForge 1.21.1 in Minecraft: setting
preview/cancel and persistence, repeated selection and close/reopen, all quality
modes, window resizing, resource reload, reduced motion/high contrast, transparent
colors, host pose/scissor, GL state restoration and target-hardware frame times.

### GPU arithmetic cross-check

Run `JAVA_HOME=<jdk17-or-newer> python3 scripts/verify-black-hole-geodesics.py
--output /tmp/bh-geodesics` (the command is one line). This extracts the production
GLSL derivative/RK4 functions, camera constants and initializer into a diagnostic
float shader and compares initial/intermediate/terminal states against the
independent Java double-precision oracle. The 144-sample software-GL run passed
all three tiers, including unresolved near-critical rays: maximum absolute
u/v/phi differences were approximately 3.6e-5 / 3.8e-5 / 4.9e-5, and relative
null-energy error was below 9.1e-7. This validates the sampled numerical arithmetic,
not the full disk transfer, Minecraft GL-state restoration, or real hardware.

### Why the prior version appeared blurred and static

The previous default traced only 256 pixels along the longest edge and then
applied a five-tap filter to the entire scene. At 2048-pixel output width that
source was enlarged eightfold. Its material term was only `0.9 + 0.1*sin(...)`;
with default speed 0.18, local intensity repeated after 34.91 seconds and a single
crest took 104.72 seconds to orbit. In a 512×256 default offscreen comparison,
one second changed RGB by only 0.30/255 on average (maximum 2/255). No pixel
changed by more than 3/255. These measurements explain poor visible motion, not
a claim that the time uniform was disconnected. The old 50 ms frame-time cap also
slowed animation below 20 FPS.

The resolution, progressive geometry cache, independent per-frame material shading
and clock changes address these separate causes. Before/after comparisons use the
same viewport, default camera and physical model; they are not screenshots of an
actual Minecraft UI and do not include the user's additional HUD-glass blur.


### Follow-up clarity correction

The previous 768px balanced cap still enlarged the image 2.5× at 1080p and 5× at
4K. The balanced/high caps are now 1536/2048 physical pixels (LOW remains 384).
This improves the spatial detail budget; it is not native-4K rendering. Geometry
is allocated at its actual rectangular extent and the shared shade target grows
only when needed, to avoid allocation churn when two previews have different sizes.
Sampling uses each texture's actual width and height, not a fixed square assumption.

The emission previously consisted mostly of broad smooth sine bands, which could
look blurred even with bloom disabled. Narrower advected bands and radial filaments
now provide disk-local structure, with derivative-based filtering including the
fourth harmonic and an analytic mean fallback at subpixel sizes. This is a prescribed
emissivity model, not a GRMHD simulation. Strong bloom remains configurable; the
scene body is never convolved with it. Optional HUD-glass blur is independent.

The unchanged 16,384-pixel trace budget trades cold-cache latency for clarity:
1080p balanced builds in 87 host frames, and the maximum 2048² high-quality square
needs 256 frames. These are frame counts, not wall-clock guarantees. The first
complete image is still published atomically; animated emission then reuses it.
Use LOW for faster startup or lower memory. No synchronous full-frame raytrace or
unbounded cache was added.

Validation uses the production GLSL in software EGL, including rectangular targets,
large-to-small viewport reuse, finite values, freeze/wrap, glow/opacity controls
and steady-frame no-retrace checks. This does not replace actual Minecraft visual
acceptance, GPU memory tracking or hardware frame-time testing.
