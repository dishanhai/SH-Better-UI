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
| External GTL wrappers | Optional runtime delegation added below; exact signatures statically verified, real-provider runtime pending |
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
- External GTLCore/GTLAdditions support was initially deferred. The subsequent
  optional-provider slice below verifies exact signatures without bundling the libraries.
- This facade does not install new KubeJS 26.x, GuideMe or FTB integrations.

### Compatibility-slice verification

At this slice the facade covered 72 of the upstream API's 83 distinct public method names; the
11 deferred names were the external GTLCore/GTLAdditions wrappers. Method-name
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

## Optional GTL provider wrappers

All 11 external wrapper names are now exposed by the facade, bringing its method
name surface to all 83 upstream names. This is not proof of full migration or
runtime compatibility: these 11 calls require an installed, compatible provider.
The bridge delegates to that provider rather than copying its algorithms,
shipping its JARs, or returning a similar-looking fallback. Existing native
SHBUI functionality does not load or require either provider.

`gtlCoreAvailable()` / `gtlAdditionsAvailable()` inspect the public method shapes
without initializing the provider. They report signature availability, not
successful runtime integration. Missing classes, wrong signatures or class-file
linkage failures yield false. Calling an unavailable operation throws a diagnostic
exception. Provider execution failures retain their original cause and are not
misreported as a missing dependency. Only successful method resolutions are cached.

### Exact source-artifact inspection

The following files were downloaded outside the project solely for static
inspection from [GTSH's libs](https://github.com/dishanhai/gtl_shanhai-dishanhai/tree/master/libs).
Their sizes and Git blob hashes were verified before `javap` / metadata inspection:

| Artifact | Bytes | Git blob | Text implementation class version |
| --- | ---: | --- | --- |
| gtlcore-1.2.3.2-fix4.jar | 13,069,272 | `399807a82dc467658ac7bcb39ad32184a4b4448e` | 61 (Java 17) |
| gtladditions-3.3.1Custom.jar | 40,680,482 | `858316fb76356cfb8c40e363bd8f35099aa4c2fe` | 65 (Java 21) |

The exact bytecode confirms the seven public static `TextUtil` methods and the
four `CommonUtils` methods (two static and two via public static final `INSTANCE`)
used by the bridge. Component subtypes are accepted as return types; arguments
and returned objects are passed through unchanged.

Both JAR metadata files declare LGPLv3.0 and Forge 47 / Minecraft 1.20.1. This
change copies neither implementation nor binaries and does not remove upstream
license requirements from anyone distributing those dependencies. The Additions
custom class requires Java 21 despite its 1.20.1 metadata, and declares further
GTLCore / SGJourney / client Oculus dependencies. It cannot load on a Java 17
runtime. Neither old Forge artifact is an accepted NeoForge 1.21.1/26.x provider.

No code from these JARs was executed. Contract fixtures verify SHBUI's dispatch,
not real-provider game behavior. Actual installed-mod integration remains pending
on a supported Minecraft/loader/Java/dependency combination.

## Instance-scoped tooltip alignment

`UiTextTooltip.lines(...)` produces native `ClientTooltipComponent` rows sharing
one immutable `TooltipTextLayout`. Select LEFT, CENTER or RIGHT and an optional
minimum width in native GUI units. `UiTextTooltip.wrap(font, component, width,
alignment)` normalizes before native wrapping, preserving styles and visual order.
Submit the returned rows through the host's native tooltip API. The caller owns
screen placement, available width, showing/closing the tooltip and reveal updates.
Rebuild the rows when changing reveal content or wrapping; no global registration
or cleanup is needed. Alignments apply to these text rows, not unrelated image or
item rows added by a different tooltip contributor.

This deliberately replaces, rather than reproduces, GTSH's global
`CENTER_TARGETS`/`CENTER_CONTAINER_WIDTH` matching by text content. Identical text
in separate tooltip instances cannot inherit one another's alignment. The 26.x
adapter restores its pose in `finally`; the older adapter submits float offsets
without mutating the caller's pose. The host applies GUI scale once. No global
Font mixin, string-content cache or tooltip event interception is introduced.

### Third-party 26.x adaptation inventory

GuideME and FTB Library do have 26.1.2 upstream source variants:
[GuideME v26.1.17-beta](https://github.com/AppliedEnergistics/GuideME/releases/tag/v26.1.17-beta)
uses `LineTextRun.render(RenderContext)` and `RenderContext.renderText`, not the
old adapter's `renderBatch`/`renderTextInBatch` entry points. The
[FTB Library dev parser](https://github.com/FTBTeam/FTB-Library/blob/dev/common/src/main/java/dev/ftb/mods/ftblibrary/util/TextComponentParser.java)
retains the `parse(String, Function)` / `parse0` shapes. This is source inspection,
not a version-pinned binary integration test. Dedicated 26.x adapters and actual
mod-page validation remain a separate slice; old adapters must not be enabled
blindly against the changed GuideME renderer.

### Optional-provider and tooltip verification

After independent review, the final combined tree passed
`test kernelTest verifyLibraryJar` on all three targets:

| Target | Tests | Skipped | Failures/errors | Kernel tests | Library verification |
| --- | ---: | ---: | ---: | ---: | --- |
| Forge 1.20.1 | 1,884 | 5 | 0 | 541 passed | passed |
| NeoForge 1.21.1 | 1,896 | 5 | 0 | 541 passed | passed |
| NeoForge 26.1.2 | 843 | 5 | 0 | 541 passed | passed |

Seven optional-provider contract tests and five tooltip tests were added per
version. The provider tests use explicit fixtures, including missing/linkage
failures, covariant Component returns, static/INSTANCE dispatch, lazy loading,
original argument/object identity and provider exceptions/initialization errors.
They do not execute the external artifacts.

The real NeoForge 26.1.2 OpenGL client then passed the extended formatted-text
smoke. It displayed three native tooltip containers with identical text and
LEFT/CENTER/RIGHT alignment, at GUI scale 2 and 104-unit text width. Native
width/height and independent-offset assertions passed. Two actual framebuffer
screenshots 568ms apart were visually inspected; each tooltip region changed
468 pixels, confirming visible animated text in all three containers. The
original native-text probe changed 814 pixels and the built-in palette region
1682 pixels. The game exited normally; build exit 0. This validates explicit
native tooltip rows, not automatic third-party tooltip interception or real GTL
provider execution.

### GuideME 26.1.2 optional adapter: literal fallback boundary

The isolated GuideME 26.1.17-beta adapter accepts FCS color/displacement markup while
retaining GuideME's own font, bold, scale and layout metrics. Unsupported inline
section formatting is counted and rendered as **literal logical-order FCS**: both
`§` and its following character remain visible. This fallback deliberately bypasses
`Language.getVisualOrder`, whose formatted-text decomposition would consume `§`.
It does not claim RTL reordering or Arabic shaping for unsupported inputs. Supported
FCS still uses Minecraft's visual ordering. The final-sequence regression checks
literal code points, host styles and fractional advance totals with a deterministic
`StringSplitter` metric fixture; real-font/game acceptance remains separate.

### NeoForge 26.1.2 real-provider slice

Optional GuideME 26.1.17-beta and FTB Library 26.1.2.12 adapters now have real
installed-provider layout/parser/render acceptance and a separate no-provider
startup regression. See [the integration boundary and evidence](NEOFORGE_26_TEXT_COMPAT.md).
The vendor jars remain compile-only and are not distributed in SHBUI. This does
not close GTL runtime verification, 26.2, all provider screens/versions, or the
remaining unsupported Unicode/layout combinations.
