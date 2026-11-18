<!-- Author: dishanhai -->
# NeoForge 26.1.2 optional text integrations

This slice is isolated to `neoforge-26.1.2/src/textCompat`. It is not enabled for
26.2 or used to replace the existing older-loader adapters. The integrations use
optional `@Pseudo` targets with required injection hits when their target exists;
they are not silently accepted from a successful library build alone.

## Pinned official artifacts

| Integration | Official coordinate | SHA-256 | Declared license |
| --- | --- | --- | --- |
| GuideME | `org.appliedenergistics:guideme:26.1.17-beta` (Maven Central) | `613a11017cb843e424d5021161d21adff955de26dde508c126933c7a5d53ced0` | LGPLv3, with shaded MIT/Apache components |
| FTB Library | `dev.ftb.mods:ftb-library-neoforge:26.1.2.12` (`https://maven.ftb.dev/releases`) | `0a2002c9bb138fc4a68b0acd77c2092f33ecdaff89edc8aa6ae4f36540e24320` | All Rights Reserved |

GuideME's Central binary matches its official GitHub release byte-for-byte.
Its metadata requires Minecraft 26.1.2 and NeoForge >=26.1.2.10-beta, <26.2.
FTB requires NeoForge >=26.1.2.71, <27 and Minecraft >=26.1, <27; this test target
uses 26.1.2.126. FTB bundles its own JSON5/WebP dependencies and does not require
Architectury in this version. These artifacts are compile-only and are copied
only into the isolated smoke profile. They are not bundled into SHBUI's jar;
`verifyLibraryJar` rejects provider implementation packages/JarJar content.

## GuideME boundary

The adapter targets the actual `LineBuilder.appendText` / constructor call and
`LineTextRun.render(RenderContext)` entry points of this pinned version. Layout
associations belong to each builder/run instance, with `try/finally` restoration;
there is no global string-content cache or ThreadLocal drawing side channel.
Markup is removed before GuideME measures supported text, then attached to the
actual emitted run. Native host font, bold, hover-resolved style and scale remain
owned by GuideME. Normal, unmarked text uses the original method unchanged.

This slice supports FCS color/displacement effects within GuideME's own metrics.
Inline native formatting that changes glyph metrics is not accepted as if it had
correct measurement: it remains literal, including its prefix, and is counted as
unsupported. Unexpected emitted-run mismatches invalidate further association
rather than crashing the page or styling a different run. Source content changes
require a native GuideME relayout, as usual.

GuideME's own narrow-line UTF-16 break-width behavior is not rewritten here.
Passing a representative supplementary-character probe does not mean every
vendor Unicode line-breaking defect has been fixed.

## FTB boundary

The adapter delegates all native formatting/substitution/newline processing to
the real `TextComponentParser.parse0` after SHBUI's supported markup parsing.
Plain input remains on the original path. The real FTB Theme's Component and FCS
rendering branches use the already-supported native 26 text pipeline.

Call the FTB parser before laying out marked strings. Directly sending unparsed
FCS markup to arbitrary vendor string-layout APIs is not covered. No FTB parser
or Theme implementation is copied into SHBUI.

## Isolated real-provider smoke

Use `gradle/neoforge-26-text-compat-smoke.init.gradle` and task
`runTextCompatSmoke`. The separate consumer installs the pinned official jars
only under `build/text-compat-smoke/<version>/mods`, alongside the actual SHBUI
library. It executes FTB parser/Theme probes and a genuine GuideME document
layout/render probe, then saves two framebuffers plus `compat.txt` and exits.
Two-frame region changes and provider hit checks are required; fixture tests are
not substituted for the actual provider APIs. The ordinary widget smoke profile
remains available to verify startup without either optional provider installed.

## Validation on the final adapter tree

Full `test kernelTest verifyLibraryJar` passed: 848 tests (5 skipped), zero
failures/errors; 541 kernel tests, zero failures/errors; no bundled provider code.
Old-loader source/configuration was unchanged by this optional 26.1.2 slice.

- Actual official-provider client smoke passed on Minecraft 26.1.2 / NeoForge
  26.1.2.126, Java 25. GuideME built 3 native paragraphs, attached 4 real runs,
  rendered 104 runs, with zero unsupported/mismatched associations. Its document
  and paragraph bounds matched the unmarked baseline (height 54).
- Two real screenshots 597 ms apart changed 9,103 GuideME-region pixels and 4,074
  FTB-region pixels. FTB actual parser checks passed for native formatting,
  substitution (exactly once), newline wrapping, FCS markers and width; both
  official Theme Component/FCS draw branches passed their advance checks.
- The separate no-provider client then passed native String/Component/FCS and
  tooltip probes and exited normally. Its mods directory contained only SHBUI.
- Representative Unicode strings retain their code points and measured widths;
  the bundled Minecraft font does not provide every supplementary glyph. The
  screenshots are not evidence of complete font coverage.
- These tests exercise real provider APIs inside a dedicated consumer screen,
  not an FTB Quests screen or a user-authored GuideME book. They do not validate
  arbitrary provider versions, 26.2, PRE whitespace, hover-font metric changes,
  or all bidirectional/Arabic layout combinations.
