# Native Mods and Preferences

`NativeModsScreen(parent)` snapshots loader metadata through PlatformUiApi on
Forge 1.20.1 and NeoForge 1.21.1. It searches name/ID/version with locale-stable
matching, sorts results deterministically and exposes native keyboard-focusable
rows, wheel/PageUp/PageDown and a draggable keyboard-accessible scrollbar. Detail
text wraps and scrolls; returning preserves search and list position. The screen
shows installed metadata, not runtime enable/disable controls. Descriptions are
literal text, never parsed as commands or auto-opened URLs. No mod code/config
factory is instantiated and no external links are opened.

`NativePreferencesScreen.persisted(parent)` edits actual high contrast, reduced
motion and widget backdrop blur preferences. Apply persists via SettingsApi;
Cancel/Escape confirms discarding changes. Save failure keeps the draft. The
three-field patch merges untouched fields with the latest settings and rejects
conflicting edits to changed fields. All unrelated theme/backdrop/feedback data
is preserved. High contrast intentionally suppresses effective backdrop blur.

When called from ControlPanel, construct `NativePreferencesScreen(parent,
currentDraft, patch -> { updateDraft(patch.apply(currentDraft)); return true; }, true)`.
The final `true` marks the host as staged: the button reads **Add to draft**,
not **Apply**. The outer control panel remains responsible for saving to disk.
This commits only to the parent's unsaved draft. It must not independently write
global settings while the parent owns a pending draft.

Reference audit: MuoniumPlayer's `MuoniumFavorites` documents its optional
MuoniumConfig mod-card homepage and FavoritesTree. The checked-out MP source
contains that integration but not MuoniumConfig's Mods/Preferences implementation.
These pages adapt those navigation roles to actual SHBUI data and existing native
controls; they are not a claim of complete MP page parity. No fonts/assets or
third-party implementation code are copied. Experimental 26.x runtime support
was not validated by this change.

Both native screens override the four-argument background hook with an empty
implementation because their render methods already paint the backdrop. This
applies to both list and detail modes and avoids NeoForge double background blur.
