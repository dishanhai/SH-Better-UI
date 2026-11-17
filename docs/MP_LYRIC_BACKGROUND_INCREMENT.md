# MP-inspired lyric focus and artwork fluid background

Original SH native implementation, visually informed by MuoniumPlayer at
`9b4cc7e7bd143315da716075a6d024a0d4c0c81b`. No upstream font, shader or artwork copied.

## Connected changes

- The existing online music lyric page uses a critically damped focus scroll, with
  bounded frame time. Initial display and large seeks snap; ordinary line advances
  retain velocity. Manual scrolling responds immediately and pauses follow for five
  seconds. The existing “follow lyrics” action returns to playback. Keyboard focus
  keeps seek targets stationary. Reduced motion skips the animation.
- Current lyric text is 1.12x native font size; peripheral lines are modestly smaller.
  Width is measured in the scaled coordinate space and existing real word timing,
  click-to-seek, narrator and high-contrast behavior remain intact. No synthetic
  spectrum or duplicate blurred font pass is added.
- The Muonium fluid material gains `followArtwork`, **off by default**. When enabled,
  it reads only artwork already published by the **system media provider**. It does
  not start capture, sign in, fetch URLs or enable unrelated dynamic theme settings.
  This is not yet connected to the independent online player's URL artwork cache.
- A host-owned background worker decodes only the existing bounded PNG format
  (max 240x240, 256 KiB); palette sampling is at most 64x64. Jobs are cancelled and
  stale completions rejected on change/close. No image upload or decode occurs on
  the render thread. Missing/invalid/unsupported artwork falls back to configured
  theme/manual colors rather than invented cover colors.
- Colors interpolate in OKLab over 650 ms, preserving material alpha. Interruptions
  start from the currently displayed colors; reduced motion applies immediately.
  High contrast/hidden/zero-opacity rendering releases the worker.

## Boundaries

This is not an import of MP's ten lyric renderers, world lyrics, Skia/Skija or Folia
shader stack. Actual cover colors require a media provider exposing valid artwork.
The fluid renderer remains SH's existing one-pass shader with its tint fallback.
