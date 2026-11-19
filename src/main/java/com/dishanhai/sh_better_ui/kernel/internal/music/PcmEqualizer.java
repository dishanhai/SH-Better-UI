// Author: dishanhai. Native PCM tone shaping, independent of mixer/DSP support.
package com.dishanhai.sh_better_ui.kernel.internal.music;

import java.util.Objects;

/**
 * Playback-thread-owned, bounded three-band tone shaper for interleaved signed-16 PCM.
 * Two one-pole low passes split the signal at 250 Hz and 4 kHz. These are broad relative
 * tone presets, not calibrated graphic EQ bands or Apple Music presets. No allocation
 * occurs while processing; each output instance owns its history.
 */
final class PcmEqualizer {
    enum Preset {
        FLAT(1, 1, 1), BASS_FOCUS(1, .65, .45),
        VOICE_FOCUS(.5, 1, .6), TREBLE_FOCUS(.45, .65, 1);
        final double low, mid, high;
        Preset(double low, double mid, double high) {
            this.low = low; this.mid = mid; this.high = high;
        }
    }

    private final int channels, rampFrames;
    private final double lowAlpha, upperAlpha;
    private final double[] lowState, upperState;
    private Preset requested = Preset.FLAT;
    private double low = 1, mid = 1, high = 1;
    private int remaining;

    PcmEqualizer(int sampleRate, int channels) {
        if (sampleRate < 8000 || sampleRate > 192000 || channels < 1 || channels > 2)
            throw new IllegalArgumentException("Expected 8-192 kHz mono/stereo PCM");
        this.channels = channels;
        this.rampFrames = Math.max(1, sampleRate / 50); // 20 ms, independent of block size.
        this.lowAlpha = 1 - Math.exp(-2 * Math.PI * 250 / sampleRate);
        this.upperAlpha = 1 - Math.exp(-2 * Math.PI * Math.min(4000, sampleRate * .4) / sampleRate);
        lowState = new double[channels];
        upperState = new double[channels];
    }

    /** Must be called on the playback thread; callers publish settings at block boundaries. */
    void preset(Preset value) {
        Objects.requireNonNull(value, "preset");
        if (value != requested) {
            requested = value;
            remaining = rampFrames;
        }
    }

    void process(short[] pcm, int from, int to) {
        Objects.requireNonNull(pcm, "pcm");
        if (from < 0 || to < from || to > pcm.length || from % channels != 0 || to % channels != 0)
            throw new IllegalArgumentException("Expected complete interleaved PCM frames");
        for (int offset = from; offset < to; offset += channels) {
            if (remaining > 0) {
                low += (requested.low - low) / remaining;
                mid += (requested.mid - mid) / remaining;
                high += (requested.high - high) / remaining;
                if (--remaining == 0) {
                    low = requested.low; mid = requested.mid; high = requested.high;
                }
            }
            boolean bypass = remaining == 0 && requested == Preset.FLAT;
            for (int c = 0; c < channels; c++) {
                double sample = pcm[offset + c];
                lowState[c] += lowAlpha * (sample - lowState[c]);
                upperState[c] += upperAlpha * (sample - upperState[c]);
                // Keep filter histories warm even in bypass, but preserve default bits exactly.
                if (!bypass) {
                    double result = low * lowState[c] + mid * (upperState[c] - lowState[c])
                            + high * (sample - upperState[c]);
                    pcm[offset + c] = (short) Math.max(Short.MIN_VALUE,
                            Math.min(Short.MAX_VALUE, Math.round(result)));
                }
            }
        }
    }
}
