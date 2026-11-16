# Native theme browser host contract

`MpThemeBrowser` is a registry-backed controller and `UiThemePreviewCard` is its native focusable widget. Inspired by MuoniumPlayer `MConfigHudScreen.drawThemeChips` / `MConfigTheme` at revision `9b4cc7e7bd143315da716075a6d024a0d4c0c81b`; implemented against SHBUI's existing types, without copying Skija rendering or assets.

```java
var browser = new MpThemeBrowser(MinecraftThemes.manager(),
    () -> draft.settings().theme(), theme -> stageTheme(theme), MinecraftThemes::isConfiguredPalette);
for (var widget : browser.layout(body, preferences)) addRenderableWidget(widget);
// Before normal Screen.render: if (browser.refresh()) remount the visible page.
// Widgets paint themselves. Do not separately render them twice.
// Previous/Next: browser.page(-1 / +1), then remount; pageIndex is zero-based.
// On host/page removal: browser.close(). Never closes the supplied live manager.
```

The supplied theme must reflect the actual draft selection. Legacy `dynamicTheme.fixedTheme` takes precedence over dormant DARK/LIGHT/CUSTOM palette fields until the host seeds the corresponding `withRegisteredTheme(id)` draft. This controller cannot infer that field from `ThemeSettings` alone.

There is one real custom-palette entry plus current registered themes. Hidden runtime-owned themes are excluded by the supplied predicate; removed registrations cannot be selected even before remount. Read `error()` for a bounded palette archive failure, retain the draft and show its localized message. `layout` mounts at most nine cards; small viewports mount no cards rather than clipping interactive targets. Card geometry uses native `getX/getY`, so a scrolling host may reposition widgets.

The miniature shows actual resolved background, surface, text, accent and transparency, with per-theme RGB overrides. Registry and token changes refresh it without changing live preferences. It is a bounded illustration over an opaque reference canvas, not a costly per-card render-to-texture simulation of world refraction. A real selected-theme preview/editor remains available through the host's existing appearance editor.

Language additions are supplied in `docs/snippets/mp-theme-browser-language.json` for the host integrator. Shared language files are intentionally not edited here.
