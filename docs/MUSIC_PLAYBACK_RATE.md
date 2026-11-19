# Playback rate for the native online player

Open **Now Playing → More → Playback speed** while a supported track is active.
The menu offers 0.5×, 0.75×, 1×, 1.25×, 1.5×, 1.75× and 2×. The default is
1×. The selection belongs to the current player session and carries across tracks
and seeks; it is not written to disk or pushed to the music provider.

- The native MP3 path converts decoded PCM at a fixed device sample rate. It
  changes tempo **and** pitch; this is not pitch-preserving time stretch.
- Switching speeds reopens native playback at the last consumed song position
  using already-authorized audio in memory. This may briefly rebuffer locally
  (especially well into a long file); it does not resolve or download the song
  again. A paused or pause-requested player remains paused.
- Queue, seek, lyrics, HUD and preview offsets use song time. Queued PCM does
  not advance the displayed clock until the audio line consumes it. Foreground
  music snapshots also carry the rate for HUD timeline interpolation.
- Auto crossfade is temporarily inactive at rates other than 1×. It resumes at
  1× if enabled. A change cannot interrupt an overlap that is already mixing;
  wait for the transition, or resume first if paused.
- EQ, per-source effects and session gain stay applied on the same output path.
  At 1× the converter is bypassed. OS media, browser and other external players
  are not affected by the native setting.

`OnlineMusicPlayer.supportsPlaybackRate()` is the capability flag. A supporting
player accepts finite rates in [0.5, 2] through `playbackRate(double)`; inspect
`playbackRate()` for the current preference. Other implementations keep their
existing behavior through default unsupported methods.
