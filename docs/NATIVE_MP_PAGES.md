# Native Favorites, Mods, Themes and Preferences

Entry: control center → Muonium → the four native page buttons. The optional
external MP settings bridge remains a separate button below them.

These pages are SHBUI-native adaptations, not an embedded OneConfig runtime.
The inspected source exposes MuoniumFavorites/FavoritesTree as high-frequency
HUD controls. Its Favorites is **not** an account's liked songs or playlists.
The Themes page reuses the existing SHBUI registry browser and appearance editor;
Mods/Preferences use actual loader metadata and existing SHBUI settings.

- Favorites: current HUD/island/ambient/notice settings and island size; each
  successful field edit is immediately persisted, with field-level Undo.
  Pinned frequent settings are local, independently persisted, and editable.
- Mods: real loaded mod ID/name/version/description, searchable and scrollable.
  This is read-only metadata, not pretend hot enable/disable functionality.
- Themes: actual registry choices, native previews and existing color/background/
  effect editors. Preview is draw-scoped and Cancel discards uncommitted edits.
- Preferences: high contrast, reduced motion and control backdrop blur. These
  edit the real three-field settings record, not a disconnected mock form.

When opened from the control center, Themes/Preferences Apply only stage into
its current draft. The page explicitly says so. **Use the control center's Apply
button to persist these changes.** Existing unrelated draft settings/shortcut
edits are retained. Standalone persistence entry points keep their separate
commit semantics. Favorites uses the independent overlay configuration, so its
immediate-save behavior is separately identified.

No MP account/music backends, optional OneConfig runtime, Apple fonts, brand
assets or third-party binaries are introduced. Forge 1.20.1 and NeoForge 1.21.1
are the supported targets for this increment; experimental 26.x is not claimed.
