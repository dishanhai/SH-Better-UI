// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

/**
 * Streaming linear interpolation at a fixed source-frames-per-output-frame ratio.
 * The output device retains its native format. This is varispeed: pitch follows speed.
 */
final class PcmRateResampler {
    static final double MIN_RATE = 0.5, MAX_RATE = 2;
    private final int channels;
    private final double speed;
    private final short[] previous;
    private long sourceFrames, outputFrames;
    private boolean finished;

    PcmRateResampler(int channels, double speed) {
        this.channels = channels;
        this.speed = checkedRate(speed);
        this.previous = new short[channels];
    }

    static double checkedRate(double speed) {
        if (!Double.isFinite(speed) || speed < MIN_RATE || speed > MAX_RATE)
            throw new IllegalArgumentException("Playback rate must be between 0.5 and 2");
        return speed;
    }

    int outputCapacity(int maxInputFrames) {
        // The prior input frame can complete one interpolation interval in this chunk.
        return (int) Math.ceil((maxInputFrames + 1) / speed) + 1;
    }

    int append(short[] input, int frames, short[] output) {
        if (finished) throw new IllegalStateException("Rate converter already finished");
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
        while (outputFrames * speed < sourceFrames) {
            System.arraycopy(previous, 0, output, written, channels);
            written += channels;
            outputFrames++;
        }
        return written / channels;
    }
}
