# Native music HUD information card

`LocalMusicHud` is the SHBUI renderer for a music information card and optional lyric area.
It uses `MusicApi` and UUK frame drawing, not reflection into MuoniumPlayer. It does not
bundle MP fonts, Skija, native binaries, or copyrighted cover art.

## Implemented

- One coherent music frame for metadata, artwork and lyrics.
- Fitted artwork or a native music-icon placeholder.
- Title/artist scrolling via `UiMarqueeLabel`; reduced-motion preferences stop scrolling.
- Explicit playback/availability state, bounded timeline and elapsed/duration text.
- Existing SHBUI timed lyric engine with one or two lines, and loading/missing/time-unknown hints.
- Viewport-clamped normalized placement (`MusicHudLayout.anchored`); fractions use remaining
  travel distance so an anchor of 1 remains right/bottom aligned after a viewport resize.
- Compact cards drop sections that cannot fit; all rendering is clipped to the card.

Hosts own activation, configuration, input and UUK mounting. Instantiate one renderer per
visible card and call `suspend()` when hidden to reset transient animation state. A 230×112
GUI-unit area offers metadata, progress and lyric space; 230×56 is a compact information card.

## Scope and provenance

The presentation was compared with MP's information/lyrics HUD provider model at
`muonium-common/.../screens/hud/MuoniumHudProviders.java` (upstream reference `9b4cc7e`).
The native implementation does not copy MP source or resources. MP's world-space lyrics,
full HUD editor, all spectrum styles and player-specific DSP are not implemented by this class.

Geometry tests cover full/compact bounds, viewport resizing, non-finite anchors and unknown
or out-of-range timeline values. Full platform build and runtime validation are performed by
the integration task, not inferred from geometry tests.
