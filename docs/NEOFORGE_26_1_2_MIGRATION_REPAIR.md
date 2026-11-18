<!-- Author: dishanhai -->
# NeoForge 26.1.2 migration repair

This increment keeps the Forge 1.20.1 and NeoForge 1.21.1 implementations intact.
It extends the existing native 26.x implementation rather than substituting legacy rendering classes.

## Building

Minecraft 26.1.2 requires Java 25. This project uses ModDevGradle 2.0.148,
NeoForge 26.1.2.126 and the checked-in Gradle 9.2.1 wrapper. The shared kernel
is compiled using JDK 21 with `--release 17`, and its contract tests run on JDK 17.
Install those toolchains before running the complete build.

From the repository root:

```sh
JAVA_HOME=/path/to/jdk25 ./neoforge-1.21.1/gradlew -p neoforge-26.1.2 build \
  -Porg.gradle.java.installations.paths=/path/to/jdk17,/path/to/jdk21,/path/to/jdk25
```

On Windows, set `JAVA_HOME` to your JDK 25 installation and run
`neoforge-26.1.2\build.bat`. The launcher now uses the existing wrapper path,
respects your Java selection, and permits first-run dependency downloads.
It no longer overwrites `TEMP` or requires a machine-specific JDK 21 path.

Official reference: https://neoforged.net/news/26.1release/

## Consumer APIs

`ShBetterUiApi.motion()`, `.interactions()` and `.search()` expose native-safe
state/logic helpers. Animation, drag, countdown and history factories return
independent state for each consumer. They do not imply support for legacy
rendering methods, JEI integration or a global music HUD.

## Scope boundary

The existing 26.x gallery and core are not the complete legacy feature set.
Global activities/music HUD, full rich-text workbench, docking and optional
mod integrations remain separate migration work. See
[the platform capability inventory](THREE_PLATFORM_GAPS_2026-11-15.md).

## Music renderer migration

The shared online music UI had begun referencing newer legacy-platform drawing and
input classes. The 26.x source set now selects native counterparts before shared
sources, including transport/seek/text/segmented controls, lyric and artwork stages,
and the dedicated player screen. Legacy Forge/1.21.1 source files are unchanged.

- Music controls use native mouse/key events and GUI state extraction.
- The lyric view keeps word timing, clipped highlighting, lift and future-line softening.
- Cover loading is no longer an empty implementation: bounded shared artwork
  retrieval feeds a native texture and an ARGB color palette; view closure releases it.
- The cover backdrop is an extracted vertex-color mesh, not immediate-mode drawing.
- The player host retains close veto, return focus, clipped lyric input and resize ownership.
- Bundled licensed SH Sans UI regular/semibold font definitions now accompany the port.

These are platform adaptations, not new music provider authentication or a claim
that all 26.x legacy feature gaps are closed. Live-account playback and real GPU
rendering require separate in-game acceptance.

## Verification for this increment

- Java 25 / Gradle 9.2.1: complete `build` succeeded, including production Kotlin/Java compilation and `verifyLibraryJar`.
- Native/platform tests: 460 total, 0 failures, 5 skipped. Shared kernel tests: 541 total, 0 failures.
- Native headless fixtures explicitly supply a scoped Minecraft client/font/input manager and restore globals; these tests do not start a GPU or audio engine.
- Independent code review found and repaired exact slider cancellation, hidden-input authority synchronization, and stale-screen close/focus issues.
- The library sign-in return recognizes the current `SIGN_IN` state (the same minimal fix needed by the legacy player); it does not merge the separate background-queue PR.
- License notices and the Apple Music local authorization HTML/modules are now included and checked in the 26.x JAR.
- No in-game GPU, real-account music, audible playback, Windows batch execution or 26.2 build acceptance is claimed. The remaining feature inventory is not marked complete.
- Compatibility regression: the shared account authorization-return suite passed on both Forge 1.20.1 and NeoForge 1.21.1 (15 tests each, zero failures/skips), including actual production/test compilation; Forge `reobfJar` also passed. These were targeted suites, not complete legacy-platform builds or in-game tests.

## Main synchronization after first publication

The follow-up merges main `6b714b70b98be653bfa65031a7889784b81468f5`
(the accepted background queue, online island bridge, font and rich-media fixes).
The authorization-return conflict preserves both `AUTHORIZATION_REQUIRED` and
`SIGN_IN`; the test uses the shared `SIGN_IN` constant. The new background queue
and bridge are retained rather than replaced with the older screen-owned queue.
This is a compatibility merge, not an assertion that native 26.x HUD presentation
has already been ported. Native 26.2 compilation was additionally attempted and
still reports older screen access APIs; it remains outside the verified target.

Post-merge validation: full 26.1.2 build and library-JAR validation pass again.
The native queue-view tests now exercise the merged player-owned queue rather
than casting it to the removed screen-owned implementation. Native music frames
use the shared real metadata/lyrics services and online publisher lease; native
GPU artwork/system-output capture/global HUD remain separate migrations.
Forge 1.20.1 and NeoForge 1.21.1 each pass 35 targeted account, background queue
and frame-bridge tests (zero failures/errors/skips), with production/test
compilation and Forge reobfuscation. These are not full old-platform test runs.

## Native Screen Dock spring follow-up

Screen Dock now accepts `CollapseSpec.Curve.SPRING` using the existing shared
interruptible spring implementation. The window privately owns and releases its
track, so existing render/input grants remain sufficient. Timed LINEAR/EASE
behavior and layout persistence are unchanged. Reversal preserves the current
position and velocity; progress is bounded, zero duration is immediate, and
reduced-motion changes settle the current target. Controlled progress remains
rejected by MiniWindowSpec. Screen Dock additionally accepts glass materials through
one shared same-frame backdrop and rounded optical plates with readable opaque
fallbacks. Quality FALLBACK forces opaque rendering; other tiers use the native
fixed shader budget. This does not migrate HUD windows or the full GlassService. See `neoforge-26.1.2/DOCK-INDICATORS.md` for the example and boundary.
