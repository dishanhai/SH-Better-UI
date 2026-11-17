# QQ Music inspired presentation

## Reference and scope

User references: a mobile warm-wood turntable with album sleeve/record and white
transport controls; a desktop color-wash library with a sidebar, restrained song
rows and persistent bottom playback controls. The official web navigation and
content hierarchy were consulted at <https://y.qq.com/> (2026-11-17).
No QQ logo, artist image, proprietary source, font or screenshot is distributed.
The reference site was inspected for interaction/layout only; no license to its
assets is assumed. Existing provider-owned album art remains borrowed through
the current artwork loader. No new music provider or network request is added.

## Native usage

Select `QQ Music · Aurora` or `QQ Music · Vinyl` in the existing Themes browser.
They are registered UUK themes, so existing theme persistence, preview and
return-to-previous-theme behavior remain in effect. The control panel's classic
layout option and existing Apple-inspired player remain available. Changing away
from these presets restores the previous ordinary player presentation.

Aurora gives the player a continuous dark multicolor canvas, translucent sidebar
and green playback emphasis. Vinyl additionally replaces the immersive cover
with an original procedural record/turntable. Click the now-playing cover to
enter the existing immersive page; existing queue, lyric, seek and volume actions
are retained. No fake VIP, download or comments functionality is added.

`MusicVinylStage` is a reusable UUK presentation primitive. It borrows a texture,
never owns/downloads it, and samples host playback state. Rotation freezes when
paused or reduced motion is enabled. High contrast bypasses decorative backdrops
and the turntable. Geometry cost is bounded (nine record grooves); no FBO,
readback, blur pass or image generation is required.

## Web counterpart

See `reference/qq-music/` for a standalone original HTML/CSS/JS interaction study.
Its demo data/player are explicitly simulated, not an authenticated QQ service.
It is a design counterpart, not a claim of native screenshot parity.

## Remaining visual work

The native turntable is intentionally procedural 2D, not a reproduction of the
reference's photorealistic 3D room. Real-game screenshots and broad native
interaction/visual acceptance are still required; compilation and unit tests
must not be presented as that acceptance.

## Verification (2026-11-17)

Forge 1.20.1 and NeoForge 1.21.1 compilation and each 17 focused tests passed:
3 QQ theme registration/gallery/selection tests, 6 existing Muonium theme tests,
6 existing player-screen lifecycle tests and 2 vinyl motion tests. These are not
full-suite or real-game visual results. Web JavaScript syntax and 12 DOM-stub
interaction tests passed. Headless Chromium could not start because process
sockets were denied, including an approved retry; web render screenshots remain
unverified. No native gameplay screenshots were produced for this change.

### Native follow-up acceptance

Subsequently launched the actual NeoForge 1.21.1 client on the Dot desktop
(OpenGL 4.5 llvmpipe, 1180×812, GUI scale 2). Confirmed both presets visible in
the theme gallery; applied Aurora through the UI and reopened the normal player.
Verified real NetEase search results and artwork, list scrolling, entering Vinyl
immersive view and switching queue/lyrics, and returning to the same search.
Saved unmodified Minecraft F2 screenshots of Vinyl, Aurora and the theme gallery.
No obvious overlap was observed at this size. The selected provider returned no
playable audio for the selected song; the UI correctly retained the unavailable
message. Thus real audio-driven rotation/playback, small-window native behavior,
Forge gameplay visuals and web rendering remain unverified. The earlier
no-native-screenshot statement describes the initial unit-test phase only.
