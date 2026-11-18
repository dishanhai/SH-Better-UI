# Native 26.1.2 music activity

The native module observes `ShBetterUiApi.music().getFrame()`, including the existing online foreground lease and system metadata fallback. It does not create a player, restart playback, fetch artwork, or invent audio bands. Its scope-owned activity and optional task notifications use the same `LiveSurfaceRuntime` as other applications; there is no parallel music HUD arbiter.

## Host integration

Construct `NativeMusicActivity(persistent, notificationsEnabled, activityAdmission, notificationAdmission, openOnlineMusicPage)`, add `candidate()` to normal module discovery, and call `advance()` during UI-thread MEASURE before the activity runtime advances. Stop is owned by normal module lifecycle. The notification predicate reflects the current global notification policy. Admission predicates compare the runtime's live counts to its current limits on the same UI thread. A full activity queue delays a new music publication; a full notification queue skips optional notices without tearing down existing music. The module requests only the global configuration permission, never critical priority or independent input/render ownership.

The existing `MusicOverlaySettings.KEY` is reused and optionally persisted using the existing JSON codec. Island and task notices remain opt-in, both false by default. `settings()` and `settings(value)` expose the normal configuration transaction path. Global activity presentation policy still controls placement, available shapes and visibility. The legacy independent HUD, ambient clock, custom island coordinates and style-specific shell renderers are not migrated by this module.

## Content and lifetime

* Title and artist derive from the observed track. Detail becomes the current timed lyric only while that line is active; gaps retain artist text.
* Progress uses the same immutable session clock and duration. Missing position or duration means no progress, not a fabricated clock.
* Playing tracks are eligible; paused tracks remain visible for 30 seconds, then withdraw. Stopped, absent, unavailable or closed sources withdraw immediately. Track/session/source changes replace the publication and cancel old notices.
* Dismissed music remains dismissed until a new track/source or disable/re-enable cycle, rather than republishing every frame.
* Online frames expose an open-music-page action only. The callback rechecks the exact provider/session/track identity, including a change between click and queued dispatch. System metadata is read-only. Transport and per-word rendering are separate capabilities, not claimed by this projection.
* Task notices map the existing loading/provider observations to the unified notification queue, with one optional notice per second to coalesce bursts. They obey the shared service's capacity and admission policy.

Tests use genuine host-issued contexts, registered services, configuration transactions and the activity scheduler. No Minecraft client or playback network is needed to test projection, lifecycle and stale actions.

## Native presentation and controls

The control-panel Tools section opens the music-island settings page. Its opt-in
switches use the actual persistent configuration, not a preview-only Boolean.
The native HUD uses the unified spring transition controller for geometry and
content crossfades, honoring no-motion/reduced-motion settings. Compact mode is
a single small pill with title, current lyric/artist and real progress. The
shell is painted once while content layers crossfade, avoiding transparency
flashes. New HUD reservations snap an obstructed transition to its safe target;
opening a menu or losing focus suspends and clears the visual state.

No separate always-on HUD, draggable global transport controls, artwork extraction,
audio spectrum or word-level karaoke rendering is claimed in this increment.

## Build verification

Full NeoForge26.1.2 Java25/Gradle9.2.1 build and library-JAR checks pass.
Platform tests: 545 total, zero failures/errors, five existing skips.
Shared kernel tests: 541, zero failures/errors/skips. This includes real kernel
module/config/admission tests, native settings tests, spring interruption and
reserved-area tests, rich-document layout/font/scroll tests. Independent review
corrected crossfade shell opacity before publication.

Actual Dot desktop runClient was attempted; official 26.1.2 asset index30 is
being populated and verified. No successful gameplay screenshot, hardware input,
live-account playback or audible output acceptance is claimed at publication.
This is stacked on PR #42; the complete legacy island/transport and rich-media
workbench remain outside this increment.
