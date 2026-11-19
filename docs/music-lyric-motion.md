# Native music lyric motion

The full lyric viewport follows playback with a critically damped scroll. Normal line changes preserve velocity; a new track, discontinuous seek, first render and reduced motion do not animate through stale lyrics. Browsing keeps its own offset until Follow resumes. The blur and ink hierarchy both use distance from the on-screen reading anchor, including older lines above it. Edge opacity prevents partly clipped text from appearing abruptly. Soft focus eases out of an incoming timed row while its foreground resolves.

Timed words use the provider's start and end times. Bold wrapped glyphs are measured once; a feathered highlight travels across their advances, maintaining the same width budget through wrapping. A restrained upward tilt returns to baseline before the word ends. Untimed lyrics stay line-level; right-to-left or reduced-motion paths do not simulate a left-to-right sweep. High contrast does not blur or dim the reading text.

Design references, independently implemented for Minecraft's Java renderers (no copied implementation or bundled web dependency):
- [Apple Music-like Lyrics: DOM lyric line](https://github.com/amll-dev/applemusic-like-lyrics/blob/main/packages/core/src/lyric-player/dom/lyric-line.ts), for soft highlight transitions, measured word masks and spring-driven transforms.
- [KaraokeText](https://github.com/WillSuo-Github/KaraokeText), for feathered glyph emphasis and restrained rise-and-release.

Long, explicitly timed gaps show a three-dot instrumental indicator; ordinary line-only LRC does not invent gaps.

The five-tap soft focus is a bounded native approximation, not an offscreen Gaussian or a pixel-identical reproduction of Apple Music. Compact/HUD hosts remain crisp. Verify Chinese and wrapped English at different GUI scales, seeking in both directions, pause/resume, quickly changing lyrics, scrolling, Follow, and accessibility modes.
