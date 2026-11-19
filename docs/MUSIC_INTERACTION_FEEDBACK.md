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
revoke authorization already accepted on the phone. The first stage did not carry detached read outcomes into the island; the second stage below adds reads only. Detached write and QR outcomes remain a follow-up.

Follow-up requires session-owned operation identities, account-generation binding,
explicit cancellation versus unknown-write outcomes, durable terminal snapshots,
and separate view subscriptions. Migrating writes/QR polling to that owner is an
explicit next phase, not functionality claimed by this PR.

## Session read operations (second stage)

Built-in player sessions own searches, account playlist lists, playlist tracks and liked-track reads. Leaving their page, opening immersive lyrics, switching source, or closing a borrowed player view detaches observation without stopping the session job. A new read detaches the previous one. Explicit Cancel asks the owner to cancel; this does not prove a remote server stopped processing. Third-party players without this internal capability retain their original request lifetime semantics.

The small History control beside Search opens Background tasks, a secondary, paginated recent-task list. It shows only operation category and state, with provider in its tooltip, never search text, account names or song titles. Selecting an entry explicitly reattaches and restores its result page; no automatic page navigation or stale completion takeover. Search text and playlist identity exist only as private, in-memory restoration metadata, not notification payloads or persistent history. The registry is bounded to 32 total entries, with ten-minute retention for completed entries; a full set of running jobs rejects new work rather than growing unbounded. Logout/account changes invalidate handles, and the view checks validity before applying any observed result. Closing the whole session clears jobs. QR login, account-profile reads and like mutations retain their existing foreground lifetime; they are not advertised as background reads.

Regression coverage includes real session/real view detach → complete → reopen, explicit cancellation, and credential invalidation preventing restore. These tests use controlled provider results, not live account credentials or remote music services.

The island uses a separate read-operation channel, never a fake playback frame. Detached running and terminal states replace only their own notices with a fresh visible budget; no query, title or account identity is copied. Foreground-consumed results do not produce a duplicate background notice. Sampling while disabled consumes events, so enabling notices does not replay old work; the recent-task page remains available. Native notification capacity defers pending notices while their source record remains valid; the legacy island uses non-evicting admission and retries while full or hidden. Later higher-priority notices can still preempt admitted notices under the existing island policy, without deleting task history. Already-displayed results remain authorized after ordinary history expiry/eviction; account changes and session close invalidate them. Ordinary HUD visibility rules still apply, and history is session-local, not a persistent inbox.
