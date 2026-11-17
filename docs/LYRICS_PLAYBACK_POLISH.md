# Playback-driven lyric presentation

The supplied four reference screenshots inform the content hierarchy: generous cover/lyric columns, large primary lyrics, subdued surrounding lines, and quiet transport controls. This implementation does not ship the reference artwork, song lyrics, platform branding or proprietary fonts.

## Timing contract

The online session's snapshot reads the audio output position. UI animation must not synthesize elapsed song time. Word highlighting is available only where the source carries meaningful timed word units; ordinary LRC provides line timing, not inferred equal-width syllables. The existing parser also accepts YRC and Enhanced LRC. Existing timeline normalization may repair missing word spans, so this is not a claim that all upstream timings are exact measurements.

The reusable lyric control is independent of a provider and does not own audio or credentials. The online adapter must retain actual translated text and timed word data instead of discarding them in a line-only projection.

## Verification boundary

`/shbetterlyricsdemo` is a clearly labeled silent harness containing original sample text. Its independent demonstration clock is for repeatable rendering/interaction QA only; it is not connected to an account or a real audio stream, and screenshots from this harness must be described as examples, not actual song playback.

Final automated and native validation results will be recorded after implementation is frozen.

## Results (2026-11-17)

Final implementation revision `37094b7`:
- Forge 1.20.1: 1,680 tests, zero failures/errors, five skipped; 538 kernel tests passed.
- NeoForge 1.21.1: 1,692 tests, zero failures/errors, five skipped; 538 kernel tests passed.
- Native Neo 1.21.1 ran successfully in the isolated QA world and exited normally. The explicit silent demo showed automatic active-line changes, pause retained at 10 seconds, manual scroll without changing time, follow restoring the active row, and click seeking to 13 seconds before continuing. Two F2 screenshots were saved.
- Native compact-window resizing did not take effect in this run, so small-screen coverage remains automated rather than a claimed native pass. No real streaming-song/audio synchronization was listened to or measured in this run.
- Word units crossfade as complete words using their supplied start/end. This is not a per-character sweeping mask. Plain line-timed LRC remains line-highlighted. Translation and speaker metadata are preserved but translation/speaker display and switching are not implemented in this stage.
- The demo uses the current configured Minecraft font. It is not a preview of the separate custom-font work and does not claim proprietary Apple typography.

## Web-informed sweep follow-up

The previous whole-word brightness change was not a progressive glyph sweep. This follow-up specifically targets that missing effect rather than renaming it.

References inspected:
- https://github.com/amll-dev/applemusic-like-lyrics/blob/main/packages/core/src/lyric-player/dom/lyric-line.ts
- https://github.com/amll-dev/applemusic-like-lyrics/blob/main/packages/docs/src/content/docs/en/guides/component/sequence.md
- https://github.com/amll-dev/applemusic-like-lyrics/blob/main/LICENSE

The useful Web mechanism is a moving reveal mask parameterized by a word's start/end and driven by media time; animation frame delta is a separate input for presentation easing. AMLL is AGPL-3.0. This implementation is original Java/UUK drawing and does not incorporate its code, assets or dependencies. The document links identify conceptual research, not a claim of porting that library.

### Follow-up results, revision c8ff136

This stage supersedes the earlier whole-word-brightness limitation: a bright copy of the unchanged glyph sequence is revealed by a moving physical clip within each provider-timed word. Wrapped fragments consume one combined measured advance budget. Completed adjacent masks are coalesced. This is a hard edge, not a feathered/glowing mask. It interpolates geometry within source word timings; it does not invent per-character audio timestamps. RTL/mixed bidi and reduced-motion modes deliberately use instantaneous word emphasis instead; line-only LRC is still line-highlighted.

- Forge: 1,684 tests, zero failures/errors, five skipped; 538 kernel tests passed.
- Neo: 1,696 tests, zero failures/errors, five skipped; 538 kernel tests passed.
- Neo native silent example recorded for 65 seconds at 1180×812 / 30fps, H.264 without audio. Video full decode passed. Actual inspected video frames at 3.5 and 4.1 seconds show different spatial reveal positions inside the same word. Pause at five seconds held the front stationary; restarting at zero reset the mask. Client/world exited normally.
- Saved a mid-word frame extracted from that native video and an F2 paused screenshot. These are explicit original-text silent examples, not evidence of a real music service/audio session. Native RTL/reduced-motion/compact rendering was not exercised in this recording; corresponding behavior is covered by automated policy/layout tests and remains a native validation boundary.

## Reference-driven lift and future-line softness

Two further supplied references show small local upward emphasis on illuminated glyphs and a heavier face, while the current line's unsung gray words remain sharp. The intended boundary is future, non-current lines only: past text must not be mistaken for upcoming text; hover/focus and accessibility modes must remain readable. Font advances and line wrapping remain fixed while presentation changes. The existing scoped semibold face supplies weight; no proprietary font is added.

Implementation and final verification details follow after validation.

The renderer now keeps the scoped semibold face and raises timed illuminated spans by at most 0.45 GUI units, with at most 0.55 GUI units of right-edge vertical shear. X advances stay fixed. Pause and seek therefore derive geometry solely from the supplied media timestamp, rather than accumulating a separate animation clock. Future non-current rows use a bounded nine-sample spatial softness approximation (radius <= 0.8 GUI units); this is source-over sampling, not a Gaussian framebuffer blur. Current unsung glyphs and past rows remain sharp. Browsing, hover/focus, reduced motion and high contrast retain crisp text; the latter accessibility modes also disable lift/shear.

Native inspection caught insufficient contrast in the current row's pending mask: distance-zero row color approached the illuminated color. Revision 7786db0 fixes that mask to the existing contrast-adjusted secondary color, including reduced-motion/bidi fallback, while preserving past/future row colors. A regression checks the current/non-current policy independently.

Final full regression on 7786db0: Forge 1.20.1 ran 1,689 tests and NeoForge 1.21.1 ran 1,701, both zero failures/errors and five skips; each loader's 538 kernel tests passed. A Neo-specific unavailable PoseStack convenience API was caught and replaced with the shared underlying GUI matrix API before these final runs.

Final Neo native verification: 45-second 1180×812 / 30fps H.264 screen recording (2,624,924 bytes, no audio), full decode passed. Inspected extracted frames show a partial spatial reveal within the first timed line, with sharp pending gray text and softened future rows. The F2 capture at 16 seconds shows the current sharp emphasis, previous sharp row and future soft rows together. Pause holds the timestamp and emphasis; rewind resets it, and replay resumes. The example is explicitly labelled original silent demonstration, not a real streaming/music account session. World and client exited normally. Compact, RTL and accessibility modes were not exercised in this native run; their tests remain automated evidence only. Very small GUI scales and bitmap fallback fonts can still show rough glyph edges, so this is not a claim of Apple font/rendering parity.
