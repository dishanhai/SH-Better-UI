# Muonium-style native media card

The new **Developer panel → Controls → Media card** is an interactive, audio-free demonstration. Click or focus it and use Enter/Space to toggle the demo's play/pause state. Switching previews preserves the demo state; leaving cancels an unfinished press.

This is a reusable UUK component, not a replacement music service or a new default player screen. Existing playback, favorites, accounts, providers and credentials are unchanged. Pair it with the [three Muonium-derived themes](muonium-theme-presets.md) through the existing Appearance theme settings.

```java
var card = ShBetterUiApi.controls().mediaCard(
        new Bounds(x, y, 240, 64),
        Component.translatable("my_mod.album.title"),
        Component.translatable("my_mod.album.artist"),
        ShBetterUiApi.settings().preferences(),
        this::requestPlaybackToggle);
addRenderableWidget(card);
// Only reflect confirmed host state, including asynchronous failure/recovery.
card.setPlaying(player.isPlaying());
card.setSelected(selectedAlbum);
```

- Minimum dimensions: **128 × 48 GUI units**. Smaller initial bounds are rejected; later undersized bounds suppress drawing and activation.
- One focus target and one action for the whole visible card. Left click activates on release inside. Dragging out, Escape, focus loss, screen suspension or disabling cancels a pending press.
- Title, subtitle and play/pause action remain available in tooltip/narration when visual text is clipped. The caller owns localization and confirmed playback state.
- Vector record placeholder and native palette rendering: no image decode, new textures, blur passes, network calls or continuous decorative animation. It inherits native hover/press/focus, high-contrast and reduced-motion preferences.
- Call `cancelInteraction()` when removing or suspending a standalone card, and update preferences when the host preferences change.

## Reference and licensing

The information hierarchy was inspired by MuoniumPlayer's `RadioCardWidget`, from [source revision 9b4cc7e7](https://github.com/zihe114514/MuoniumPlayer-multiversion/tree/9b4cc7e7bd143315da716075a6d024a0d4c0c81b). The widget is newly written against SHBUI's native interfaces; no upstream widget implementation or assets are embedded. Theme palette adaptations separately retain upstream attribution under Apache-2.0. Their complete LICENSE, original NOTICE and SHBUI changes are packaged under `META-INF/licenses/muoniumplayer/` in the runtime JARs; `verifyLibraryJar` checks their presence.

## Verification boundary

Targeted implementations are Forge 1.20.1 and NeoForge 1.21.1. The experimental 26.x rendering port does not yet expose this media-card control. Automated constructor/input/narration and theme tests do not substitute for real Minecraft rendering, GUI-scale and screen-reader acceptance on the user's installation.
