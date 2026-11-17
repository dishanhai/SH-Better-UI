# Session-owned background music queue

## Behavior

The built-in online player now owns the supplied playback collection, current queue index and single-step edit undo. Opening another view of the same background player reuses that queue instead of starting with an empty view-local queue.

A session-owned daemon observes the current audio output every 50 ms while a queue has a following track. Natural `ENDED` advances once without rendering a UI frame. This is not gapless playback: the next permitted stream is resolved and downloaded after the previous track ends. Only the tracks explicitly supplied to the queue are played; this change does not fetch later provider playlist pages, repeat the collection or skip unavailable tracks.

- Pausing, including during download, prevents automatic advancement. The existing resume-at-end behavior replays the current track.
- Stop, credential replacement/sign-out, standalone `play(track)` and closing the session detach automatic queue advancement. Stop and standalone playback retain the queue for an explicit later selection; session close clears it.
- Previous, next and queue selection are atomic with playback under the session lock. A previous output cannot advance a newer selection.
- Removing the audible queue entry or clearing the queue leaves the current audio playing but detaches automatic advancement. Undo restores metadata without reattaching that detached queue.
- Removing a different entry preserves playback association. Undo of that edit preserves automatic advancement if it was still attached.
- Audio/decode/network failures stop rather than silently skipping more songs. Explicit next/select can recover.
- Borrowed view suspend/close do not change a persistent queue. Independent preview ownership still closes its player. Third-party `OnlineMusicPlayer` implementations default to no persistent queue and retain the existing view-local behavior through an adapter.

Both audio generation and an independent queue-watch epoch guard delayed callbacks. Canceling a scheduled task alone is insufficient when its callback has already started waiting for the session lock; clearing/removing and undoing cannot revive such a callback.

## Verification (2026-11-17)

The production session, production adapter and API compile with `javac --release 17`. Focused tests use the real session implementation and a controlled audio-output boundary, not replacement session code. They cover automatic progression without render/snapshot polling, duplicate entries, end of collection, pause/resume, loading cancellation, stale output/download completion, explicit selections, stop, failure, close, credential changes, queue edits/undo, the blocked-callback cancellation race, third-party fallback and persistent adapter reentry. Existing account-session and legacy queue tests run alongside them: all 46 tests passed on both Java 17 and Java 21 (15 background queue, 2 adapter, 18 account-session, 11 legacy queue).

Forge 1.20.1 and NeoForge 1.21.1 Gradle test tasks were both attempted. They stop before compilation because the environment cannot resolve `net.minecraftforge.gradle` / `net.neoforged.moddev` plugins. The focused Java tests are not a full loader integration pass. No new in-game screenshot or real streaming-account test is claimed. The environment exposes zero JavaSound mixers, so audible playback cannot be verified here.

## Main integration verification (2026-11-17)

Merged main `0c63db9` into this branch without changing the Apple mini-player layout or keyboard order. Mini previous/next use the queue's atomic playback commands rather than replaying their result. Updated view integration tests to use the new queue adapter. Also corrected the guest sign-in return path to reload the selected library group after authorization, including the new `library.sign_in` state.

After integration, 49 focused tests (including mini-player layout) pass on Java 17 and Java 21. NeoForge 1.21.1 production Kotlin/Java compilation and the full Gradle suite pass: 1,740 tests, zero failures, five skipped. The script worker's 13 tests and `verifyWorkerJar` pass. These results supersede the earlier NeoForge plugin-resolution limitation; they do not establish live-account or audible-playback validation.
