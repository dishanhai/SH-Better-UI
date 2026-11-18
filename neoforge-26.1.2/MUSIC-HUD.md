# Native music HUD and layout controls

The optional in-world music information card is independent of the unified music
activity/island. Both remain off by default. The card observes the existing real
foreground frame; it never creates a player, registers a synthetic provider or
starts playback. It displays metadata, artwork, known progress and word-timed
lyrics through the native player lyric projection. A paused real session remains
visible and is marked paused. Missing sessions do not create demo music.

The music overlays settings page exposes HUD enable, normalized horizontal and
vertical position, and panel size (50–200%). Size changes reflow geometry, not
font glyph scaling. Slider drags commit on release; cancellation retains exact
stored values. Updates preserve all other fields in the shared overlay settings.
The layout reset has an explicit undo and changes only HUD position and size.
These settings use the existing kernel configuration persistence.

The settings-only layout preview is explicitly labelled as a static example,
not playback. It uses the same panel geometry, but never publishes metadata or
reserves a global HUD region. It makes layout adjustment possible without an
account or audio device.

The actual HUD reserves only its visible rectangle through the existing live
surface arbiter so the island can avoid it. Menus, blocking overlays, hidden GUI,
player list, loss of focus and logout suppress it without closing the background
player. Artwork has a single native texture owner shared with the island and is
prepared once before GUI extraction, including frames where GUI drawing is
suppressed. Reload/logout/shutdown release resources. Cover geometry preserves
aspect ratio and samples the complete texture.

This does not add audio spectrum/ambient effects, remote providers or all legacy
HUD presentation styles. No fake audio features are synthesized. The broader
migration still needs player background/theme customization, custom font-file
management, advanced rich media, higher-level Dock features and optional-mod/API
compatibility. NeoForge26.2 remains separately unverified.
