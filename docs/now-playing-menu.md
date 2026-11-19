# Now Playing actions

The immersive player has a native `…` menu. It uses grouped quick actions and
search destinations, anchored to the header and bounded by the player viewport.

- Like/unlike uses the current provider's existing authenticated operation and
  confirmed state; it is disabled without support/login or while a write is pending.
- Queue stays in the immersive stage when space permits, otherwise opens the queue.
- Playlists opens the current track provider's existing account library.
- Album/artist actions explicitly perform a search, not a fabricated detail route.
- No download, Apple radio, credits, lyrics sharing, or synthetic share URLs are added.

The menu binds to the track that opened it. A track change, relayout, screen removal,
or window deactivation invalidates it. Arrow keys, Home/End, Enter and Space operate
the menu; Escape dismisses. Tab dismisses and returns to native navigation. Pointer
activation occurs on release within the same action. Disabled actions are skipped.

1.21.1/Forge uses the existing UUK local-modal host. 26.1.2 uses an extractor-specific
foreground panel and first-registered input shield, including wheel consumption.
The 26.x entry is only exposed by the standalone player, whose foreground pass is
guaranteed; arbitrary embedded hosts do not receive an invisible input shield.
Reduced-motion and high-contrast preferences are respected. At the smallest supported
208×240 viewport, the trigger does not overlap the back or lyric-mode buttons and
all five actions fit without scrolling.

Focused tests cover release activation, disabled actions, keyboard focus changes,
cancellation, track invalidation, and the smallest header geometry. This is not a
claim of in-game optical or full Gradle validation.

When the active output supports native PCM equalization, a separate Equalizer list shows the current preset and applies the selected one. The companion web player and outputs without this capability do not expose the entry.

On a supported native QQ track, **QQ Sound Effects** opens a compact two-column
selector. It marks and focuses the current preset, including Off. Up/Down
move along the current column, and Left/Right move through the focus order.
The Equalizer remains separate because it can also affect other sources.
