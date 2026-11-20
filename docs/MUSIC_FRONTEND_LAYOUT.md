# Music frontend workspace geometry

`MusicFrontendLayout` is an internal layout snapshot. It does not query the player or mount widgets. Use the same bounds for paint, clipping and pointer/keyboard hit targets.

- Compact (208 logical pixels wide and up): a single scrollable content pane, a reserved mini-player below it, and equal-width Discover, Library, and Search destinations along the bottom. Back and Settings belong in the top utility row, away from the destination bar.
- Desktop (560 or wider): Discover, Library and Search move to a stable sidebar. The mini-player stays below the workspace. A separate queue rail is opt-in from 864 logical pixels and suppressed in Now Playing, Lyrics or Queue to avoid duplicate content.
- The route header and body do not overlap; render wrapping rows and provider details within the body viewport, not above the transport. Secondary routes reuse the body rather than adding tabs.
- The minimum supported frame is 208 × 240. A smaller frame returns empty click targets and should render an unavailable/resize affordance, rather than clipping partially interactive controls. Long labels inside a bounded item belong to the text layout; the geometry guarantees no neighboring widget is covered.

This geometry uses the shared SHBUI bounds model and adds no renderer or dependency. Forge and NeoForge use the same pure source; the NeoForge 26.x music source set includes it without a fork.
