# Native queue crossfade

In **Music → Queue → Crossfade**, choose Off or 1–12 seconds. This preference is
off by default and applies to the current player session. The slider previews
its value while held; it applies the selection only on release or a keyboard
step. Esc cancels a drag, then returns to the queue.

Crossfade is for automatic sequential and repeat-all advancement of SHBUI's
native MP3 streams from the same provider (QQ Music or NetEase). It does not affect Apple Music in MusicKit Web, the
Spotify Web Player, previews, manually skipped tracks, shuffle or repeat-one.
It is not tempo/key analysis or Apple AutoMix.

## Audio and queue boundaries

The session resolves the next song using the usual source authorization
before reusing cached audio. A background worker decodes the next head and
verifies the outgoing tail against EOF independently of the active device
worker. The active decoder keeps delivering dry audio until a compatible
offer is ready. Incoming and outgoing sample rate and channel count must
match. Short tracks, incompatible formats or late audio falls back to normal queue
advance without fading to silence on its own.

The native renderer overlaps interleaved PCM frames with complementary linear
gains on one Java Sound output line. The line, combined volume/mute,
equalizer, provider sound effect and activity meter stay in place. A provider-to-provider
transition never mixes audio or carries the previous provider's effects. The queue cursor, track metadata
and playback clock adopt the successor only after the device has consumed the
overlap. Successor position includes its already-heard opening.

Before the output claims the overlap, queue changes can cancel and replace
it. Once mixed audio has been submitted, queue-only edits wait for the handoff
rather than silently changing the successor; try the action again after the
transition. Seek, play, stop and manual next/previous can interrupt playback.
Turning the setting off while mixing lets the current transition finish and
prevents later transitions. Pause freezes the single device, including any
pending audible overlap.

## References

The independent implementation combines the scheduling idea in
[Soundscape's desktop engine](https://github.com/NadeemIqbal/soundscape/blob/85086d491b7f1aba0b037f8541b77852ef2bc90c/player/src/desktopMain/kotlin/io/github/nadeemiqbal/soundscape/player/DesktopPlayer.kt#L208-L277)
with the single-buffer PCM mixing idea in
[Rockbox's PCM output](https://github.com/Rockbox/rockbox/blob/master/apps/pcmbuf.c).
It does not import code from either project.

Before release, listen to a long same-provider pair starting with an empty
audio cache. A slow download or a long outgoing-tail decode may miss the
preparation window and trigger normal playback instead of crossfade.
