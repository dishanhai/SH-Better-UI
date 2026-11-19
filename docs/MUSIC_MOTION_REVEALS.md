# Music motion: reveal, text and scrolled background

- **Now Playing menus** reveal their contents in a short, clipped pass through
  the existing motion system. Rows keep their layout. The part still hidden does
  not accept pointer presses, and keyboard interaction reveals the controls
  immediately so that focus and narration remain visible.
- **Title and artist** use a gentle scale and opacity reveal on track identity
  changes. It does not reshape individual glyphs, change available text width,
  or replay for a refreshed snapshot of the same track.
- **Timed lyrics** apply a small baseline-anchored ink expansion to active words
  on their own time envelope. No advances, row bounds, or seek coordinates move.
  Browsing and discontinuities don't animate from stale word highlights.
- **Manual scroll** gives the decorative background a bounded vertical lag,
  up to 8 GUI pixels, while text, art and controls keep their normal positions
  and hit targets. This covers the music search/library/queue/lyrics lists and
  the immersive lyric/queue panes. Playback ticks and seek previews do not
  drive the effect. Route changes reset the motion.

Reduce-motion settings keep decorative motion static; high contrast also
disables menu/metadata reveals and decorative scroll parallax. The second
setting does not change the lyric timing or remove readable highlighting.
