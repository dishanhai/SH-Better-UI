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
the pure FCS convenience APIs above are also mirrored on 26.x. Actual in-game
rendering, third-party font engines and external GuideMe integration require their
own smoke tests. Unit/build checks are not screenshots or visual acceptance.

## Validation snapshot (2026-11-18)

Rebased on main `7f4a23e3de9bf520eb23fefb7f616a7a85f36fa5` after the parallel PRs merged.

- Forge: 1,852 tests, one unrelated control-panel pagination test failure, five skips.
- NeoForge 1.21.1: 1,864 tests, the same control-panel test failure, five skips.
- All formatted-text regression tests passed on both legacy loaders.
- NeoForge 26.1.2: 789 tests (five skips), 541 kernel tests and `verifyLibraryJar` passed.
- NeoForge 26.2: compilation blocked in existing screen/options API migrations; tests not reached.

The legacy failure is `ControlPanelScreenTest.themePaginationAndRegistryRemountRestoreMountedKeyboardFocus`:
the newly merged built-in display suites move its target off the first page. It is
tracked separately, not hidden by excluding the test. On the earlier `51e3284`
base, both legacy full suites, 541 kernel tests per loader and library verification
passed. These earlier results do not substitute for a green run on the new main.
No game screenshot, external font-engine or GuideMe 26.x runtime acceptance is claimed.
