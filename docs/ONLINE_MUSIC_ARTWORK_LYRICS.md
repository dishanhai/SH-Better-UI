# Online music: provider artwork and lyric presentation

The Forge 1.20.1 and NeoForge 1.21.1 music view now uses provider album artwork in
both the persistent player and the wide lyric page. `OnlineTrack.artworkUrl()` is
optional; the existing seven-argument constructor remains available. NetEase uses
album `picUrl` (requesting a 512px thumbnail for bare URLs); QQ uses album MID,
not a guessed song title. No album artwork is included in the distribution.

Artwork uses a separate unauthenticated HTTP client, exact provider CDN host
validation on every redirect, a 2 MiB streaming byte limit, and a 20-second total
download deadline. Only bounded PNG/JPEG images are decoded, off the rendering
thread, and normalized to at most 512px. Each view owns one in-memory texture;
switching artwork invalidates stale asynchronous work. Close/reload releases the
texture. There is no disk cache or credentials attached to CDN requests. A
missing/blocked/oversized/failed cover retains the vector fallback. A failed URL
is not retried every frame; changing URL or invalidating/reloading allows retry.

Lyrics are borderless native keyboard/narrator targets instead of glass buttons.
The wide layout presents artwork and track metadata at left, lyrics at right;
compact layouts reserve the available width for text. Automatic following is
smooth, with immediate positioning under reduced motion. Existing word timing,
translations, preview offsets, seeking and playback controls are preserved.
Mouse-wheel browsing suspends following for five seconds; the follow control
returns immediately. Keyboard focus freezes targets so they cannot move under
activation. Clicking a line verifies the rendered track/time axis is still
current before seeking. Full clipped lines and translation remain in tooltips.

This presentation is inspired by MuoniumPlayer's MusicLyricsPanel and the user's
QQ Music layout reference. No QQ Music art, fonts, icons or shaders were copied.
Existing Muonium Apache-2.0 LICENSE/NOTICE/SOURCE attribution remains bundled.

## Verification limits

Automated checks do not constitute an in-game visual or authenticated-provider
acceptance test. Real CDN availability depends on the user's connection and
provider metadata. The experimental 26.x port retains an explicit empty artwork
adapter; it is not advertised as supporting remote covers in this change.
