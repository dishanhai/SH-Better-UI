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
