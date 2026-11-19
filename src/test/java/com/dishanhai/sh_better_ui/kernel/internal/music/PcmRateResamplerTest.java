// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class PcmRateResamplerTest {
    @Test void slowStereoInterpolatesAcrossInputChunksWithoutMixingChannels() {
        var converter = new PcmRateResampler(2, 0.5);
        var output = new short[converter.outputCapacity(2) * 2];
        assertEquals(1, converter.append(new short[]{0, 100}, 1, output));
        assertArrayEquals(new short[]{0,100}, Arrays.copyOf(output, 2));
        assertEquals(4, converter.append(new short[]{10, 120, 20, 140}, 2, output));
        assertArrayEquals(new short[]{5, 110, 10, 120, 15, 130, 20, 140}, Arrays.copyOf(output, 8));
        assertEquals(1, converter.finish(output));
        assertArrayEquals(new short[]{20, 140}, Arrays.copyOf(output, 2));
        assertEquals(0, converter.finish(output));
    }

    @Test void fasterRatesKeepSinglePhaseAndExpectedDurationAcrossLongUnevenBlocks() {
        for (double rate : new double[]{0.5, 0.75, 1.25, 1.5, 1.75, 2.0}) {
            var converter = new PcmRateResampler(1, rate);
            var output = new short[converter.outputCapacity(511)];
            short[] entire = new short[10003];
            int written = 0;
            for (int at = 0; at < entire.length;) {
                int frames = Math.min(511, entire.length - at);
                short[] input = new short[frames];
                for (int i = 0; i < frames; i++) input[i] = (short) (at + i);
                int emitted = converter.append(input, frames, output);
                for (int i = 0; i < emitted; i++)
                    assertEquals(Math.round((written + i) * rate), output[i]);
                written += emitted;
                at += frames;
            }
            written += converter.finish(output);
            assertEquals(Math.ceil(entire.length / rate), written);
        }
    }

    @Test void invalidRateIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PcmRateResampler.checkedRate(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> PcmRateResampler.checkedRate(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> PcmRateResampler.checkedRate(0.49));
        assertThrows(IllegalArgumentException.class, () -> PcmRateResampler.checkedRate(2.01));
    }
}
