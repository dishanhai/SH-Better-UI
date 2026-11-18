# Music interaction feedback coverage

The frontend retains native press/release, focus, selected state, scrolling and
seek preview behavior. A single bounded, UI-owned acknowledgement replaces the
previous acknowledgement instead of accumulating toast messages. **Requested**
means the action was dispatched, not that a provider committed it. Known async
results replace that acknowledgement only when the existing current request is
consumed. Cancelled/replaced requests are detached and cannot report later results.

The ordinary player uses its transport status line; compact stacked transport
temporarily uses the metadata title line (the complete track remains in its tooltip). Immersive playback uses the space between its
time labels plus the play control tooltip. Playback failure/loading/preview
information takes priority over ordinary feedback. Text remains without motion;
the existing transport accent receives a short pulse only when motion is allowed.
Tooltips expose recent context, not a promise of automatic spoken announcements.

| Event | Feedback | Boundary |
| --- | --- | --- |
| Text/transport buttons, pointer or keyboard activation | Current label acknowledgement, existing pressed animation, short transport pulse | No claim of remote success |
| Search/page request | Searching, completion/empty/failure; safe synchronous failure | Suggestions retain dropdown update; no noisy toast for each keystroke |
| Search/library cancel | Cancelled | Read requests only |
| Playlists/playlist/liked request | Loading and actual result/empty/failure | Existing account gate remains |
| Like/unlike | Updating then actual completion or classified failure | Cancelling only stops waiting: refresh to confirm provider state |
| Account query | Loading and actual completion/failure | No account identity/cookie in new messages |
| Import credentials, save preference, sign out | Existing sanitized result keys replace acknowledgement | No clipboard or exception text is exposed |
| QR login | Actual creating/waiting/scanned/confirmed/expired/failed state changes, not every frame | Existing QR cancellation and account detail remain |
| Queue edit, move, remove, clear, undo, select | Current action acknowledgement plus real changed rows/selection/button availability | Not a server write or saved cloud playlist |
| Source/group/navigation | Selected state and request/action feedback | Existing focus and request cancellation remain |
| Play/pause/stop/previous/next | Request acknowledgement plus real player state | Not proof of audible device output |
| Seek/volume commit | Formatted target acknowledgement and actual slider/player state; safe failure | Drag preview is existing control behavior, no fake progress |
| Scroll, lyric follow, pointer hover/focus | Existing viewport, follow/selected/focus and control animations | Not every low-level motion gets a new text message |
| Suspend/close/reopen | Feedback is cleared | Playback ownership unchanged |

This is not a claim that every control on every preferences/HUD page has been
redesigned. Immersive extremely narrow layouts may ellipsize the transient line;
its control tooltip remains available. Live service success, audio output and
hardware frame pacing require separate runtime validation.

## Background notice scope and follow-up

The island observes real playback buffering/ready/failure and existing lyric tasks.
A failed online attempt withdraws its old track, lyrics and controls. Its notice
may take display priority over a system song for at most five seconds; then the
online lease is withdrawn and the system frame is available again. No system
playback command is sent. Notices respect the island/task-notice switches and
normal HUD visibility rules; this is not a durable notification inbox.

Search/library read cancellation requests interruption and detaches stale UI
results, not a guarantee that a remote server stopped. Cancelling a like/unlike
write only stops waiting: the provider may already have applied it, so the UI says
to refresh rather than claiming undo. QR closure stops local polling; it does not
revoke authorization already accepted on the phone. This phase does not carry
these detached operations' terminal results into the island.

Follow-up requires session-owned operation identities, account-generation binding,
explicit cancellation versus unknown-write outcomes, durable terminal snapshots,
and separate view subscriptions. Migrating writes/QR polling to that owner is an
explicit next phase, not functionality claimed by this PR.
