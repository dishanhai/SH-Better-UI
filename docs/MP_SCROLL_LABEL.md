# 原生滚动标签 / Native scrolling label

`UiMarqueeLabel` is a reusable native-font overflow label inspired by MuoniumPlayer's
`ScrollLabelWidget` / `ScrollText` behavior (reference: `muonium-common`, upstream
commit `9b4cc7e`). This is an independent UUK implementation, not a copied Skija
renderer. It contains no MP/Apple font files or native dependencies.

## API

- Keep one `com.dishanhai.sh_better_ui.client.gui.text.UiMarqueeLabel` per label.
- Call `setText(Component)` when content changes. Component style is retained;
  `text()` returns a defensive copy suitable for narration and tooltips.
- Call `render(graphics, font, x, y, width, color, shadow, nowMillis, paused,
  preferences)` using the host's existing animation clock and effective preferences.
- Default behavior holds the beginning for 2500 ms, then scrolls at 24 logical
  pixels/second with a 24-pixel repeat gap. Configure with a `MarqueeMotion` instance.
- Set `paused=true` on sampled hidden/inactive frames or `reset()` before reopening.
  Resizing or changing text restarts the initial hold. Reduced motion freezes the
  first copy. A host should expose the full text in narration/tooltip even then.
- The host remains responsible for accessible colors, focus and tooltip delivery.
  The label has no click action or automatic tooltip side effects.

`MarqueeMotion` lives in the renderer-independent kernel source set. It returns
`Frame(offset, repeatOffset, overflowing, moving)` for custom renderers; it does
not truncate Unicode strings, alter text layout or perform per-character shaping.
The native adapter uses bounded nested scissor clipping and restores pose/scissor
state with `finally` blocks. Invalid or zero widths suppress motion/drawing.

## Differences and limits

This adds one reusable MP UI-library capability, not the whole MP widget library.
It uses a logical-time constant-speed loop rather than frame-dependent interpolation.
Skija edge-gradient masks and custom font backends are not migrated. Integration
must use the native `Font` measured width, not an estimated string length.

## Validation

`MarqueeMotionTest` covers initial delay, loop continuity, frame-rate independence,
pause, content/viewport changes, reduced motion, fit, invalid geometry, backwards
clock and reset. Native rendering still requires in-game validation at scaled GUI
sizes and within nested scrolling panels.
