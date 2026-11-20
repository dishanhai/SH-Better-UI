# Apple Music-inspired native frontend rebuild

The new workspace is an opt-in implementation slice, not a redesign of the legacy screen's colors. The default music screen is unchanged until parity and in-game visual acceptance. To try the new workspace, add `-Dshbui.music.previewRedesign=true` to the client JVM arguments and open the existing music menu. The preview owns a separate session and closes it on exit; the stock background session, release modpack and player credential store migration are not changed.

## Layout and behavior

- Wide layout: sidebar for Discover, Library and Search, separate content area, low-profile pinned MiniPlayer. Compact layout: the same three destinations below the MiniPlayer.
- MiniPlayer metadata opens Now Playing. Transport controls do not change view. Now Playing owns the artwork, Lyrics and Playing Next. Current/reading lyrics remain sharp; inactive rows use a smooth ink hierarchy, without drawing multiple offset copies.
- Account access lives under Settings → Accounts. Apple Music is presented as system-browser sign-in, not a fourth native search source. An explicit action opens the local MusicKit companion only if the project token and fixed port have been supplied and Minecraft allows links. Apple passwords, Music User Tokens and native queue operations do not cross the browser boundary.
- Routing, geometry and transport are separate from drawing. The old frontend and existing audio providers remain intact. Coverage in the preview is intentionally limited: first existing catalog source and first search page, basic queue selection, playback and synchronized lyrics. Native account QR, library collections and discovery feeds, effects, full queue editing and the final now-playing layout must gain parity before the release switch.

## Reference and acceptance

- [Apple iPhone Music controls](https://support.apple.com/en-euro/guide/iphone/iph676daac9b/ios): MiniPlayer → Now Playing, with queue and lyrics as contextual destinations.
- [Apple iPad Music library](https://support.apple.com/en-euro/guide/ipad/ipadcdc3376e/27/ipados/27): sidebar preserves browsing context while the album/list detail owns the canvas.
- [Apple iPhone queue](https://support.apple.com/en-lamr/guide/iphone/ipha4521ef7d/ios): queue follows the selected occurrence and remains editable without replacing navigation.

Target next: capture compact and wide real in-game screens with long CJK titles, long queues and dense lyrics. Verify reduced motion, high contrast and keyboard focus. Then port route-level parity and runtime-test provider auth, search and ordered playlists. Do not bundle Apple fonts, logos or app assets.
