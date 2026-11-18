# MP player backend parity: queue modes (first slice)

Reference inspected: [MuoniumPlayer-multiversion](https://github.com/zihe114514/MuoniumPlayer-multiversion/tree/9b4cc7e7bd143315da716075a6d024a0d4c0c81b), especially
`muonium-common/src/main/java/com/muoniumplayer/core/ncm/music/CloudMusic.java`
(`PlayMode`, `next`, `prev`), `MusicAudioCache.java`, and `AudioPlayer.java`.
This slice is an independent implementation of queue behavior; no MP code or assets copied.

## Actual boundary

| Capability | SHBUI baseline / this slice |
| --- | --- |
| Credentials and accounts | Existing explicit remember-credentials opt-in; account playlists, liked tracks and supported like writes. No new platform authentication in this slice. |
| Background queue | Existing session-owned queue and natural-end watcher; does not depend on screen rendering. |
| Playback order | **Added:** sequential, repeat one, repeat all, shuffle via public backend API. |
| Shuffle | Visits each queue position before refill; no immediate repeat for a multi-entry queue. Previous retraces up to 255 previous transitions; next after previous retraces forward history. Explicit selection, queue edits and mode changes reset navigation history; undo restores the prior order when playback and mode have not changed. Duplicate songs are distinct queue positions. |
| Download/cache | Still resolves and downloads a bounded whole MP3 for each play. No disk audio cache, prefetch or gapless playback. |
| Codecs / DSP | Existing MP3 decoding and PCM volume only. No claimed FLAC/AAC/Opus, FFmpeg transcoding, equalizer, crossfade or Automix parity. |
| Radio / together listening | Not added. No automatic remote playlist pagination. |
| Platform playback rights | Unchanged. Only provider-granted streams; no substitute audio URLs or DRM bypass. |

## API

```java
OnlineMusicQueue queue = player.queue();
// A third-party player may return null or retain the default unsupported mode setter.
queue.mode(OnlineMusicQueue.Mode.REPEAT_ALL);
queue.replace(tracks, selectedIndex); // Starts playback, as before.
```

Mode changes do not issue a play command or resume paused playback. A stopped,
cleared or detached/restored queue is not reattached by changing mode. At natural
EOF, repeat-one replays the current entry; explicit Next instead selects the next
entry and remains at the boundary if none exists. Repeat-all wraps at both ends.
A one-entry shuffle repeats at EOF, but explicit Next is unavailable.

The snapshot now includes mode and mode-aware Previous/Next availability. The
existing three-argument constructor remains available for legacy view-local
queues. The default mode remains sequential. Modes are session-local, not saved
across game restarts. The regular queue header and immersive queue header now expose mode selection.
Click cycles Sequential → Repeat all → Repeat one → Shuffle; the tooltip describes
the next mode. Both controls read the same session-owned state, including after
layout changes or reopening a borrowed view. Unsupported third-party queues show
an unavailable sequential control via `supportsModes()` (default false).

The normal header uses the short mode name to fit compact English labels; the
immersive header and tooltip include the full playback label. English, Simplified
Chinese and Traditional Chinese strings are supplied. Hide/suspend/relayout
cancel armed interactions. This targets the existing Forge 1.20.1 / NeoForge 1.21.1
player views; the separate 26.1.2 native Activity UI is not changed here.

Failure handling remains fail-stop: network, unavailable stream, login and output
failures do not silently skip tracks or loop failures. A repeat begins a fresh
provider resolution, so an expired/revoked stream is not treated as permanent.

## Verification

Focused tests cover ordering boundaries, shuffle cycles/history, repeat EOF,
explicit skip, paused mode changes, background progression without rendering,
and removal/undo/stop detachment. Fake providers and outputs validate state
transitions, not live platform authorization or audible device output. Builds and
full test results are recorded in the PR; this is not evidence of MP feature parity
or a newly verified 26.1.2 runtime player.
