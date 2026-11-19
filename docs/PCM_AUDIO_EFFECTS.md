# Native PCM tone effects: first backend slice

## Implemented scope

`Mp3Output` now applies a bounded, allocation-free-per-block tone stage before
`PcmGain` and Java Sound output. It only processes signed 16-bit mono/stereo PCM
that the existing MP3 decoder actually produces. It does not add a new codec or
source, capture browser audio, or change source authorization.

`PcmEqualizer` provides FLAT, BASS_FOCUS, VOICE_FOCUS and TREBLE_FOCUS. These are
broad relative tone presets using two one-pole low-pass splits (250 Hz and 4 kHz,
with the upper split limited at low sample rates), not calibrated graphic EQ
bands. They attenuate complementary bands instead of requesting positive gain;
transients are saturated to the signed-16 range. There is no loudness, spatial
sound, Dolby Atmos or Apple preset claim.

The default remains FLAT, preserving PCM samples bit-for-bit. Switching presets
ramps gains over 20 ms, independent of decoder block boundaries. Stereo channels
have independent histories. Each output owns its state; replacing output for a
track or seek creates fresh histories. Filter histories remain warm in FLAT.
Format changes midstream are rejected instead of feeding incompatible PCM to the
already-open device. Existing session volume/mute remains downstream.

## Public session control and player menu

`OnlineMusicPlayer.supportsEqualizer()` reports whether the current output supports
these effects. `equalizerPreset(OnlineMusicEqualizerPreset)` selects a named preset;
`equalizerPreset()` returns the session's remembered preference. The preference is
reapplied before starting replacement supported outputs on seek/track changes.
There is no persistence on disk and a new session starts FLAT.

Without a current output (including idle/loading before creation), or with an
unsupported custom/browser output, the capability is false and the setter throws
`UnsupportedOperationException`. The getter still reports the remembered setting,
not a claim that unsupported playback is applying it. Public default implementations
and the output boundary default to unsupported; adapters must explicitly opt in.
The session only remembers a new preset after its output accepts it. The Now Playing menu rechecks capability when opening the preset chooser and before applying a choice.

The Java Sound implementation publishes a volatile preset which its playback
thread consumes at block boundaries. The Equalizer submenu lists the current preset
and is hidden when the active output cannot apply it.

## Apple Music boundary

Apple Music protected playback hosted by a browser/MusicKit is outside this PCM
pipeline. These presets cannot affect that playback. A provider returning a
legitimately accessible, supported MP3 preview can use this stage if it is routed
through the native decoder, but this does not imply full-song/DRM, AAC, lossless,
or spatial-audio support. No DRM circumvention is provided.

## Verification

`PcmEqualizerTest` covers exact FLAT output across all signed-16 values, tone
selectivity, stereo isolation, frame-boundary validation, subrange isolation,
20 ms transitions, processing-block invariance, silence across supported sample
rates, and downstream mute. Existing `PcmGainTest` also passes. Session tests cover capability rejection,
selection, seek/track replacement, unsupported-output switching, and remembered
preferences. All 30 tests in these three classes pass against the NeoForge
1.21.1 compiled dependency boundary. These are pure
Java numerical tests, not audio-device, in-game, listening, or Apple Music tests.
