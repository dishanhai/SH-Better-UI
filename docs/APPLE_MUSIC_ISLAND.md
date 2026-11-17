# Compact native music island

This is a fresh implementation on main `0ab67fb`, not a recovered copy of the earlier unpushed work. It is a Minecraft-native adaptation, not Apple software or a claim of exact iOS parity.

## Presentation

- Default geometry: 126 × 28 compact, 208 × 92 expanded GUI units, sharing a horizontal centre. At smaller settings the expanded panel keeps a 168 × 92 readable minimum; the viewport still clamps it.
- Compact presentation contains a small cover / original rounded note and a truthful trailing transport indicator. Full title, artist, state, progress and lyrics belong to expanded mode.
- New settings default to `music_capsule`: opaque black, quietly rounded, no idle border, visible hover/high-contrast outline. Existing persisted style IDs and the legacy missing-style fallback remain intact.
- Playing without audio analysis has a static transport glyph, not an invented spectrum. Paused, stopped, buffering and unknown are visually distinct. Audio-reactive bars require real available audio features.
- Existing interruptible expansion and reduced-motion policy are retained. Content is clipped throughout transitions; readable details appear only when the shell has sufficient height.
- No-media islands without ambient chips, explicit expansion or real notifications occupy no space. Paused media retains the existing 30-second timeout.
- A new Controls keybinding, **Expand/collapse music island**, is unbound by default. It changes only session state, not saved preferences. F1, player list and menus continue to hide the gameplay island.
- LiveSurface placement receives the visible island's full expansion envelope as a reserved area. An invisible island does not reserve that area.

## Design references

Apple's [Live Activities HIG](https://developer.apple.com/design/human-interface-guidelines/live-activities), [Creating custom views for Live Activities](https://developer.apple.com/documentation/ActivityKit/creating-custom-views-for-live-activities), and [Design dynamic Live Activities (WWDC23)](https://developer.apple.com/videos/play/wwdc2023/10194/) inform the compact leading/trailing separation, black background and progressive disclosure. No Apple fonts, symbols or artwork are bundled.

## Verification and limitations

- Isolated Java 17-compatible geometry/lifecycle/notice/ambient/capsule/shell models: 42 JUnit tests passed on JDK 21, zero failures or skips. These are **not** loader integration tests.
- `git diff --check` passed; independent source review completed. The newly added LocalMusicIsland integration test requires the normal game classpath and was not run in the isolated suite.
- Forge 1.20.1 Gradle test attempt blocked before compilation: `net.minecraftforge.gradle [6.0,6.2)` plugin could not be resolved from configured repositories.
- NeoForge 1.21.1 Gradle test attempt blocked before compilation: `net.neoforged.moddev 2.0.148` plugin could not be resolved from configured repositories.
- Full loader compilation, in-game screenshots, real audible playback, timing synchronization, actual FPS LiveSurface overlap and performance are **not verified for this new implementation**. Earlier screenshots/test counts from the missing checkout do not validate this patch.
- The island observes the existing MusicApi source. No new bridge from online player output or provider licensing/stream resolution is added here.
- Detail content switches at a readable height rather than cross-fading. This is a draft pending loader and in-game validation; do not automatically merge.
