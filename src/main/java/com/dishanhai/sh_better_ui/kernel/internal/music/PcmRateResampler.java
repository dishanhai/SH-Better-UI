// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

/**
 * Streaming interpolation with a low-pass filter before downsampling.
 * The output device retains its native format. This is varispeed: pitch follows speed.
 */
final class PcmRateResampler {
    static final double MIN_RATE = 0.5, MAX_RATE = 2;
    private final int channels;
    private final double speed;
    private final short[] previous;
    private final LowPass lowPass;
    private short[] filtered = new short[0];
    private long sourceFrames, outputFrames;
    private boolean finished;

    PcmRateResampler(int channels, double speed) {
        this.channels = channels;
        this.speed = checkedRate(speed);
        this.previous = new short[channels];
        this.lowPass = speed > 1 ? new LowPass(channels, speed) : null;
    }

    static double checkedRate(double speed) {
        if (!Double.isFinite(speed) || speed < MIN_RATE || speed > MAX_RATE)
            throw new IllegalArgumentException("Playback rate must be between 0.5 and 2");
        return speed;
    }

    int outputCapacity(int maxInputFrames) {
        // The prior input frame can complete one interpolation interval in this chunk.
        int longestBlock = Math.max(maxInputFrames, lowPass == null ? 0 : LowPass.DELAY);
        return (int) Math.ceil((longestBlock + 1) / speed) + 2;
    }

    int append(short[] input, int frames, short[] output) {
        if (finished) throw new IllegalStateException("Rate converter already finished");
        if (frames == 0) return 0;
        if (lowPass == null) return appendFiltered(input, frames, output);
        reserveFiltered(frames);
        int filteredFrames = lowPass.append(input, frames, filtered);
        return appendFiltered(filtered, filteredFrames, output);
    }

    private void reserveFiltered(int frames) {
        if (filtered.length < frames * channels) filtered = new short[frames * channels];
    }

    private int appendFiltered(short[] input, int frames, short[] output) {
        if (frames == 0) return 0;
        int written = 0;
        long last = sourceFrames + frames - 1;
        for (double cursor = outputFrames * speed; cursor <= last; cursor = ++outputFrames * speed) {
            long lower = (long) cursor;
            double part = cursor - lower;
            int index = (int) (lower - sourceFrames) * channels;
            // When crossing a chunk boundary, interpolate using the final frame of the prior chunk.
            for (int channel = 0; channel < channels; channel++) {
                short a = index < 0 ? previous[channel] : input[index + channel];
                short b = part == 0 ? a : input[index + channels + channel];
                output[written++] = (short) Math.round(a + (b - a) * part);
            }
        }
        System.arraycopy(input, (frames - 1) * channels, previous, 0, channels);
        sourceFrames += frames;
        return written / channels;
    }

    int finish(short[] output) {
        if (finished) return 0;
        finished = true;
        int written = 0;
        if (lowPass != null) {
            reserveFiltered(LowPass.DELAY);
            int frames = lowPass.finish(filtered);
            written = appendFiltered(filtered, frames, output) * channels;
        }
        while (outputFrames * speed < sourceFrames) {
            System.arraycopy(previous, 0, output, written, channels);
            written += channels;
            outputFrames++;
        }
        return written / channels;
    }

    /** Normalized symmetric Blackman-windowed sinc; only used above unity. */
    private static final class LowPass {
        static final int DELAY = 20;
        private static final int TAPS = DELAY * 2 + 1;
        private final int channels;
        private final double[] weights = new double[TAPS];
        private final short[] history, latest;
        private int head;
        private long fed;

        LowPass(int channels, double speed) {
            this.channels = channels;
            history = new short[TAPS * channels];
            latest = new short[channels];
            double cutoff = 0.46 / speed;
            double sum = 0;
            for (int i = 0; i < TAPS; i++) {
                int x = i - DELAY;
                double sinc = x == 0 ? 2 * cutoff : Math.sin(2 * Math.PI * cutoff * x) / (Math.PI * x);
                double angle = 2 * Math.PI * i / (TAPS - 1);
                double window = .42 - .5 * Math.cos(angle) + .08 * Math.cos(2 * angle);
                weights[i] = sinc * window;
                sum += weights[i];
            }
            for (int i = 0; i < TAPS; i++) weights[i] /= sum;
        }

        int append(short[] source, int frames, short[] target) {
            if (fed == 0) for (int i = 0; i < TAPS; i++)
                System.arraycopy(source, 0, history, i * channels, channels);
            int written = 0;
            for (int frame = 0; frame < frames; frame++) {
                if (push(source, frame * channels, target, written * channels)) written++;
            }
            System.arraycopy(source, (frames - 1) * channels, latest, 0, channels);
            return written;
        }

        int finish(short[] target) {
            if (fed == 0) return 0;
            int written = 0;
            for (int i = 0; i < DELAY; i++)
                if (push(latest, 0, target, written * channels)) written++;
            return written;
        }

        private boolean push(short[] input, int at, short[] output, int target) {
            System.arraycopy(input, at, history, head * channels, channels);
            if (++head == TAPS) head = 0;
            // Buffer future samples; replicate only the endpoints so no group delay shifts media time.
            if (++fed <= DELAY) return false;
            for (int channel = 0; channel < channels; channel++) {
                double sum = 0;
                int sample = head;
                for (double weight : weights) {
                    sum += history[sample * channels + channel] * weight;
                    if (++sample == TAPS) sample = 0;
                }
                output[target + channel] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sum)));
            }
            return true;
        }
    }
}
