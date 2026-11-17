# Native Favorites / 常用设置

Upstream reference: `MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/screens/hud/MuoniumFavorites.java`.
Its Favorites page fills MuoniumConfig's FavoritesTree with frequently used HUD settings; it is not a song-library or account-likes page.

SHBUI's independent native implementation offers real HUD enabled, island enabled, ambient status, task notices, and island-scale controls. Settings are written through NativeMusicOverlay's existing transaction/persistence, not a second settings copy. Scale uses the shared keyboard/pointer slider. Changes save immediately and the last change has an Undo action. Opening the page never enables an overlay automatically.

Users can pin/unpin these supported settings. Pin IDs alone are stored locally in `config/sh_better_ui/favorite-settings.txt`; there are no songs, account identifiers, credentials, or network requests. The default pin list differs from an explicitly empty list. Writes use a temporary file and atomic replacement when supported; failed writes retain the prior in-memory list. Page controls, mouse wheel, and Page Up/Down reach rows at small GUI sizes.

This is an adapted subset, not full MuoniumConfig or MP playback integration. MP-specific desktop lyrics, spectrum opacity and environment acoustics are not fabricated when SHBUI does not expose an equivalent native setting. Existing native song-provider favorites remain separate.
