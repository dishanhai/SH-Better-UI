<!-- Author: dishanhai -->
# Explicit local queue checkpoints

The built-in music service exposes `OnlineMusicQueue.supportsCheckpoints()` and
explicit `saveCheckpoint`, `restoreCheckpoint`, and `deleteCheckpoint` operations.
Custom players remain compatible and advertise false unless implemented.

Saving is opt-in for each call. Opening, closing, playing, editing or logging into
a player does not save or automatically restore a queue. The single local file
is `config/sh_better_ui/music-queue.bin`; it is separate from account credentials.
The latest successful explicit save replaces that local checkpoint across sessions.

Source names are 1–40 ASCII letters/digits/underscore/hyphen; track/media IDs
are 1–40 ASCII letters/digits. These match the supported built-in provider IDs.
Only platform source/track/media IDs, title, artist, album, duration and playback
mode are stored. Artwork URLs, resolved stream URLs, credentials/cookies, audio
bytes, login identity and playback history are not fields in the format. Display
metadata is plaintext local personal data, not encrypted account storage. Only use
trusted providers; a provider controls its own track labels. Do not commit or sync
the checkpoint as a public project artifact. Delete removes the saved file, not
the active queue or credentials.

Restore replaces the queue, clears undo and selection, resets navigation and
restores the mode. It does not select a track, resume audio, authenticate, resolve
streams or download anything. Existing audible playback continues detached until
an explicit queue selection. Unsupported sources, corrupt files or unsupported
format versions reject the entire restore before changing the active queue.
Playback after selection still uses the provider's ordinary permissions and URLs.
Restored entries intentionally have no artwork URL; cover metadata must be
refreshed by a caller before covers can be shown again.

These explicit methods perform local file I/O synchronously; front-end adapters
should invoke them off the render thread and report completion/errors. File I/O
does not hold the player lock. If playback/queue state changes during loading,
restore rejects the stale result instead of overwriting the newer state. Front-end
adapters must serialize/await save and delete commands; concurrent explicit file
operations have no ordering guarantee and a pending save can otherwise recreate
a just-deleted checkpoint.

The bounded, versioned file is written through a temporary replacement. A rejected
save leaves the previous checkpoint intact. Maximum 1000 tracks; display fields
are capped at 1024 characters. No automatic expiry or cloud upload is introduced.

This independent API/backend slice is based on main, not #60/#62. It does not yet
add front-end checkpoint controls, automatic resume, playback-time seeking or
saved-queue management. Tests use fake sources, not real accounts or audio devices.
