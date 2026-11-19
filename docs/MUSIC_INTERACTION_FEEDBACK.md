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
results, not a guarantee that a remote server stopped. Built-in sessions also
own favorite writes and QR sign-in, as described below. Closing or changing
views detaches observation; it does not reverse a submitted write or revoke
authorization that was already accepted on the phone.

## Session read operations (second stage)

Built-in player sessions own searches, account playlist lists, playlist tracks and liked-track reads. Leaving their page, opening immersive lyrics, switching source, or closing a borrowed player view detaches observation without stopping the session job. A new read detaches the previous one. Explicit Cancel asks the owner to cancel; this does not prove a remote server stopped processing. Third-party players without this internal capability retain their original request lifetime semantics.

The small History control beside Search opens Background tasks, a secondary, paginated recent-task list. It shows only operation category and state, with provider in its tooltip, never search text, account names or song titles. Selecting an entry explicitly reattaches and restores its result page; no automatic page navigation or stale completion takeover. Search text and playlist identity exist only as private, in-memory restoration metadata, not notification payloads or persistent history. The registry is bounded to 32 total entries, with ten-minute retention for completed entries; a full set of running jobs rejects new work rather than growing unbounded. Logout/account changes invalidate handles, and the view checks validity before applying any observed result. Closing the whole session clears jobs. Account-profile reads remain foreground-only. QR sign-in and favorite updates are session-owned operations, not background reads.

Regression coverage includes real session/real view detach → complete → reopen, explicit cancellation, and credential invalidation preventing restore. These tests use controlled provider results, not live account credentials or remote music services.

The island uses a separate operation channel, never a fake playback frame. Detached running and terminal states replace only their own notices with a fresh visible budget; no query, title or account identity is copied. Foreground-consumed results do not produce a duplicate background notice. Sampling while disabled consumes events, so enabling notices does not replay old work; the recent-task page remains available. Native notification capacity defers pending notices while their source record remains valid; the legacy island uses non-evicting admission and retries while full or hidden. Later higher-priority notices can still preempt admitted notices under the existing island policy, without deleting task history. Already-displayed results remain authorized after ordinary history expiry/eviction; account changes and session close invalidate them. Ordinary HUD visibility rules still apply, and history is session-local, not a persistent inbox.

## Provider writes and QR sign-in (third stage)

The built-in player owns like/unlike writes. Closing the panel or changing tabs
only stops observing the request. It does not revoke an already dispatched
write. While the same provider and track have a running write, a repeat press
reports that it is still updating and sends nothing new. The server's confirmed
rejection is distinct from an unconfirmed result. In particular, QQ can apply
a write even if its confirmation read times out. The island reports that result
as unconfirmed; refresh the Likes page to check the current provider state.
The player never automatically retries the write.

QR polling also remains with the built-in player when the panel is dismissed.
Opening the provider's account detail returns to the active QR challenge without
creating a new one. Restarting sign-in cancels the old attempt; replacing or
expiring the provider's account clears its old challenge and results. The island
does not display a QR image or report sign-in success until the session has
applied the credentials and any requested persistence has succeeded.

Notifications and recent-task labels contain only operation type and normalized
state; no track identity, credentials, account data, QR image, or raw provider
error. Write and login history records never re-execute their original action;
the safe navigation shortcuts below open the related workflow instead. These
paths have synthetic provider regression coverage;
they do not imply live account or audio-service verification.


## History shortcuts

The Search toolbar shows History as text where there is room, otherwise as a
named clock button. A record shows its provider, outcome and next action. A
search or library result is restored from memory rather than sent again. A
favorite update opens that provider's Liked Songs through the existing sign-in
gate, even if the write outcome is uncertain; it never retries the mutation.
A sign-in record opens that provider's account settings and can show the
existing QR challenge, without starting one. Pruned or cancelled records
cannot be activated. Pagination controls are disabled at their limits.
Back and Escape return to History after following a record; leaving History
returns focus to the toolbar button. Explicit new navigation or drill-down clears
that temporary return path. Sign-in gates return to the originating provider and
library context first. When a focused history slot is rebound to another record,
its held gesture is cancelled and focus moves to Back instead of silently
activating a different task.

On NeoForge 26, choose **View task** on a visible operation notice in the
live-activity details screen to open History at the matching record. The
world HUD is display-only and does not intercept mouse clicks. This action
does not restore, retry or refresh the operation: use the record’s own action
separately if needed. An expired, replaced or no-longer-background record
cannot be opened from an old notice.

## Open SH Music from the world

The **Open SH Music** shortcut starts unbound, so installing the mod never
takes an existing game or modpack shortcut. Set it in **Options → Controls →
Key Binds → SHBUI / SH Better UI → Open SH Music**. Choose a key that
is not already used for another gameplay action. It opens the player only
from a focused world with no screen or overlay. Holding the key does not
reopen it after closing. Opening is navigation only: the shortcut does not
start a search, play a song, or sign in. The existing **Control Panel → Tools →
SH Music** entry remains available.
