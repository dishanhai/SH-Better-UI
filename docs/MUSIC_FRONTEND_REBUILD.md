# Apple Music-inspired native frontend rebuild

The new workspace is an opt-in implementation slice, not a redesign of the legacy screen's colors. The default music layout is unchanged until parity and in-game visual acceptance. To try the new workspace, add `-Dshbui.music.previewRedesign=true` to the client JVM arguments and open the existing music menu. The built-in preview borrows the same kernel-owned player, queue and HUD bridge as the stock route. Exiting the workspace closes only its UI, local requests and Apple browser listener; playback and HUD controls remain live. The ordinary route stays the default. Activity-notice links follow the same preview flag and select only the original operation in the shared session history. This does not update the release modpack.

## Layout and behavior

- Wide layout: sidebar for Discover, Library and Search, separate content area, low-profile pinned MiniPlayer. Compact layout: the same three destinations below the MiniPlayer.
- MiniPlayer metadata opens Now Playing. Transport controls do not change view. Now Playing owns the artwork, Lyrics and Playing Next. Current/reading lyrics remain sharp; inactive rows use a smooth ink hierarchy, without drawing multiple offset copies.
- Preview page changes ease over 180 ms: the header settles, the destination body fades to full contrast, the tab marker can retarget, and Now Playing artwork grows into its own slot. Layout, input and MiniPlayer do not wait. Reduced motion and high contrast skip the route transition. Lyric motion uses the current preferences and the Follow Lyrics action returns from manual scrolling.
- Account access lives under Settings → Accounts. Apple Music is presented as system-browser sign-in, not a fourth native search source. An explicit action opens the local MusicKit companion only if the project token and fixed port have been supplied and Minecraft allows links. Apple passwords, Music User Tokens and native queue operations do not cross the browser boundary.
- Routing, geometry and transport are separate from drawing. The old frontend and existing audio providers remain intact. The preview supports provider switching, a scrollable search page and remote pagination with retry, QQ/NetEase account playlists and liked songs, native QR sign-in and explicit credential storage, Apple browser sign-in, and an occurrence-bound queue with reorder/remove/undo. Discover uses verified official provider entry points, not an invented personalized feed. Complete supported playlists retain source order and duplicate tracks; Liked only queues the songs already loaded, never unseen provider pages. The compact workspace and transport remain separate. Returning from a confirmed or cancelled official site prompt preserves the exact parent stack and typed link; stale callbacks cannot replace newer navigation.
- Settings groups Accounts, Playback, Sound, and Activity. Playback exposes native speed and crossfade; Sound exposes volume, mute, EQ and QQ effects. Controls use the existing background player's capabilities and settings. They do not process the browser companion's audio, and no AutoMix or Sound Check implementation is claimed.
- Activity is a bounded, in-session view of the existing operation registry. A notification selects its original record without replaying it; an expired or replaced account's target cannot open a different record. A search already in progress can finish after the player UI closes and post its existing background notice.
- The compact navigation uses original vector glyphs with text labels. At the minimum supported viewport, both native Discover providers and Search still fit; search has at least one result slot and the Settings groups can be scrolled.

## Reference and acceptance

- [Apple iPhone Music controls](https://support.apple.com/en-euro/guide/iphone/iph676daac9b/ios): MiniPlayer → Now Playing, with queue and lyrics as contextual destinations.
- [Apple iPad Music library](https://support.apple.com/en-euro/guide/ipad/ipadcdc3376e/27/ipados/27): sidebar preserves browsing context while the album/list detail owns the canvas.
- [Apple guide to EQ and Sound Check](https://support.apple.com/guide/iphone/adjust-the-sound-quality-iph5643d2c85/ios) and [song transitions](https://support.apple.com/zh-hk/105067): reference for separating audio controls from the library and playback surface.
- [Apple iPhone queue](https://support.apple.com/en-lamr/guide/iphone/ipha4521ef7d/ios): queue follows the selected occurrence and remains editable without replacing navigation.

- In-game Forge 1.20.1 preview smoke used a local playback fixture on draft baseline `f5b4bc4`. Wide and narrow lyrics rendered with readable CJK text; native input opened Now Playing → Lyrics and Settings → Accounts. This was not a real provider login, audio or lyric-animation test.
- Regression coverage checks three acceptance issues: a painted Cancel cannot become Sign Out on QR confirmation, a Settings subpage returns to the same row and page on narrow layouts, and a search result cannot enqueue after its account context is invalidated.
- Outstanding acceptance: actual QQ, NetEase and Apple login and playback, real audio continuity and lyric scrubbing, and in-game keyboard focus, reduced motion and high contrast. Do not bundle Apple fonts, logos or app assets.

## Music appearance

Liquid Glass is now the default **music chrome** under the default SHBUI palette, on both the ordinary player and the opt-in workspace above. The layout option is independent of the theme. The native material is limited to navigation and playback controls, using one backdrop snapshot per batch. Covers, lyrics, queue text, and focus indicators are painted after those surfaces and never fed back through them.

Settings → Appearance offers Automatic, Liquid Glass and Classic. The stock player offers the same preference in Accounts. Choose a style to see it immediately and reopen the player without losing the choice. Automatic keeps the original behavior: standard dark SHBUI and registered glass themes use glass; light, QQ and other non-glass custom palettes retain their usual chrome. Explicit glass changes the music surface material without replacing the global palette. The legacy JVM argument `-Dshbui.music.theme=classic` locks Classic for that launch, without overwriting the saved preference. High contrast, disabling blur or disabling glass sampling, and unavailable backdrop capture fall back to solid chrome.


## List scrolling

The stock and rebuilt players use native scroll viewports rather than Previous/Next list buttons. Search, library collections, artist results, queue, activity and settings share the kernel's damped vertical spring. Virtualized rows move with the sampled pixel offset inside a shared paint and input clip. Dragging the scroll thumb follows the pointer directly; reduced motion snaps without coasting. Arrow, Page Up/Down, Home and End remain keyboard scroll actions. Playback and route navigation buttons are unaffected.

Where the provider paginates, continued scrolling at the loaded end requests one additional batch. The next successful batch extends the list without jumping the current viewport or dropping duplicate occurrences. Empty terminal batches end the feed. Provider requests remain scoped to the active source, query and account.


## Interaction polish

- Continuing a search, library or artist list never replaces visible rows with a new page. On returning from a library detail, the authorized feed and visible row offset are restored, including the partial row where available. The list footer reports loading, retry and end states without restoring page buttons.
- The lyric timeline shares the transport snapshot. Dragging previews matching lyrics without a seek; release commits once for the same occurrence. A manual lyric scroll position is preserved during that temporary preview, and Follow Lyrics jumps back to the live position.
- When a track has a provider-owned artist ID, the artist credit in redesigned Now Playing opens that provider’s matching songs. A row plays sequentially from that occurrence, including duplicate songs; Back returns to its source page. The view never guesses an artist ID from a name.
