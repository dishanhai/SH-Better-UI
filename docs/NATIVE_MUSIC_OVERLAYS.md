# Native music overlay host

The control center's Music category now opens **Music HUD and island**. Both
surfaces are opt-in, independent of the optional MuoniumPlayer settings bridge.
They read SHBUI's existing MusicApi snapshot and do not enable audio capture,
start network playback, or copy MP assets.

The module owns a scoped `UIApis.HUD` mount and a versioned persisted
`sh_better_ui:music_overlay` configuration. It stops drawing without a world or
player, with F1 HUD hiding, while the player list is held, and while another
screen is open. It never consumes gameplay pointer or keyboard input.

Each surface has independent normalized position and 0.5–2.0 size. Positions
refer to the remaining travel distance after the surface size is deducted.
The dedicated position editor previews real current music, allows dragging,
keyboard arrows, and wheel / +/- resizing at the top-left anchor (clamped to
bounds). Runtime and editor share full-screen geometry, so saving a position does
not shift the surface. Editor buttons overlay the bottom of the preview; use
keyboard arrows to move a surface behind those buttons. Island compact height
is floored at 29 GUI units so the 0.5 size remains renderable (subject to the
actual screen bounds).
Confirm only updates the outer settings draft; Cancel/Escape discards the
position edit. The outer Apply persists settings. Errors remain visible and
leave the draft available for retry.

The island can be kept expanded. An optional compact ambient display uses only
Minecraft's cached FPS and player latency plus the local wall clock, sampled
at most four times a second. No network probes are generated.

This is an independently hosted first migration, **not full MP HUD parity**:
there is no audio DSP, timeline seeking, queue playback control, world-space
lyrics, or MP theme/asset transplantation. The host does not invent progress
notifications for downloads it does not own. On very small windows the renderer
reduces available bounds; a visual runtime check remains required for supported
GUI scales and screen sizes.

## Real task notices and scene lifetime

The optional **Task notices** setting projects the existing system MusicApi's
immutable frames: lyric loading, lyric availability/unavailability and temporary
provider failure/recovery. These are local-lyric/media observations, not online
song downloads. Loading has no percentage and a bounded 8-second notice TTL;
completion is shown only after an observed loading state, never for an initially
cached result. Matching uses provider/session/track identity and the actual lyrics
request revision. Older revisions cannot replace a newer song's feedback.
Changing songs cancels the obsolete notice. Pausing the game overlay for a screen,
F1 or Tab does not destroy its queue, but wall-clock expiry still prevents old
feedback appearing much later. Leaving the world, disabling the island or module
shutdown clears state. ClientMusic currently creates one service per process and
cannot restart after shutdown; this host resets its observer at world boundaries.

The shell selector persists stable IDs `classic`, `pill`, `minimal`, not enum
ordinals. Missing/null style in existing configuration defaults to `classic`
without resetting coordinates or dimensions. These native shell variants do not
claim MP theme/animation parity or import third-party fonts.

Backend audit: OnlinePlayerSession downloads audio internally but publishes no
byte-progress task API. OnlineMusicScreen closes its owned session on removal.
Consequently this increment does not claim background-download percentages or
player-to-island continuing playback/handoff. Those require an explicit durable
player owner and actual backend task events, rather than cosmetic notifications.

### In-world Island playback shortcuts (Forge 1.20.1 / NeoForge 1.21.1)

In Options → Controls → Key Binds → SHBUI, bind the Island toggle and the
Island playback shortcuts you want. They are unbound by default. Expand the
island while it shows an SH Music track to play/pause, skip to the previous or
next track, or seek back/forward by 10 seconds. The existing Open SH Music
shortcut opens the complete player. These controls do not open a session, start
a new capture, or control an external system player. Notifications that cover
the track, background/minimized Minecraft, chat, other screens, player list,
and hidden HUD disable the controls. Choose non-conflicting keyboard bindings;
there is no HUD mouse capture.

### Open the displayed Island task (Forge 1.20.1 / NeoForge 1.21.1)

In **Options → Controls → Key Binds → SHBUI**, bind **Open displayed Music
Island task** (unbound by default). Use it while the island displays a live
background operation notice in the world. It opens the matching row in SH
Music History, not the result, account or playback action itself. The key
does nothing for media and lyric-only notices, an expired task, or while
the HUD is hidden. It never starts or retries a search, favorite update or
QR sign-in. Use a key that does not conflict with another gameplay action.
