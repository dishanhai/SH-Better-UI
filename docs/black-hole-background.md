# Schwarzschild black-hole background / 物理黑洞背景

`sh_better_ui:black_hole` now traces null geodesics of a non-rotating Schwarzschild
black hole, instead of the previous hand-drawn light-band approximation. A real
texture-space bloom pass spreads highlights. The production trace texture is
RGBA8 and receives tone-mapped emission, so this is LDR threshold bloom, not an
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
by a small periodic pattern and exposure. Circular Keplerian angular velocity
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

One reusable 384×384 color target bounds its image storage to about 576 KiB
(excluding driver overhead). LOW/BALANCED/HIGH limit the traced longest edge to
128/256/384 pixels, with at most 192/320/512 RK4 steps of 0.04/0.025/0.016 radians.
The integration budget is therefore about eight radians, not infinitely many
orbits. Fixed-capacity storage avoids reallocating on each window/quality change.
Static frames reuse the trace; one owner's animation is retraced at most 30 Hz.
Different owners retrace independently to avoid phase leakage; many simultaneous
instances still multiply tracing work. A small reconstruction filter softens
undersampling. Bloom offsets are measured in trace texels, so lower quality also
changes apparent blur width. Resource failure falls back until the next reload.

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

### Measured cost (software renderer only)

The final 512×256 output / RGBA8 trace-target microbenchmark uses 12 warmups and
20 samples per quality. Trace+composite median/p95 milliseconds: LOW 8.28/11.31,
BALANCED 11.83/17.40, HIGH 15.54/22.13. A cached frame still pays the composite
cost (about 6–7.5 ms here) because the bloom uses a bounded 7×7 Gaussian kernel
plus five reconstruction samples. `glow=0` skips its 49 highlight taps. The
first trace took about 106 ms including driver first-use/JIT cost. This physical
version is substantially more expensive than the old analytic backdrop; it is
not described as lightweight or guaranteed stutter-free. These measurements
exclude initial target allocation, Minecraft overhead and real GPU behavior.
Run the visual script with `--benchmark` to reproduce the timing protocol.
