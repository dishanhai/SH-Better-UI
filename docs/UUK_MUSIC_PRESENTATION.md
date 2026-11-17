# UUK music presentation controls

This local increment adds original reusable music UI primitives. Apple Music Now Playing and its lyric view inform layout hierarchy, not branding, font assets or unavailable service capabilities.

## Responsibilities

- `MusicSeekModel`: pure interaction state, value previews, identity checks and release/cancel semantics.
- `MusicSeekControl`, `MusicTransportControl`, `MusicControlRenderer`: custom thin-track and vector transport rendering with native keyboard/narration adapters. They do not reuse glass-button/slider chrome.
- `MusicArtworkStage`: continuous artwork-derived background and sharp artwork presentation. Textures are borrowed from the owner; rendering does not download music or own the texture.
- `MusicLyricsModel`, `MusicLyricsView`: real line timing, wrapped text, manual browsing, keyboard selection and time-based seeking. Unknown timing must stay static rather than fabricate word timing.
- `OnlineMusicView`: wires these primitives to the actual player session. Account, search and queue capabilities remain real, separate routes.

## Host contract

The owner supplies truthful availability, the current track identity, playback position and duration. Update static lyric content only when the track or timeline changes. Cancel an in-progress adjustment when hiding/suspending the host. Revalidate track identity before applying a seek. Do not dispose a borrowed artwork texture from a presentation widget. Keep failure states visible and distinguish unavailable playback from a successful stream.

Volume is session-local software gain, not an operating-system volume change. `OnlineMusicPlayer.supportsVolume()` gates whether the UI exposes it. The built-in session applies normalized 0..1 gain to decoded PCM and retains it across output replacement; third-party players without this capability must not show a fake volume slider.

## Accessibility and preferences

Use theme-aware foreground/background contrast and clear keyboard focus. High-contrast mode takes precedence over artwork color. Reduced motion and low-performance modes must retain usable static views. Only actual synchronized line timestamps may enable lyric click-to-seek; no translation, karaoke, AirPlay or Dolby badge is shown without a corresponding capability.

Typography uses the bundled OFL-licensed SH Sans UI regular and semibold derivatives. These are scoped resources, not replacements for Minecraft's global font. Apple SF/PingFang fonts, logos and the owner's reference album images are not bundled.

## References

- https://developer.apple.com/design/human-interface-guidelines/layout
- https://developer.apple.com/design/human-interface-guidelines/typography
- https://developer.apple.com/design/human-interface-guidelines/materials
- https://support.apple.com/en-la/105076
- https://support.apple.com/en-gb/guide/iphone/iphb9bf483aa/26/ios/26

This document describes the implementation contract. It does not claim visual parity or live playback validation until a real client pass is recorded.

## Recorded validation — 2026-11-17

Local implementation head `b2d8e74`, no remote push. Full regression after the reusable UUK controls and integration: Forge 1.20.1 1600 tests (0 failures, 4 skipped) plus 528 kernel tests; NeoForge 1.21.1 1612 tests (0 failures, 4 skipped) plus 528 kernel tests. The final primary-entry/header change was compiled and exercised by 106 targeted tests per loader (0 failures, 2 skipped). These are test-case counts, not assertions.

A real NeoForge 1.21.1 client was run in the isolated creative QA save at 1180×812, GUI scale 2, zh_cn. Verified:

- Classic horizontal control-panel selection survived a full client restart.
- Official NetEase search returned actual Canon results. Missing search artwork was enriched from the official detail endpoint; both selected songs displayed their own covers.
- The new full-viewport artwork/queue and large-lyrics modes use the same transport positions, with no legacy native header. The primary Lyrics sidebar entrance opens the new stage directly.
- Changing the session volume to 50% updated the real session control and remained at 50% after selecting another song. This is UI/state verification, not an audible-volume claim.
- Escape returned to the prior search page with the selected song retained. Native F2 captures were saved for the classic layout, two lyric presentations, and artwork/queue presentation.
- The QA world was saved and the client exited normally.

**Observed limits:** the provider did not return a playable audio URL for the selected recordings. The UI correctly displayed that unavailability, and the real lyric response contained composer metadata and an instrumental notice. No successful audio playback, timed lyric follow, or audible gain is claimed from this run. The desktop also has no usable ALSA output device. Headless regression covers timing, seek cancellation, gain math, reduced motion and small-layout logic; native held-drag/window-deactivation and every GUI scale were not exhaustively exercised.

**Remaining design work:** this stage replaces the now-playing presentation, not every search/account/library control. Those routes still use older row/sidebar/miniplayer chrome and need the same dedicated design pass. Very long queue/song metadata remains bounded but may be abruptly clipped rather than elegantly ellipsized. There is no claim of complete MP business-feature migration or pixel-identical Apple Music parity.

## Library-shell continuation — 2026-11-17

The earlier search/account/sidebar/miniplayer design gap above is now addressed by the shared music shell. Search results, account/provider rows, playlists and queue entries use public `MusicTextControl`; narrow layouts keep real artist/duration metadata on two lines. `MusicInputAppearance` changes only the optional field presentation and styled text metrics, retaining the established editor, undo/redo, selection and platform input path. `MusicTextLayout` keeps styled ellipsized display separate from full tooltip/narration content. The native host now avoids redundant focus-loss/focus-gain on the same widget, which otherwise cancelled a release action on repeated clicks.

Full regression at `51e968d`: Forge 1617 tests and 531 kernel tests; NeoForge 1629 tests and 531 kernel tests. Zero failures/errors, four main-test skips per loader. Later lyric-boundary/follow-overlay corrections through `4b43bbd` were compiled and covered by 186 music/host/layout tests per loader, zero failures/errors and two explicit live-test skips.

Real NeoForge QA of the shell covered 1180×812 and 784×538 client pixels (GUI scale 2: 590×406 and 392×269), plus a brief minimum-height 854×480 window. The window was actually resized; screenshots were not resized mockups. Confirmed real search, artwork retrieval, two-line metadata, source/account navigation, small-screen account scrolling, input undo, queue removal followed by undo (20→19→20), and stable mini-player controls. No account was logged in or credentials imported/saved during QA.

The compact lyric pass exposed and corrected an actual boundary defect: an error message leaves a 42-GUI-pixel lyric viewport, where the old trailing gap plus scroll padding could move every line offscreen. The scroll extent now excludes decorative trailing space, and follow without an active timestamp returns to the first line. Real revalidation confirmed the final instrumental line stays visible at the bottom and keyboard follow restores the first line. A subsequent real mouse pass identified the overlapping follow action being consumed by the lyric widget; the formal host now uses an explicit shared input stack, with a non-overlapping fallback for embedded previews.

Remaining validation limits: provider stream unavailability still prevents claiming audible playback or real timed-song following; signed-in playlist/like mutations and credential persistence were not exercised with a real account. Small windows intentionally ask to enlarge when a complete scannable QR cannot fit, instead of presenting a partial or unusably downscaled code. The large MP business-feature gaps from the migration audit are not implied complete by this visual/UI work.

Final native overlay confirmation at `4b43bbd`: in the same 784×538 window, scrolled the real two-line instrumental response to its end and clicked the visible follow action twice in successive browse/follow cycles. Both clicks restored the first composer line; the follow action disappeared correctly and the lyric content stayed visible. The client then saved the QA world and exited normally. This verifies the mouse input-stack correction in the real game, not just the headless fixture.
