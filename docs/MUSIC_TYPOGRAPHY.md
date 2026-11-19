# Scoped music typography

SH Music's player, lyrics, music HUD and Dynamic Island use `MusicTypography`
(`sh_better_ui:music_ui` / `music_ui_semibold`). This does not replace Minecraft's
default font or the existing `mp_ui` font used by other SHBUI panels.

The primary font is **SH Music Sans**, a renamed TrueType subset of IBM Plex Sans
SC (medium / semibold), licensed under SIL OFL 1.1. Sources, modifications and
checksums are in `META-INF/third-party/ibm-plex/SOURCE`; the license is beside it.
Existing SH Sans UI (Noto Sans SC derivative) supplies missing CJK glyphs,
including 青 and traditional 長 and 頁; Minecraft's font is the final fallback. This
is not a claim that every Chinese character comes from Plex. No Apple font is
included.

Explicit component fonts retain priority. Enabled SHBUI user-font packs retain
priority, including packs created before the separate music IDs existed; the
music resolver uses their existing `mp_ui` definitions without rewriting files.
Newly generated packs also expose the music IDs. Resource packs can override the
music IDs directly.

Text is styled before measuring, wrapping, clipping or ellipsizing. Scoped line
height is shared by drawing and marquee scissors. The music-only bundled providers
use a three-pixel baseline shift; user-provided font baselines are not rewritten.
The compact native HUD draws complete lyric rows rather than cropped lower halves;
the full player keeps its usual scrolling behavior. Legacy HUD/Island word-gradient
lyrics resolve glyphs from the same selected font rather than the default font.
The scoped lyrics path uses that font's baked glyphs; it does not reuse a global
SDF atlas belonging to a different typeface, and therefore does not claim the
old SDF-specific glow/stroke effects on this path. Existing non-scoped lyric API
calls keep their original behavior.

NeoForge 26's general live-activity renderer applies this font only to music-owned
content. Ownership stays with each crossfade layer, not the newest snapshot;
third-party activities keep the default renderer.
