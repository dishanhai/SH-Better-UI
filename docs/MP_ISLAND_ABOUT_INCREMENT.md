# Native island scheduling and About increment

Built on PR #19. Reference source: MuoniumPlayer-multiversion revision
`9b4cc7e7bd143315da716075a6d024a0d4c0c81b`, in particular
`muonium-common/.../rendering/DownloadDynamicIsland.java`.

## Implemented

- Four priority levels (LOW, NORMAL, INSTANT, HIGH), interruption with remaining
  visible budget, FIFO peers, INSTANT peer replacement, and 700 ms protection
  when a suspended notice resumes (HIGH can still interrupt).
- Consumption is confirmed after actual rendering, not visibility polling.
  Hidden surfaces pause this budget. Absolute deadlines remain independent;
  a notification is not kept fresh merely by querying it.
- Stable, persisted classic/pill/minimal IDs drive three real native shell
  renderings. Existing configuration without this field retains its coordinates
  and receives the classic default. These are not all nine upstream styles.
- Opt-in notices project the actual MusicApi lyric request generation and
  provider state. Old generations and superseded tracks are rejected. Loading
  is bounded and never shown as invented byte progress.
- A native SHBUI About page shows actual installed version/platform and a
  snapshot of current media capabilities, with scrolling, narration, source
  notices and confirmation-mediated links.

## Explicit limits

No About implementation was found in the inspected upstream version, so the
About page is an independent SHBUI implementation, not an upstream page port.
No third-party fonts, binaries, Skija libraries, or MP branding are copied.

SHBUI does not expose MP's persistent download/transcode/Automix task backend.
This change therefore does not manufacture those tasks or claim their stall
handling. It does not transplant the upstream 30-second transcode progress
watchdog, download ownership, full surface-to-island morph/handoff, or the nine
upstream styles. The online player still closes its session when its page is
closed. Generic notice durations remain caller-defined rather than forcing
all upstream business-specific minimum durations.

See NATIVE_MUSIC_OVERLAYS.md and NATIVE_ABOUT.md for integration details.
