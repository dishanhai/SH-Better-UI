# Native music lyric motion

The full lyric viewport follows playback with a critically damped scroll. Normal line changes preserve velocity; a new track, discontinuous seek, first render and reduced motion do not animate through stale lyrics. Browsing keeps its own offset until Follow resumes. The ink hierarchy uses distance from the on-screen reading anchor, including older lines above it. Inactive rows fade gradually near the clip edges; the current line and the hovered or keyboard-focused line keep their sharp, full-alpha glyphs.

Timed words use the provider's start and end times. Bold wrapped glyphs are measured once; a feathered highlight travels across their advances, maintaining the same width budget through wrapping. A restrained upward tilt returns to baseline before the word ends. Active words also expand vertically by at most 2.5% about the bottom of the glyph cell, using the same provider-timed envelope. Horizontal advances, wrapping and hit targets remain unchanged. Browsing and discontinuous seek frames keep static geometry; the next frame samples the current timestamp directly rather than replaying a reveal. Untimed lyrics stay line-level; right-to-left or reduced-motion paths do not simulate a left-to-right sweep. High contrast bypasses dimming and edge fading.

Design references, independently implemented for Minecraft's Java renderers (no copied implementation or bundled web dependency):
- [Apple Music-like Lyrics: DOM lyric line](https://github.com/amll-dev/applemusic-like-lyrics/blob/main/packages/core/src/lyric-player/dom/lyric-line.ts), for soft highlight transitions, measured word masks and spring-driven transforms.
- [KaraokeText](https://github.com/WillSuo-Github/KaraokeText), for feathered glyph emphasis and restrained rise-and-release.

Long, explicitly timed gaps show a three-dot instrumental indicator; ordinary line-only LRC does not invent gaps.

Immersive, compact and HUD lyrics stay sharp. The immersive rail uses color and edge opacity rather than drawing displaced copies as faux blur, which produced visible doubled strokes in dense fonts such as Chinese. Both the timed and untimed paths submit just one glyph image per character; popup and backdrop blur are unaffected. Verify Chinese and wrapped English at different GUI scales, seeking in both directions, pause/resume, quickly changing lyrics, scrolling, Follow, and accessibility modes.
