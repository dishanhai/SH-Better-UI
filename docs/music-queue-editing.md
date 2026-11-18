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

This is a backend API increment, not new queue drag/drop or add-to-queue UI.
Validation uses deterministic device/provider doubles and checks all three
supported loaders. It does not establish live account access or real audio-device
playback. PR #47's session cache and #48's mode UI remain separate work.

MuoniumPlayer's queue-reorder identity tests informed the duplicate-entry and stale
selection review; this implementation does not copy MP source. Reference:
https://github.com/zihe114514/MuoniumPlayer-multiversion
