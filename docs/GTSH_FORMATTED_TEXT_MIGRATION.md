<!-- Author: dishanhai -->
# GTSH formatted text migration

Source reference: [GTSH](https://github.com/dishanhai/gtl_shanhai-dishanhai),
`TextFormatParser`, `DShanhaiStyleRegistry`, `DShanhaiTextUtil`, `ShanhaiTextAPI`,
`WobbleFontMixin`, component wrapping and GuideMe support. The migration extends
SHBUI's existing text system rather than installing a second global Font renderer.

## API mapping

| GTSH capability | SHBUI entry point |
| --- | --- |
| `format`, `inline`, explicit `&$palette-` and effect symbols | `UiTextEffects.parse`, `normalize` |
| `fcs`, `styled`, named/body variants | `UiTextEffects.applyNamed(component, name, symbols)` |
| palettes, aliases and dynamic color modes | `TextPalettes` and `UiTextEffects.styleNames()` |
| custom palettes and effects | `registerPalette`, `registerPaletteVariants`, `registerEffect` |
| scoped/script-owned palettes | `registerScopedPalette`, returned cleanup action |
| clock, speed and direction | explicit frame time and `UiTextEffects.Timing` |
| continuous multiline color | `UiFormattedText.colorizeLines` |
| gradient and lyric progress | `UiFormattedText.gradientFiller`, `colorizeLyric` |
| typewriter reveal | `UiFormattedText.reveal` |
| native styled measurement/wrapping | normalize **before** `Font.width`, `Font.split` |

Do not flatten Components to strings: native font, bold, italic, events and
per-segment styles must survive. Unknown markup remains literal. Plain text and
unrecognized insertion values take the unchanged native path. Unlike the source,
SHBUI does not use a global clean-string-to-format cache or cast code points to
UTF-16 `char` values.

## Native renderer coverage

Forge 1.20.1 and NeoForge 1.21.1 already have native Font, wrapping, FTB and GuideMe
adapters. NeoForge 26.1.2/26.2 now use `UiFormattedText` through the extractor's
String and visual-sequence text entry points. The String width hook uses the same
parser as drawing. Component measurement and splitting keep the existing
normalization path. Sampled output removes its effect marker, avoiding recursive
resampling; float pen advances and pose translations avoid per-character rounding.
HUD text delegates to the same renderer with its readability palette.

Native sequences are **already in visual order**. Supply literal markup through
`parse`/`normalize` before obtaining `getVisualOrderText`; do not embed unparsed
markup into an already reordered RTL sequence. Effects never reorder sink entries.

## Unicode and accessibility

Effects use ICU grapheme boundaries, so supplementary characters, combining marks,
variation selectors, emoji modifiers, flags and ZWJ sequences share an effect
ordinal. Existing custom effect callbacks still receive each original code point
and its native style, but index/length now refer to visual clusters. A callback
that deliberately varies offsets by code point remains responsible for that choice.
When a reversed or RTL run contains attached marks and the original logical shaping
map is unavailable, move the entire marked run together rather than guessing mark
attachment. The original visual sink indices are retained.

High contrast suppresses animated decoration; reduced motion uses existing static
palette/effect behavior. Width remains the untransformed native advance, never the
animated offset. Line breaking therefore does not oscillate with the animation.

## Explicit remaining boundaries

This is not a claim of full external-mod compatibility on 26.x. GuideMe/FTB binary
adapters need version-specific dependencies and in-game validation before enabling
hooks on those versions. The 26.x renderer currently covers native text and HUD;
the pure FCS convenience APIs above are also mirrored on 26.x. Native 26.1.2 rendering has the game smoke evidence below. Third-party font
engines and external GuideMe integration still require their own smoke tests.

## Validation snapshot (2026-11-18)

Merged current main `49502c69c56a01373303a1f8ee4dd255b890698c` into this branch,
retaining both sets of changes, then ran `test kernelTest verifyLibraryJar`:

| Target | Tests | Skipped | Failures/errors | Kernel tests | Library verification |
| --- | ---: | ---: | ---: | ---: | --- |
| Forge 1.20.1 | 1,853 | 5 | 0 | 541 passed | passed |
| NeoForge 1.21.1 | 1,865 | 5 | 0 | 541 passed | passed |
| NeoForge 26.1.2 | 812 | 5 | 0 | 541 passed | passed |

The control-panel pagination failure observed on previous main `7f4a23e` was fixed
by the subsequent main changes; the tests are not excluded here. These numbers
are from the combined final tree, not the earlier isolated branches.

NeoForge 26.2 is not accepted: the earlier attempt on `7f4a23e` was blocked by 22
compile errors in unchanged screen/options API migration files. It was not rerun
on `49502c6`. No external font-engine or GuideMe 26.x runtime acceptance is claimed.


## Native game smoke and independent review

On 2026-11-18, the real NeoForge 26.1.2 OpenGL game client passed
`runWidgetSmoke -PwidgetSmokeFormattedText` with
`gradle/neoforge-26-widgets-smoke.init.gradle`. This isolated smoke source set is
not shipped in the library. The test invokes native String, Component and FCS
extractor overloads, checks callback invocation, matching native widths (78px),
and three wrapped lines bounded by 210px. It saves two real framebuffers and a
machine-readable result in the smoke game directory. Across 597ms, the custom
probe changed 837 pixels and the built-in animated palette changed 1650 pixels.
Both screenshots were visually inspected; this validates native animation and
rendering, not external fonts or complete Unicode glyph availability. Default
Minecraft fonts remain pixel-styled. The game shut down normally, build exit 0.

An independent source review found no remaining blocking issues in this core
change. Three earlier issues were fixed and regression-tested: cross-line index
resets, custom code-point offsets/outlines within clusters, and grapheme joining
across physical line boundaries.

## GTSH parity checklist (not a complete migration)

| Feature | Status / boundary |
| --- | --- |
| Named palettes, aliases, body/random modes, inline effects | Covered by parser/registry and native renderer |
| Dynamic colors, per-glyph offsets, outline and obfuscation | Covered; custom callbacks preserve original code points/styles |
| Component styles, measurement and native wrapping | Covered; normalize markup before shaping |
| Unicode cluster animation and visual/source ordering | Covered with conservative marked RTL-run handling |
| Native 26.1.2 HUD and text entry points | Covered; actual game smoke passed |
| High contrast and reduced motion | Existing accessibility policies preserved |
| Pure FCS colorize/lines, gradient, lyrics and reveal | Mirrored on 26.x; reveal/lyric progress remains code-point based |
| ShanhaiTextAPI source facade | Added in the opt-in compatibility slice below; SHBUI namespace, not binary compatibility |
| Speed/direction/time locks | Added for facade snapshots only; existing explicit frame-time API is unchanged |
| Concat overloads, default-style format, start/end colors | Added in the compatibility facade; exact differences documented below |
| External GTL wrappers | Not implemented without verified bundled dependency contracts |
| Raw markup in already-shaped bare FCS | Intentionally not reparsed; parse before bidi shaping |
| Duplicate registry IDs | Namespaced registration/cleanup; duplicate IDs throw rather than return old definition |
| Unmarked text and unknown markup | Remain unchanged/literal, unlike GTSH implicit ultimate-color behavior |
| GuideMe/FTB on 26.x | Missing version-specific adapters/dependencies and runtime acceptance; older adapters are not proof |
| KubeJS on 26.x | Bindings not established |
| Third-party font engines | Not verified |
| NeoForge 26.2 | Not accepted; separate API migration compile blockers described above |

## Opt-in source API compatibility slice

`com.dishanhai.sh_better_ui.client.gui.text.ShanhaiTextAPI` is an explicit,
source-level facade. It is not installed under GTSH's original Java package and
is not a drop-in binary mod replacement. Existing `UiTextEffects`,
`UiFormattedText` and existing KubeJS bindings retain their current semantics.

The facade distinguishes construction-time color snapshots from live FCS effect
markers. Rebuild snapshot Components to animate them. Its speed/direction/time
lock are confined to the facade, never the whole SHBUI renderer. `setSpeed(80)`
restores the source adaptive/body timing policy rather than forcing an 80ms step.
Custom RGB/ChatFormatting calls are discrete wall-clock snapshots and deliberately
ignore that lock, speed and global direction. RGB `dir` remains an arbitrary
palette stride, including zero. `resetCompatibilityState()` restores compatibility clock defaults.

The compatibility surface includes named/body convenience methods, style fallback,
both concat overloads, `format(input, defaultStyle)`, permissive inline tags,
legacy section codes, custom snapshots, multiline snapshots, endpoint queries,
gradient filler and reveal overloads. Unknown styles and unclosed inline tags
follow the explicit compatibility facade's rules; the existing stricter parser
is not changed. Native Component text is clean before layout instead of retaining
hidden ampersand markup for a later mixin to strip.

Deliberate corrections and limits:

- Unicode code points replace upstream UTF-16 halves. This snapshot API does not
  claim grapheme-count reveal semantics.
- Multiline dash conversion (`——` to `----`) and empty-line space behavior remain,
  but the total length is counted after conversion to keep phases consistent.
- Single-code-point gradient filler uses the source midpoint with truncating RGB
  interpolation; the existing modern gradient API is unchanged.
- Legacy `§` parsing additionally preserves native bold/italic formatting rather
  than reproducing upstream's color-only loss. Unknown section-code sequences
  stay literal so malformed markup cannot split a supplementary character.
- Explicit styled/reveal preference overloads honor accessibility. Source-signature
  defaults remain pure construction with DEFAULT preferences; hosts must pass their
  actual preferences when needed. The upstream global string-matching
  tooltip centering side channel is not recreated; the host still owns alignment.
- Live FCS/WOBBLE and randomized meta modes use SHBUI's engine, not GTSH's global
  render-context caches or identical random registry/seed identity.
- External GTLCore and GTLAdditions wrappers remain unimplemented: the exact
  bundled GTLCore `1.2.3.2-fix4` and GTLAdditions contracts were not established.
- This facade does not install new KubeJS 26.x, GuideMe or FTB integrations.

### Compatibility-slice verification

The facade covers 72 of the upstream API's 83 distinct public method names; the
11 omitted names are the external GTLCore/GTLAdditions wrappers. Method-name
coverage is not an overall migration completion percentage or exact binary parity.

After cross-review, malformed `§` plus supplementary characters was corrected and
regression-tested. The final tree then passed `test kernelTest verifyLibraryJar`:

| Target | Tests | Skipped | Failures/errors | Kernel tests | Library verification |
| --- | ---: | ---: | ---: | ---: | --- |
| Forge 1.20.1 | 1,872 | 5 | 0 | 541 passed | passed |
| NeoForge 1.21.1 | 1,884 | 5 | 0 | 541 passed | passed |
| NeoForge 26.1.2 | 831 | 5 | 0 | 541 passed | passed |

This adds 19 tests per target over the native-renderer slice. Independent
cross-review confirmed the Unicode correction and found no remaining blocking
issues. The prior real-game smoke covers the renderer; this additional snapshot
API slice was verified by the tests above, not a new visual screenshot run.
