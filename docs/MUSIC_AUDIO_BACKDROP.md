# Playback-scoped artwork response

The ordinary immersive artwork background uses `OnlineMusicPlayer.audioActivity()`.
Its default is zero: an external/protected provider without an explicit PCM activity
implementation stays still rather than animating a fictional spectrum. This does not
implement Apple Music playback, Spatial Audio, or analysis of protected audio.

The built-in MP3 output measures signed-16, interleaved, post-volume PCM blocks after
writing them to its current audio device. Channel powers are combined, so opposite
stereo signals do not cancel. No audio is captured from the operating system, saved,
uploaded, copied into notifications, or retained beyond the block already used for
playback. Only a normalized RMS value and monotonic timestamp cross threads. Samples
older than 250 ms expire; paused/failed/closed output is not exposed to the UI. This
is an approximate output activity envelope, not a measured speaker waveform or BPM.

The artwork palette mesh follows that envelope with 65 ms attack, 80 ms hold, and
260 ms release. There is no free-running clock in the rendered audio-driven phase.
Reduced motion, high contrast, and low-performance rendering remain static. Artwork
palette transitions remain independent of audio. Existing `renderBackground` callers
retain their prior ambient behavior; the player explicitly selects the new
`renderAudioBackground` overload on both legacy and Neo26 implementations.

The current implementation affects the immersive artwork background only. QQ's
separate background remains unchanged. It does not add EQ, alter PCM, change device
ownership, or move controls/hitboxes.

Verification: deterministic `PcmActivityTest` and `AudioBackdropMotionTest` exercise
silence, output expiry, stereo power, volume-scaled samples, attack/hold/release,
non-finite input, and static accessibility fallbacks. Real audio/device and in-game
visual verification must be reported separately from these tests.
