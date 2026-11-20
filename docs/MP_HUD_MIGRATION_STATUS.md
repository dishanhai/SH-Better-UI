# MP HUD / UI migration status

The current embedded player is documented in [MuoniumPlayer 2.6.0](MUONIUM_PLAYER.md). This page records the earlier integration.

Reference: MuoniumPlayer-multiversion revision `9b4cc7e7bd143315da716075a6d024a0d4c0c81b`, principally `muonium-common` rendering/UI code and 1.21.1 `HudConfig`.

This work is an incremental **native SHBUI adaptation**, not a declaration that all MP features have been ported. It does not require loading MP or executing its companion downloads. Existing MP settings bridge is a separate integration.

## Native implementations in this increment

- Music information/lyrics HUD: coherent MusicApi frame, cover, title/artist, playback state, bounded timeline/time, real synchronized lyric feed and explicit unavailable states. Overflow text uses the shared native-font marquee.
- Island lifecycle: first-observation suppression, track-change expansion, hover exit grace, manual expansion/dismissal, scene gates and suspend cleanup. Bounded FIFO notification queue coalesces stable IDs and supplies text/progress/narration.
- Ambient island: cached FPS/ping/clock, stable widths, 4 Hz sampling and status/now-playing rotation with reduced-motion behavior.
- Native HUD mounting: opt-in user configuration and scope-owned UUK HUD callback, no audio-capture enablement or gameplay pointer theft; independent positions/scales with draft-based drag/keyboard editor and explicit Apply/Cancel.
- UI library: reusable renderer-independent marquee model and native Component/Font drawing; reduced-motion, pause and measurement reset semantics.

## Remaining parity work (not implemented by this increment)

- MP's complete version-specific island visual presets and its precise Skija optical rendering.
- All download/Automix source integrations, priorities and preemption.
- MP fullscreen/OS desktop lyric presentations, world-space 3D lyrics, all karaoke styles and spectrum presets.
- Complete draggable/resizable multi-widget MP HUD editor parity.
- Full MP widget library equivalence: animated SVG, native Skija image pipeline, Brand/Lucide icon sets and external UI integrations.
- MP business services (DSP, Automix, separated stems, provider-specific playback) remain separate from rendering.

## Provenance

New classes are independent native implementations of interaction concepts, using existing SHBUI drawing, lifecycle, typography, lyrics and music services. They do not copy bundled Apple SF/PingFang fonts, MP JARs, branded assets or its renderer/native downloads. Existing Apache attribution is retained for earlier adaptations; an upstream repository's license does not substitute for third-party asset licenses.

## Verification boundary

Forge 1.20.1 and NeoForge 1.21.1 are the target platforms. Actual test outcomes and game screenshots must be reported separately after final integration; this document does not assert test completion or MP runtime parity. Experimental 26.x is not claimed.

## Automated verification for this increment

- Forge 1.20.1: 1,491 tests (4 skipped), 506 kernel tests; build and library artifact verification passed.
- NeoForge 1.21.1: 1,503 tests (4 skipped), 506 kernel tests; 13 isolated script-worker tests; build and library artifact verification passed.
- Review regressions fixed: module core/trust mismatch, hidden compact island at small sizes, differing editor/runtime bounds, zero-scroll resizing, notification visibility before rendering.
- A real NeoForge 1.21.1 client verified settings/editor rendering, HUD dragging, wheel resizing and Cancel restoration. This caught a post-draw Screen background blur defect; both editor screens were fixed and both platform suites were rerun. The corrected HUD preview is clear. No real music provider was connected, so playback, real album covers and synchronized lyric delivery are not claimed as live-validated.
