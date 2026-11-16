# Muonium-derived native themes

Three opaque palettes are available in **Control panel → Appearance → Dynamic theme → Next registered theme** (apply the draft to save), and in **Developer panel → Theme previews**:

- `sh_better_ui:muonium_velvet_rose`: warm dark rose surfaces and pink actions.
- `sh_better_ui:muonium_midnight_graphite`: neutral charcoal surfaces and blue-grey actions.
- `sh_better_ui:muonium_porcelain_ink`: warm porcelain surfaces and terracotta actions.

They use UUK's existing theme manager, resource reload lifecycle, isolated gallery preview, high-contrast preferences and reduced-motion policy. No provider, playback, login or account behavior changes. No GPU blur, render backend, texture, font or music-platform icon is imported.

## Source and adaptation

Palette reference: [MuoniumPlayer `NCMTheme.ThemePreset`](https://github.com/zihe114514/MuoniumPlayer-multiversion/blob/9b4cc7e7bd143315da716075a6d024a0d4c0c81b/muonium-common/src/main/java/com/muoniumplayer/core/screens/ncm/NCMTheme.java), copyright 2026 MuoniumPlayer Team, Apache-2.0. This is a native semantic-token adaptation, not a drop-in copy of Muonium's renderer. The original palette source attribution is retained in `MuoniumThemes.java`; consult the distributed third-party license and notice.

Porcelain secondary text, accent, focus and border were darkened for readable native UI text. All three explicitly set primary-action foregrounds and state overlays, rather than inheriting unrelated blue colors. Automated tests verify registration after reload, isolated preview selection, 4.5:1 text/action contrast on opaque surfaces and visible focus. Those numeric checks do not replace visual testing in Minecraft at the user's GUI scale or narrator testing.

Native widget: [media-card API and developer preview](muonium-media-card.md).
