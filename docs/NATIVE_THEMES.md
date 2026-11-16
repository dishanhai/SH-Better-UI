# Native Themes page

`NativeThemesScreen(parent, initialSettings, save)` is a SHBUI page backed by the live theme registry, not a MuoniumPlayer runtime wrapper. The host supplies its existing validated/persistent settings save callback. A `false` result or thrown exception leaves the draft editable and shows failure; no successful save is claimed.

The four-argument overload `NativeThemesScreen(parent, initialSettings, stage, true)` is for a control-center child page. It explicitly labels Apply as staging to the parent draft, not a disk save. The parent callback stages the returned settings, and the control center's outer Apply is the only persistence boundary. The three-argument overload retains standalone persistent-host semantics.

## Behavior

- Registry themes, including resource-defined themes, use the existing `MpThemeBrowser` cards. Each card resolves actual palette tokens and any per-theme saved color overrides.
- Selecting a card stages a real theme setting. The page itself previews the selected palette inside `ThemeEffects.preview` / `UiTheme.preview`, without modifying the global theme manager.
- Colors, backgrounds and effects open the existing `AppearanceService` isolated theme editor. Confirming a child editor stages its result; only this page's Apply calls its host. In staged mode, the outer control center must then Apply to persist.
- Cancel/Escape discards changes since the last successful Apply. Returning from child editing retains the page and selection. Resize remounts only fitting rows.
- Wheel over the theme grid and Page Up/Down navigate the bounded catalogue. Cards support Tab, Enter and Space; explicit previous/next buttons are also available. This is paged grid navigation, not a pixel-continuous scrolling grid.
- Too-small windows retain Cancel and disable Apply rather than placing unreachable cards outside the viewport.

## Provenance and limits

Reference inspected: MuoniumPlayer-multiversion `9b4cc7e7`, `MConfigHudScreen.drawThemeChips` (preset chip selection, labels and selected-state affordance). Implementation reuses SHBUI's registry, controls and native Minecraft fonts. No MP source, Apple fonts or image assets are copied by this increment.

This page configures themes already registered in SHBUI. It does not claim Skija material parity, import every current MP preset, or add music playback/download backends. Background/effect editing is provided by the existing appearance registry, not fabricated controls.
