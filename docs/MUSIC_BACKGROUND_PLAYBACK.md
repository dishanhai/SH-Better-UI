# Background playback lifecycle

The ordinary online music screen now borrows a player owned by the kernel's
`OnlineMusicService`. Leaving the screen (Back, Escape, another screen or world
navigation) disposes presentation resources and cancels view-local requests,
not the current audio output. Reopening the screen borrows the same player and
can pause, resume or explicitly stop it. Kernel shutdown closes the player via
`BuiltinService`'s existing scope tracking.

`open()`, `view()` and `view(player)` retain their independent-session ownership
contract for developer previews and embedding. `backgroundView()` is an additive
entry point; the built-in service provides persistence, while third-party service
implementations can retain their old behavior via the default method.

This fixes a confirmed lifecycle defect: `suspend()` used to pause unconditionally,
and screen removal called `close()` on its owned player. It does not change provider
licensing, stream resolution or make unavailable tracks playable. It does not keep
audio running after Minecraft exits. Queue sequencing remains view-owned; this
change preserves current-track playback, not unattended playlist auto-advance.

Validation: lifecycle regressions cover borrowed view suspension/removal, retained
independent preview semantics, reentry, explicit player closure and kernel-service
shutdown. Real audible output must be verified with a playable track separately.

## Verification record

- Rebased onto main `3eb2a2f`; retained the Apple segmented-control cleanup added
  since the initial patch. No UI design or rich-text changes are reverted.
- The opt-in `BackgroundMusicAudioTest` was attempted with a locally generated
  20-second MP3. It could not enter PLAYING; a separate JavaSound probe returned
  zero audio mixers. This is **not** successful audible playback validation.
- Run hardware validation on a client with JavaSound output by setting
  `SHBUI_BACKGROUND_TEST_MP3` to an authorized/generated MP3 of at least ten seconds.
  The test checks advancing PCM position after view removal, pause/resume after
  reentry and explicit stop. It does not substitute for in-game listening.
- Final rebased-source regression: Forge 1.20.1 and NeoForge 1.21.1 each compiled
  and ran 183 music backend/view tests: 178 passed, zero failures/errors, five
  conditional skips (four existing live-service cases plus the opt-in hardware
  audio test). This is focused music regression, not the full repository suite.
