# UUK music lyric viewport

`kernel.api.music.MusicLyricsModel` is a renderer-independent timing/focus model.
`kernel.api.music.MusicLyricsView` is a public native widget that can be mounted by any
UUK page. It owns no player, artwork texture, network session or GPU resources.

```java
var lyrics = new MusicLyricsView(bounds, unavailableLabel, preferences, time -> {
    // Check lyrics.contentKey() against your current track/revision before seeking.
    requestSeekForCurrentTrack(time);
});
lyrics.setLines(trackRevision, timeline.lines().stream()
    .map(line -> new MusicLyricsModel.Line(line.text(), line.startMs(), line.endMs()))
    .toList());
lyrics.playback(actualPositionMillis, canSeek);
lyrics.scale(2f); // 1..2.5, recomputes wrapped layout
lyrics.palette(primaryText, secondaryText, opaqueBackground);
```

`trackRevision` should change when either the track or its lyric source changes.
The actual callback must reject stale track/revision requests. The widget reports
only actual supplied timestamps, never synthetic per-word progress. A content
change clears old seekability and playback position until the next `playback` call.

Use `Line.unsynchronized(text)` when timestamps are absent. Mixed or unordered
timestamps are displayed as static text in their supplied order and cannot seek.
The host supplies the localized empty/unavailable label through `setMessage`.

Mouse wheel and arrow/Home/End keys browse without seeking. Enter/Space or a click
on a timed line requests a seek. Escape or `follow()` resumes playback following;
`browsing()` lets the host expose a Return to current line action. Manual browsing
never automatically jumps while someone is reading. The current lyric remains
sharp; reduced motion removes animated follow. Long lines wrap and draw only their
visible portions. Size changes use `bounds(...)`, and theme/accessibility changes
use `preferences(...)` and `palette(...)`.

Limits: 4096 lines, 4096 UTF-16 characters per line, 1 MiB combined characters.
Oversized input throws `IllegalArgumentException`; adapters should report a
localized content-limit state rather than silently substituting different lyrics.

After resource/font reload, call `MusicLyricsView.invalidateFontMetrics()` once from
the host's reload callback. Each viewport remeasures lazily at its next draw/input,
not on every frame. Language identity changes also invalidate wrapping. An embedding
host can use `invalidateLayout()` for custom font-provider updates. Resizing preserves
the focused lyric's visibility after wrapping changes.
