<!-- Author: dishanhai -->
# Non-interrupting online queue edits

`OnlineMusicPlayer.queue()` exposes `supportsEditing()`, `append(List<OnlineTrack>)`
and `move(from, to)`. Third-party queue implementations remain source compatible:
the capability defaults to false and unsupported edits throw explicitly.

The built-in session validates all appended platform IDs before committing any
change. Append does not resolve a URL, download audio or start playback. An empty
queue remains unselected until the caller explicitly selects an entry. Move uses
the final destination index and preserves the exact audible entry, including
multiple equal songs. Invalid move indices and empty append are no-ops and retain
the previous undo. No implicit pagination, deduplication or provider access occurs.

Both edits keep paused/loading/playing output intact. Existing automatic queue
progress uses the edited successor on natural completion. An ended or detached
queue is not restarted. Playback mode is retained; an edit resets shuffle history,
matching removal behavior. Immediate undo restores that history. After playback
has advanced, undo maps entry positions back to the pre-edit queue, not song
value equality. If an appended entry is currently playing and undo removes it,
audio continues but the queue detaches rather than attaching a different equal song.

PR #60 introduces the backend API; the follow-up controls are described below.
Drag-and-drop is not included.
Validation uses deterministic device/provider doubles and checks all three
supported loaders. It does not establish live account access or real audio-device
playback. PR #47's session cache and #48's mode UI remain separate work.

MuoniumPlayer's queue-reorder identity tests informed the duplicate-entry and stale
selection review; this implementation does not copy MP source. Reference:
https://github.com/zihe114514/MuoniumPlayer-multiversion

## Queue page controls (follow-up)

For players advertising `supportsEditing()`, the queue page exposes per-entry
up/down buttons and an add-current-track button in the header. Adding does not
replace the queue or start playback; duplicates are intentional. Edge moves are
disabled. Existing undo applies to these edits. Unsupported legacy queues retain
the prior layout without nonfunctional editing controls.

The controls support keyboard activation and named narration/tooltips, cancel
armed interactions when the visible queue changes, keep keyboard focus on the
moved entry (scrolling it into view when needed), and hide in the immersive
player. This slice does not add drag-and-drop, search-result multi-selection,
queue persistence, or real-account/device validation.
