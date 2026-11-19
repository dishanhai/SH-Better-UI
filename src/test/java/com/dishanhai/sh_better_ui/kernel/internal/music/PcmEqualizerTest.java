package com.dishanhai.sh_better_ui.kernel.internal.music;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class PcmEqualizerTest {
    @Test void defaultFlatIsBitExactForEverySignedSample() {
        short[] pcm = new short[65536];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (short) i;
        short[] original = pcm.clone();
        new PcmEqualizer(44100, 2).process(pcm, 0, pcm.length);
        assertArrayEquals(original, pcm);
    }

    @Test void presetsProduceFrequencySelectivePcmNotJustVolume() {
        assertTrue(response(PcmEqualizer.Preset.BASS_FOCUS, 80) > response(PcmEqualizer.Preset.BASS_FOCUS, 8000) * 1.5);
        assertTrue(response(PcmEqualizer.Preset.TREBLE_FOCUS, 8000) > response(PcmEqualizer.Preset.TREBLE_FOCUS, 80) * 1.5);
        assertTrue(response(PcmEqualizer.Preset.VOICE_FOCUS, 1000) > response(PcmEqualizer.Preset.VOICE_FOCUS, 80) * 1.3);
    }

    @Test void blockPartitionDoesNotChangeSamplesOrRampDuration() {
        short[] a = new short[12000];
        Random random = new Random(3);
        for (int i = 0; i < a.length; i++) a[i] = (short) random.nextInt();
        short[] b = a.clone();
        var whole = new PcmEqualizer(48000, 2);
        var chunks = new PcmEqualizer(48000, 2);
        whole.preset(PcmEqualizer.Preset.VOICE_FOCUS);
        chunks.preset(PcmEqualizer.Preset.VOICE_FOCUS);
        whole.process(a, 0, a.length);
        for (int i = 0; i < b.length; i += 100) {
            chunks.preset(PcmEqualizer.Preset.VOICE_FOCUS); // repeated publication must not restart ramp
            chunks.process(b, i, Math.min(i + 100, b.length));
        }
        assertArrayEquals(a, b);
    }

    @Test void stereoHistoryDoesNotLeakIntoSilentChannel() {
        short[] pcm = new short[48000];
        for (int i = 0; i < pcm.length; i += 2) pcm[i] = 12000;
        var eq = new PcmEqualizer(48000, 2);
        eq.preset(PcmEqualizer.Preset.BASS_FOCUS);
        eq.process(pcm, 0, pcm.length);
        for (int i = 1; i < pcm.length; i += 2) assertEquals(0, pcm[i]);
    }

    @Test void transitionBackToFlatBecomesExactAfterTwentyMilliseconds() {
        var eq = new PcmEqualizer(48000, 1);
        eq.preset(PcmEqualizer.Preset.TREBLE_FOCUS);
        short[] warmup = new short[4800]; Arrays.fill(warmup, (short) 10000);
        eq.process(warmup, 0, warmup.length);
        eq.preset(PcmEqualizer.Preset.FLAT);
        short[] pcm = new short[1920]; Arrays.fill(pcm, (short) 10000);
        eq.process(pcm, 0, pcm.length);
        assertTrue(pcm[0] < 6000); // no instantaneous jump to unity
        for (int i = 1; i < 960; i++) assertTrue(Math.abs(pcm[i] - pcm[i - 1]) < 30);
        for (int i = 959; i < pcm.length; i++) assertEquals(10000, pcm[i]);
    }

    @Test void rangeAndFormatValidationPrecedesMutation() {
        var eq = new PcmEqualizer(44100, 2);
        short[] pcm = {1, 2, 3, 4};
        assertThrows(IllegalArgumentException.class, () -> eq.process(pcm, 1, 4));
        assertThrows(IllegalArgumentException.class, () -> eq.process(pcm, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> eq.process(pcm, -2, 4));
        assertThrows(IllegalArgumentException.class, () -> eq.process(pcm, 0, 6));
        assertArrayEquals(new short[]{1,2,3,4}, pcm);
        assertThrows(IllegalArgumentException.class, () -> new PcmEqualizer(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new PcmEqualizer(48000, 3));
        assertThrows(NullPointerException.class, () -> eq.preset(null));
    }

    @Test void processingSubrangeLeavesSurroundingSamplesUntouched() {
        short[] pcm = {123, 456, 30000, -30000, 789, 1011};
        var eq = new PcmEqualizer(44100, 2);
        eq.preset(PcmEqualizer.Preset.VOICE_FOCUS);
        eq.process(pcm, 2, 4);
        assertEquals(123, pcm[0]); assertEquals(456, pcm[1]);
        assertEquals(789, pcm[4]); assertEquals(1011, pcm[5]);
    }

    @Test void silenceStaysSilentAcrossPresetsAndSampleRates() {
        for (int rate : new int[]{8000, 16000, 22050, 44100, 48000, 192000}) {
            var eq = new PcmEqualizer(rate, 1);
            for (var preset : PcmEqualizer.Preset.values()) {
                eq.preset(preset);
                short[] pcm = new short[10000];
                eq.process(pcm, 0, pcm.length);
                assertArrayEquals(new short[pcm.length], pcm);
            }
        }
    }

    @Test void downstreamVolumeMuteStillOverridesEffects() {
        short[] pcm = {Short.MIN_VALUE, Short.MAX_VALUE, 1000, -1000};
        var eq = new PcmEqualizer(44100, 2);
        eq.preset(PcmEqualizer.Preset.BASS_FOCUS);
        eq.process(pcm, 0, pcm.length);
        byte[] out = new byte[8];
        PcmGain.write(pcm, 0, pcm.length, 2, out, 0, 0);
        assertArrayEquals(new byte[8], out);
    }

    private static double response(PcmEqualizer.Preset preset, double frequency) {
        short[] pcm = new short[48000];
        double input = 0, output = 0;
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) Math.round(10000 * Math.sin(2 * Math.PI * frequency * i / 48000));
            if (i >= 24000) input += (double) pcm[i] * pcm[i];
        }
        var eq = new PcmEqualizer(48000, 1);
        eq.preset(preset); eq.process(pcm, 0, pcm.length);
        for (int i = 24000; i < pcm.length; i++) output += (double) pcm[i] * pcm[i];
        return Math.sqrt(output / input);
    }
}
