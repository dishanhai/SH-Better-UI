// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSoundEffect;

import java.util.Objects;

/**
 * Original local DSP for interleaved signed-16 mono/stereo. One playback worker owns all
 * history. Presets blend over 30 ms, including interruptions before a ramp completes.
 */
final class PcmSoundEffects {
    private static final int BASS = OnlineMusicSoundEffect.SUPER_BASS.ordinal();
    private static final int VOCALS = OnlineMusicSoundEffect.CLEAR_VOCALS.ordinal();
    private static final int LIVE = OnlineMusicSoundEffect.LIVE_RHYTHM.ordinal();
    private static final int WIDE = OnlineMusicSoundEffect.WIDE_STEREO.ordinal();
    private static final int HALL = OnlineMusicSoundEffect.VIRTUAL_CONCERT_HALL.ordinal();
    private final int channels, rampFrames, early, late, tail;
    private final double lowAlpha, upperAlpha, attack, release;
    private final double[] weights = new double[OnlineMusicSoundEffect.values().length];
    private final double[] low, upper;
    private final short[][] delay;
    private int cursor, remaining;
    private OnlineMusicSoundEffect target = OnlineMusicSoundEffect.OFF;
    private double peak;

    PcmSoundEffects(int sampleRate, int channels) {
        if (sampleRate < 8000 || sampleRate > 192000 || channels < 1 || channels > 2)
            throw new IllegalArgumentException("Expected 8-192 kHz mono/stereo PCM");
        this.channels = channels;
        this.rampFrames = sampleRate * 30 / 1000;
        lowAlpha = coefficient(sampleRate, 1 / (2 * Math.PI * 180));
        upperAlpha = coefficient(sampleRate, 1 / (2 * Math.PI * Math.min(3200, sampleRate * .4)));
        attack = coefficient(sampleRate, .004);
        release = coefficient(sampleRate, .080);
        early = sampleRate * 17 / 1000;
        late = sampleRate * 31 / 1000;
        tail = sampleRate * 47 / 1000;
        low = new double[channels];
        upper = new double[channels];
        delay = new short[channels][tail + 1];
        weights[0] = 1;
    }

    private static double coefficient(int rate, double seconds) {
        return -Math.expm1(-1 / (rate * seconds));
    }

    void preset(OnlineMusicSoundEffect value) {
        Objects.requireNonNull(value, "effect");
        if (value == target) return;
        target = value;
        remaining = rampFrames;
    }

    void process(short[] pcm, int from, int to) {
        if (from < 0 || to < from || to > pcm.length || from % channels != 0 || to % channels != 0)
            throw new IllegalArgumentException("Expected complete interleaved PCM frames");
        for (int offset = from; offset < to; offset += channels) {
            if (remaining > 0) {
                for (int i = 0; i < weights.length; i++)
                    weights[i] += ((i == target.ordinal() ? 1d : 0d) - weights[i]) / remaining;
                --remaining;
            }
            boolean bypass = remaining == 0 && target == OnlineMusicSoundEffect.OFF;
            int left = pcm[offset], right = channels == 2 ? pcm[offset + 1] : left;
            int framePeak = Math.max(Math.abs(left), Math.abs(right));
            peak += (framePeak > peak ? attack : release) * (framePeak - peak);
            double liveGain = peak <= 14746 ? 1 : .5 + 7373 / peak;
            int echoEarly = index(early), echoLate = index(late), echoTail = index(tail);
            for (int c = 0; c < channels; c++) {
                int sample = c == 0 ? left : right;
                int other = c == 0 ? right : left;
                low[c] += lowAlpha * (sample - low[c]);
                upper[c] += upperAlpha * (sample - upper[c]);
                if (!bypass) {
                    double bass = .49 * sample + .50 * low[c];
                    double vocals = .43 * sample + .28 * (upper[c] - low[c]);
                    double live = (.74 * sample + .19 * low[c] + .065 * (sample - upper[c])) * liveGain;
                    // Stereo widening keeps mono input bit-identical; headroom only follows side signal.
                    double side = (sample - other) * .5;
                    double wide = sample + .25 * side * (1 - Math.abs(sample) / 32768d);
                    int cross = channels - 1 - c;
                    double hall = .78 * sample + .10 * delay[c][c == 0 ? echoEarly : echoLate]
                            + .07 * delay[cross][c == 0 ? echoLate : echoEarly] + .04 * delay[c][echoTail];
                    double result = weights[0] * sample + weights[BASS] * bass + weights[VOCALS] * vocals
                            + weights[LIVE] * live + weights[WIDE] * wide + weights[HALL] * hall;
                    pcm[offset + c] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(result)));
                }
                // Always retain the dry signal, including in bypass, so activation never creates an artificial gap.
                delay[c][cursor] = (short) sample;
            }
            if (++cursor == delay[0].length) cursor = 0;
        }
    }

    private int index(int back) {
        int i = cursor - back;
        return i < 0 ? i + delay[0].length : i;
    }
}
