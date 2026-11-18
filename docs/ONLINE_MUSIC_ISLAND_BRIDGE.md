# Online music → native island / HUD

The ordinary music screen's kernel-owned session now publishes immutable metadata,
transport and lyric frames to `MusicApi`. No gameplay screen needs to stay open.
Independent developer-preview sessions do not take over the shared HUD.

- A dedicated observer samples the session at 20 Hz, under one session lock; render
  callers only select immutable frames. No network, decoder or player locks run
  from the HUD renderer. A stalled observer expires after one second.
- LOADING, PLAYING and PAUSED have distinct truthful states. IDLE, FAILED, ENDED
  and CLOSED release selection to the existing system provider. The island's
  existing paused visibility timeout remains unchanged.
- Exactly one source is selected. Online sessions keep their own session identity
  across song changes. Kernel shutdown/replacement revokes the publication lease,
  so an old worker cannot republish or clear its successor.
- Lyrics and metadata are sampled together. Preview positions include the stream's
  song-relative offset; duration uses the full song duration. Task notifications
  compare revisions only within the selected provider/session.
- System-output audio features are not attached to online frames. The compact
  playing indicator is not an invented spectrum. No system capture is enabled.
- No credentials, account objects, stream URLs or downloaded audio enter these
  frames. This increment does not fetch online cover art for the HUD; the existing
  original note placeholder is used. No new transport-control capability is claimed.

## Dependencies / scope

Built on main `0c63db9`, independent of open PR #36. No queue implementation from
#36 is included. The bridge observes whichever track the session exposes, so it
also follows #36's future queue advancement without owning another queue.
`MusicApi.addListener` continues to describe system-provider callbacks only;
render consumers should use the frame API for source selection.

## Validation

Final verification results are recorded in the PR. The existing main account-view
regression expects `library.login_required` while the corrected guest UI returns
`library.sign_in`; PR #36 already corrects this separate expectation/flow. This
branch does not duplicate that change.

Native NeoForge 1.21.1 QA renders compact and expanded `LocalMusicIsland` through
the real bridge using **explicitly labelled synthetic metadata**, with no player
account login and no audible audio output. It is a native Screen preview, not a
world HUD overlap/performance or real playback acceptance test. No test fixture
or synthetic track is compiled into the production source set.

Final standalone-source results (Gradle 8.8 / JDK 21): Forge 1.20.1 compiles and
runs 1,718 tests; NeoForge 1.21.1 compiles and runs 1,730. Each has the one
pre-existing account-view failure described above, no errors and five skips.
All seven added bridge/source-transition regressions pass on both loaders.
The native preview uses NeoForge 21.1.251, 1180 × 812, GUI scale 2 and exits normally.
These are not full-green suite claims. No account login or audible playback was tested.
