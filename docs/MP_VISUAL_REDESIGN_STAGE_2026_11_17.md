# MP visual redesign: implementation pending runtime acceptance

This stage responds to feedback that the initial native integration looked poor. It is not a declaration of full MuoniumPlayer parity.

## Direction

The reference is MuoniumPlayer's actual control-panel screenshot and upstream fullscreen-lyric material, rather than a generic glass-card gallery. Reduce oversized framed buttons, duplicate branding and noisy backgrounds. Use bounded app windows, consistent left-aligned heading hierarchies, compact settings controls, and a genuine immersive playback route.

## Typography

The UI opts into `sh_better_ui:mp_ui`; Minecraft's default font is not replaced. The bundled SH Sans UI Regular is a renamed static weight-400 derivative of the official Noto Sans SC variable TrueType font. The SIL OFL 1.1, source URL, changes, upstream copyright and hashes are packaged in `META-INF/third-party/noto-sans`. No Apple fonts are included. Width measurement, truncation and drawing must use the same styled component. Explicit child icon fonts remain intact.

## Implemented locally

- Bounded Themes and Preferences windows; reduced border noise and compact controls.
- Theme previews depict a miniature player composition instead of large color stripes.
- Immersive lyrics uses the existing player session, real artwork/lyrics and transport, with a responsive two-column layout. Escape returns to the prior page without replacing the session.
- Thin seek control previews during drag and commits on release; track changes, loss of focus or hiding cancel pending edits.
- Ordinary music view removes per-label black plates and reduces the transport footprint.

## Acceptance still required

- Compile and regression tests on both Forge 1.20.1 and NeoForge 1.21.1.
- TrueType resource loading, CJK/Latin baselines, narrow screens and GUI scaling in the real game.
- Actual screenshots of these pages, not only developer-gallery demonstrations.
- Verify functional keyboard navigation, draft apply/cancel, and return paths.
- Restore a selectable classic horizontal control-panel layout from the owner's supplied screenshot while retaining the sidebar layout.

Remote music-provider availability remains independent of this work. A network failure or absent system media is not a successful playback test. No external service capability should be faked in screenshots.

## First runtime pass (NeoForge 1.21.1)

A real client loaded the scoped font without font-loader errors. The redesigned preferences/theme windows and the classic horizontal control-panel switch were exercised. Ctrl+F opens classic search; Escape collapses it. Applying CLASSIC wrote the Kernel module's `client_settings` value; the legacy loader TOML is not authoritative once Kernel owns settings. English and Simplified Chinese resource loading both succeeded.

The initial redesigned baseline passed Forge 1584 tests (4 skipped), NeoForge 1596 tests (4 skipped), and 513 Kernel tests per target. Subsequent runtime fixes require another regression pass: short footer labels; one readable immersive error region; bounded official metadata enrichment when NetEase search omits album art.

The QA launcher originally configured a proxy for Gradle but not its game subprocess. Passing the existing proxy to the QA-only game JVM made actual NetEase search and lyric retrieval work. The selected Canon in D track did not receive a playable stream URL from the provider; this is displayed as an access limitation, not a successful playback test. No login, paywall workaround or fake playback was used.

## Updated owner direction

After this pass the owner supplied Apple Music Now Playing and lyric-view screenshots and chose Apple Music as the principal music-interface design reference. Further work must prioritize content-led layouts, consistent transport placement, artwork-derived continuous backgrounds and uncluttered typography, not replicate MP's settings-card style. The classic control-panel layout remains independently selectable. No Apple fonts or branding may be bundled.
