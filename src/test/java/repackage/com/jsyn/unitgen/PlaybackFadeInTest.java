// Author: dishanhai
package repackage.com.jsyn.unitgen;

import org.junit.jupiter.api.Test;
import repackage.com.jsyn.Synthesizer;
import repackage.com.jsyn.data.FloatSample;
import repackage.com.jsyn.engine.SynthesisEngine;
import repackage.com.jsyn.ports.QueueDataEvent;
import repackage.com.jsyn.ports.UnitDataQueueCallback;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaybackFadeInTest {
    private static final int RATE = 48_000;
    private static final double FADE_SECONDS = 0.006;

    @Test
    void monoEntryStartsAtSilenceAndReachesCurrentVolume() {
        checkEntry(new VariableRateMonoReader(), 1);
    }

    @Test
    void stereoUsesTheSameRampOnBothChannelsAndEveryNewEntry() {
        checkEntry(new VariableRateStereoReader(), 2);
    }

    private static void checkEntry(VariableRateDataReader reader, int channels) {
        var synth = new SynthesisEngine(null);
        synth.setRealTime(false);
        synth.start(RATE, -1, 0, -1, channels);
        try {
            var samples = new float[4096 * channels];
            for (int i = 0; i < samples.length; i++) {
                samples[i] = i % channels == 0 ? 0.5f : -0.25f;
            }
            var sample = new FloatSample(samples, channels);
            sample.setFrameRate(RATE);
            synth.add(reader);
            reader.rate.set(RATE);
            for (int channel = 0; channel < channels; channel++) {
                var output = new ChannelOut();
                output.setChannelIndex(channel);
                synth.add(output);
                reader.output.connect(channel, output.input, 0);
                output.start();
            }
            int fullVolumeFrame = (int) (FADE_SECONDS * RATE);
            double[] rendered = new double[(fullVolumeFrame + Synthesizer.FRAMES_PER_BLOCK + 1) * channels];
            int[] sampleCount = {0};
            synth.setOutputObserver((pcm, count, rate) -> {
                int length = Math.min(pcm.length, rendered.length - sampleCount[0]);
                System.arraycopy(pcm, 0, rendered, sampleCount[0], length);
                sampleCount[0] += length;
            });

            for (double volume : new double[]{0.4, 0.25}) {
                sampleCount[0] = 0;
                long captureFrame = synth.getFrameCount();
                int[] entryFrame = {-1};
                reader.amplitude.set(volume);
                reader.dataQueue.clear();
                var entry = reader.dataQueue.createQueueDataCommand(sample);
                entry.setCallback(new UnitDataQueueCallback() {
                    @Override public void started(QueueDataEvent event) {
                        entryFrame[0] = (int) (synth.getFrameCount() - captureFrame);
                        reader.beginFadeIn(FADE_SECONDS);
                    }
                    @Override public void finished(QueueDataEvent event) { }
                    @Override public void looped(QueueDataEvent event) { }
                });
                synth.queueCommand(entry);
                for (int buffer = 0; buffer < 16 && sampleCount[0] < rendered.length; buffer++) {
                    synth.generateNextBuffer();
                }
                assertEquals(rendered.length, sampleCount[0]);
                for (int channel = 0; channel < channels; channel++) {
                    double expected = samples[channel] * volume;
                    int offset = entryFrame[0] * channels + channel;
                    assertEquals(0.0, rendered[offset], 1e-9);
                    assertEquals(expected / 2, rendered[offset + fullVolumeFrame / 2 * channels], 1e-9);
                    assertEquals(expected, rendered[offset + fullVolumeFrame * channels], 1e-9);
                }
            }
        } finally {
            synth.stop();
        }
    }
}
