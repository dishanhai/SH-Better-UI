# Native music transport and artwork increment

The unified music activity now projects controls from the existing background player:
play/pause, previous/next where the queue allows them, and seek only for seekable
non-preview playback. System metadata and other providers remain read-only.
Querying controls never creates a player. Actions are checked against the current
foreground source/session/track before execution; stale actions are not redirected
to a different song. The activity's open-player action remains available.

Artwork is supplied by the existing allowlisted online artwork downloader, off the
render thread. Source dimensions and encoded size are bounded; normalized frames
are at most 240 px and 256 KiB, with an eight-entry memory cache. Native texture
upload/release happens on the render thread. Content-addressed icon IDs prevent
old crossfade layers from displaying a newer song's cover. Reload, suspend and
shutdown invalidate texture ownership. A missing cover leaves the text usable.

Screen Dock persistence is documented in DOCK-PERSISTENCE.md. These additions do
not implement glass/HUD Dock, standalone music HUD, spectrum, or the full legacy
rich-media editor. Static rich-document support remains the preceding increment.

Validation: production compilation and headless tests cover action admission,
queue boundaries, stale source/track targets, cover bounds and cancellation, and
persistent placement reload. GPU upload, actual playback, live accounts and visual
quality need in-game acceptance; headless tests do not establish those properties.
