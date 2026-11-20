# Optional MuoniumPlayer settings integration

The current embedded player is documented in [MuoniumPlayer 2.6.0](MUONIUM_PLAYER.md). This page records the earlier integration.

Original SHBUI adapter, authored for dishanhai. API reference: MuoniumPlayer-multiversion
9b4cc7e7bd143315da716075a6d024a0d4c0c81b (2.5.2), Apache-2.0.
No upstream source bodies, fonts, images, native libraries, or JARs are embedded by this adapter.
Project-wide attribution and upstream NOTICE are maintained separately by the integration owner.

## API and ownership

`MpSettingsScreen.open(Screen parent)` opens the native SHBUI browser. With no compatible MP,
it displays a dependency error. It does not install or download MP or MuoniumConfig.
`MpSettingsBridge` reflects public APIs, including the runtime categories/groups/entries;
there is no hard-coded setting subset. Changes go to MP and MP persistence, not SHBUI drafts.
The visible banner warns that SHBUI Cancel does not undo MP changes.

All six model kinds are represented: continuous slider with local draft, toggle, RGB color
with hex plus native RGB editor where screen space permits, choices, selectable MP theme
presets, and explicit-confirm actions. Colors and sliders only write on Apply. Locks and
visibility are checked again at write time. Actions, toggles, and all choice changes require explicit confirmation; choice prompts include the original and proposed values plus upstream per-option risk hints. Long descriptions support scrolling and Page Up/Down;
no action is run merely by opening a settings page. Search spans all categories and groups.
Paging bounds widget count. The upstream setting labels/descriptions retain MP's own language.

## Model vs external shell

- HUD settings: all categories in `HudSettingsModel.categories()`, including version-dependent
  visibility and locks. MP controls actual business capability and availability.
- Presets: model actions invoke MP's own preset manager/save/import/undo flow; they do not
  invoke SHBUI's independent theme archive.
- Player themes: `NCMTheme.ThemePreset`, `isSelectable`, `setCurrent`; these style MP, not SHBUI.
- HUD spatial editing: opens the actual installed `GuiHudEditorPro`. Its API has no parent
  return callback; a visible confirmation warns that closing it returns to the game.
- Full upstream panel: uses `OneConfigHudBridge.isAvailable()` then `open()`. The API checks
  installed UI/route/property/config classes and routes to MP. It does not invoke the automatic
  installer. Missing/incompatible MuoniumConfig is an explicit error, not a silent fallback.
- Favorites, shell Mods navigation, shell Themes, Preferences and About are **MuoniumConfig /
  OneConfig shell pages**, not fields in `HudSettingsModel`. They remain accessible via that
  full upstream-panel route when its separately installed UI stack is compatible. They are
  not independently ported to SHBUI and are not guaranteed by installing MP alone.

## Verification and limitations

Static `javap` on the supplied NeoForge 1.21.1 2.5.2 JAR confirmed public Entry methods,
HudSetting float accessors, GuiHudEditorPro no-arg constructor, and OneConfigHudBridge's
isAvailable/open signatures. The 1.20.1 recode source exposes matching Entry methods.
Both modern lanes use `compat.mc.GuiScreen extends Minecraft Screen` for the editor.
Pure Java bridge compiles with JDK17. Regression tests cover missing/incompatible model,
six kinds, live reads/writes, locks, hidden rows, invalid values, reflection failure, and choice-button confirmation/cancellation with captured selection, dynamic row focus retirement, and actual
host color keyboard cancel / captured pointer release / explicit once-only Apply.
Build/test/live-game completion is tracked by the integration owner, not assumed here.

Upstream Entry callbacks swallow RuntimeException internally. The adapter reports errors
that escape the public API but cannot prove a swallowed operation succeeded. Some upstream
legacy CHOICE entries cycle instead of direct-set; the browser reads back actual values.
No real accounts, network downloads, credential storage, destructive model operations, or
external sharing were exercised by automated tests. Live upstream backend behavior still
requires the real matching mod and all of its dependencies.

Translation merge snippets are in `docs/mp-bridge-lang/{en_us,zh_cn,zh_tw}.json`.
