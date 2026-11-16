# Native theme-following fluid backdrop

`sh_better_ui:muonium_fluid` is registered as **Muonium fluid** in the UUK background picker, appearance editor and developer background gallery. It is separate from the selected theme: choose it as the background, then switch theme to recolor it live.

The effect is an original four-source analytic fluid field in SHBUI's existing ambient shader. It uses one native draw pass, no offscreen target, texture upload, image readback, asset download, Skija dependency or cover/audio service. It is **not** an implementation of QPlayer/AMLL or a cover-driven music visualizer. Its fixed-cost procedural quality avoids resolution-sized CPU work; turning opacity to zero skips drawing. This choice provides a usable transparent, moving background without importing the upstream compositor.

## Configuration and behavior

- `followTheme` defaults on. Each frame reads the active native theme colors, while preserving the configured alpha of each color. Turn it off to use the three custom ARGB colors.
- `opacity`, `intensity`, `scale` and `speed` have bounded editor sliders. Default speed is 0.28 radians per second, and opacity is 0.8; the base itself has alpha 0x40, leaving the host background visible. Individual color alpha also composes correctly.
- Reduced motion freezes phase, with no catch-up jump on resume. High contrast suppresses the decorative effect. Reopening the host owns a fresh renderer; close cancels clock catch-up.
- Shader unavailability uses a translucent base tint, without per-frame compile retries. Shader lifecycle, clipping and blend-state restoration use the already-shared native renderer. Disabled backdrop blur does not add work because the fluid shader itself does not blur.
- A host must draw it over content intended to remain visible. `BackgroundLayer` does not fill an opaque base; a host that deliberately paints an opaque page underneath will show the fluid over that page rather than the world.

## Upstream investigation and license boundaries

Reference revision: `9b4cc7e7bd143315da716075a6d024a0d4c0c81b` of [MuoniumPlayer-multiversion](https://github.com/zihe114514/MuoniumPlayer-multiversion).

- `muonium-common/.../render/skija/SkijaFluidBackground.java` explicitly labels its compositor and three shader algorithms as a QPlayer adaptation under Apache-2.0, points to `TIMER-err/qplayer`, and describes a 32×32 working cover texture and AMLL adjustment chain. It also owns runtime effects, prior-cover cross-fades, a static-mode image cache and extra layered modes.
- `muonium-common/.../core/screens/ncm/QPlayerFluidLyricBackground.java` supplies cover textures and smoothed playback/audio-driven time. It has a separate blurred-cover mode and releases borrowed references/owned renderer state.
- `muonium-common/.../core/screens/ncm/foliabg/FoliaBackgroundMode.java` defines additional layered background choices; these are not all equivalent to a simple theme preset.

Those files are evidence of the reference's behavior and attribution chain, not a blanket assumption that every shader/asset has the root license. This native implementation copies none of their algorithms, shader source, image assets or audio logic. The existing palette adaptation's Apache attribution is preserved separately. No claim is made that all upstream modes have been ported.

## Validation

JUnit coverage exercises native registry/profile registration, distinct renderer ownership, editable persistence, ARGB preservation during live theme changes, manual colors, parameter bounds, and high-contrast/empty-size short circuits. Existing ambient-clock tests cover reduced motion, pause, suspend, extreme timestamps and wrap.

A headless Mesa OpenGL 4.5 software-driver probe compiled and linked the actual shader, rendered 720×360 / 360×640 / 64×32, verified visibly differing phases, seamless 2π wrap (≤1 byte delta), zero opacity and translucent alpha. This is shader validation, not a real Minecraft GUI or hardware performance acceptance test.

A second software-GL visual pass compared blue, rose and porcelain palettes over a dark base at elapsed 0 / 1.5 seconds. The fluid kernels were narrowed and their separation increased after the first version looked too uniform. The resulting blue and rose pools are more separated while remaining smooth; porcelain intentionally remains lower-contrast. Real-client visibility still depends on the host underlay and selected colors.
