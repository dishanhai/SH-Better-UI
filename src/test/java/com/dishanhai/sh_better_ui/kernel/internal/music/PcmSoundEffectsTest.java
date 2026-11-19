// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSoundEffect.*;
import static org.junit.jupiter.api.Assertions.*;

class PcmSoundEffectsTest {
    private static final int RATE = 48000;

    @Test void bypassPreservesSamplesIncludingExtremeValuesAndPadding() {
        for (int channels : new int[]{1, 2}) {
            short[] pcm = new short[2048];
            var random = new Random(192);
            for (int i = 0; i < pcm.length; i++) pcm[i] = (short) random.nextInt();
            pcm[512] = Short.MIN_VALUE;
            pcm[1022] = Short.MAX_VALUE;
            var expected = pcm.clone();
            var effects = new PcmSoundEffects(RATE, channels);
            effects.process(pcm, 10, pcm.length - 6);
            assertArrayEquals(expected, pcm);
        }
    }

    @Test void bassAndVocalPresetsChangeActualFrequencyBalance() {
        double bassRetention = retention(SUPER_BASS, 65), bassHighRetention = retention(SUPER_BASS, 6000);
        assertTrue(bassRetention > .88, "bass should be kept while other ranges recede");
        assertTrue(bassRetention > bassHighRetention * 1.65);
        double vocal = retention(CLEAR_VOCALS, 850);
        assertTrue(vocal > retention(CLEAR_VOCALS, 55) * 1.33);
        assertTrue(vocal > retention(CLEAR_VOCALS, 9600) * 1.28);
    }

    @Test void liveRhythmAppliesLinkedCompressionWithoutClipping() {
        var effects = new PcmSoundEffects(RATE, 2);
        effects.preset(LIVE_RHYTHM);
        short[] loud = new short[RATE / 2], quiet = loud.clone();
        for (int i = 0; i < loud.length; i += 2) {
            loud[i] = 31000; loud[i + 1] = 15500;
            quiet[i] = 4000; quiet[i + 1] = 2000;
        }
        effects.process(quiet, 0, quiet.length);
        effects.process(loud, 0, loud.length);
        assertTrue(loud[loud.length - 2] / 31000d < quiet[quiet.length - 2] / 4000d - .15);
        assertEquals(loud[loud.length - 2] * .5, loud[loud.length - 1], 1);
    }

    @Test void wideningIncreasesSideOnlyAndNeverPretendsMonoIsStereo() {
        var stereo = new PcmSoundEffects(RATE, 2);
        stereo.preset(WIDE_STEREO);
        var in = new short[4096];
        for (int i = 0; i < in.length; i += 2) { in[i] = 8000; in[i + 1] = -8000; }
        stereo.process(in, 0, in.length);
        assertTrue(in[in.length - 2] > 9300);
        assertEquals(-in[in.length - 2], in[in.length - 1]);
        Arrays.fill(in, (short) 8000);
        var dry = in.clone();
        stereo.process(in, 0, in.length);
        assertArrayEquals(dry, in);
        var mono = new PcmSoundEffects(RATE, 1);
        mono.preset(WIDE_STEREO);
        mono.process(in, 0, in.length);
        assertArrayEquals(dry, in);
    }

    @Test void hallUsesActualTimeScaledEarlyReflectionsWithFiniteMemory() {
        for (int rate : new int[]{8000, 44100, 48000, 96000}) {
            var effects = new PcmSoundEffects(rate, 2);
            var in = new short[2 * rate / 6 + 2];
            int at = rate * 32 / 1000 * 2;
            in[at] = 20000;
            effects.preset(VIRTUAL_CONCERT_HALL);
            effects.process(in, 0, in.length);
            assertEquals(15600, in[at]);
            assertEquals(2000, in[at + 2 * (rate * 17 / 1000)]);
            assertEquals(1400, in[at + 2 * (rate * 17 / 1000) + 1]);
            assertEquals(800, in[at + 2 * (rate * 47 / 1000)]);
            assertEquals(0, in[at + 2 * (rate * 47 / 1000) + 2]);
        }
    }

    @Test void interruptedTransitionsAreGradualAndReturnToBitExactOff() {
        var effects = new PcmSoundEffects(RATE, 2);
        effects.preset(SUPER_BASS);
        short[] pcm = new short[RATE * 2 / 10];
        Arrays.fill(pcm, (short) 8000);
        effects.process(pcm, 0, 400);
        effects.preset(VIRTUAL_CONCERT_HALL);
        effects.process(pcm, 400, 1200);
        assertTrue(Math.abs(pcm[400] - pcm[398]) < 20);
        effects.preset(OFF);
        effects.process(pcm, 1200, pcm.length);
        assertTrue(Math.abs(pcm[1200] - pcm[1198]) < 20);
        assertNotEquals(8000, pcm[1200]);
        for (int i = 4100; i < pcm.length; i++) assertEquals(8000, pcm[i], "settled dry sample");
    }

    @Test void splittingDecodeBlocksDoesNotChangeSamples() {
        var whole = new PcmSoundEffects(RATE, 2);
        var split = new PcmSoundEffects(RATE, 2);
        var a = new short[4096];
        var random = new Random(195);
        for (int i = 0; i < a.length; i++) a[i] = (short) random.nextInt();
        var b = a.clone();
        whole.preset(VIRTUAL_CONCERT_HALL);
        split.preset(VIRTUAL_CONCERT_HALL);
        whole.process(a, 0, a.length);
        for (int i = 0; i < b.length; i += 512) split.process(b, i, i + 512);
        assertArrayEquals(a, b);
    }

    @Test void rejectsPartialOrInvalidFramesWithoutTouchingSamples() {
        var effects = new PcmSoundEffects(RATE, 2);
        short[] in = {100, 200, 300, 400};
        assertThrows(IllegalArgumentException.class, () -> effects.process(in, 1, 4));
        assertThrows(IllegalArgumentException.class, () -> effects.process(in, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> effects.process(in, 2, 6));
        assertArrayEquals(new short[]{100, 200, 300, 400}, in);
    }

    private static double retention(com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSoundEffect effect, int hz) {
        short[] in = new short[RATE / 2];
        for (int i = 0; i < in.length; i++) in[i] = (short) Math.round(8000 * Math.sin(2 * Math.PI * hz * i / RATE));
        var effects = new PcmSoundEffects(RATE, 1);
        effects.preset(effect);
        var out = in.clone();
        effects.process(out, 0, out.length);
        double original = 0, effected = 0;
        for (int i = in.length / 2; i < in.length; i++) {
            original += in[i] * in[i]; effected += out[i] * out[i];
        }
        return Math.sqrt(effected / original);
    }
}
