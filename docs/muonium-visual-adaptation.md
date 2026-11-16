# Muonium visual-system adaptation

Reference revision: [`9b4cc7e7bd143315da716075a6d024a0d4c0c81b`](https://github.com/zihe114514/MuoniumPlayer-multiversion/tree/9b4cc7e7bd143315da716075a6d024a0d4c0c81b).

## What the upstream actually does

- `NCMTheme` provides semantic colors and interruptible color/material transitions. Its Liquid Glass and Crystal presets are real glass; Aurora Frost is a separate frosted look.
- `GlassLook` distinguishes the root tint from inner washes. Applying the same opacity to every nested layer makes an apparently transparent page opaque. It also separates theme strength from per-surface user switches.
- `GlassMaterialProfile` favors low blur and thin tint, preserves user-edited capture settings, and avoids a heavy opaque wash that hides refraction.
- `GlassBackdrop`, `GlassLiquidSurface`, `GlassRefraction` and `GlassBlurLevels` use the upstream Skija renderer, backdrop capture, refraction and cached blur levels. Their exact implementation is not a portable Minecraft widget.
- `SkijaFluidBackground` is an album-texture-driven SkSL pipeline with 32×32 working images, shader variants, cover transitions and static-frame caching. Its header identifies QPlayer adaptation and an AMLL adjustment chain. Folia adds separate background composition and overlays. These are not merely additional palette presets.

## What is integrated in SHBUI

1. **Six selectable themes**, including Liquid Glass, Aurora Frost and Crystal in addition to Rose, Graphite and Porcelain. The glass entries carry actual transparent root/inner surfaces, material flags, radius and blur parameters.
2. **Native material plumbing** rather than a new Skija dependency: map dynamic theme alpha and blur into SHBUI's existing glass drawing and scope-bound buttons; retain explicit widget blur overrides and accessibility/disabled-backdrop fallbacks.
3. **Transparent flowing background**, `sh_better_ui:muonium_fluid`: original, bounded single-pass color fields following the active theme by default. Configuration allows independent color, alpha, speed, scale and intensity. No cover readback/decoding, framebuffer allocation or extra render backend.
4. **Observable previews**: glass theme and transparent background previews draw over a bounded contrasting checker, not an opaque color-matched swatch. The same registered background is available in the configuration and developer background catalog.

The existing SHBUI theme manager already handles scoped previews, transition interruption, reload, high contrast and reduced motion. Existing native optics retain their own quality, framebuffer lifecycle and resource-reload controls. We do not add a second full-screen capture pipeline or frame-rate-dependent material switching.

## Deliberately not claimed

- This is not a complete MuoniumPlayer port, a replacement music player or a pixel-identical Skija reproduction.
- The new background follows semantic theme colors, **not album artwork**, and is not audio-reactive. It does not import the QPlayer/AMLL/Folia shader chain or other assets.
- The existing SHBUI glass implementation supplies native refraction/backdrop behavior. Upstream's exact optical shader, cover transitions and per-surface HUD switches are not reproduced.
- Automated tests and software OpenGL checks are not real Minecraft visual/GUI-scale/narrator validation or real-GPU performance guarantees.
- New media-card and background targets are Forge 1.20.1 and NeoForge 1.21.1. The experimental 26.x port is not claimed as validated.

## Attribution

Palette adaptations retain the upstream copyright and Apache-2.0 notices. Runtime artifacts include the original LICENSE, unchanged NOTICE, and our CHANGES under `META-INF/licenses/muoniumplayer/`. New native material plumbing, preview composition and fluid color fields are original SHBUI implementations informed by the documented visual goals, not copied upstream shaders or assets. See [theme entry points](muonium-theme-presets.md) and [media-card API](muonium-media-card.md).
