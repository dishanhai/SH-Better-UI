package com.muoniumplayer.core.viper.dsp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class VHEFallbackTest {
    @TempDir Path directory;

    private static byte[] floats(float... values) {
        ByteBuffer bytes = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) bytes.putFloat(value);
        return bytes.array();
    }

    private VHE effect() {
        Function<String, InputStream> resources = name -> {
            if (name.endsWith("_near.f32")) return new ByteArrayInputStream(floats(1, 0));
            if (name.endsWith("_far.f32")) return new ByteArrayInputStream(floats(.25f, 0));
            return null;
        };
        return new VHE(directory, resources);
    }

    private static float[] render(VHE effect, int[] chunks, int inputFrames) {
        return render(effect, chunks, inputFrames, 0);
    }

    private static float[] render(VHE effect, int[] chunks, int inputFrames, int inputChannel) {
        List<Float> output = new ArrayList<>();
        int position = 0;
        int chunk = 0;
        while (position < inputFrames) {
            int count = Math.min(chunks[chunk++ % chunks.length], inputFrames - position);
            float[] in = new float[count * 2];
            if (position == 0) in[inputChannel] = 1;
            float[] out = new float[count * 2];
            int written = effect.process(in, out, count);
            for (int i = 0; i < written * 2; i++) output.add(out[i]);
            position += count;
        }
        float[] result = new float[output.size()];
        for (int i = 0; i < result.length; i++) result[i] = output.get(i);
        return result;
    }

    @Test void fallbackProducesNearAndCrossEarSignalsAcrossBufferBoundaries() {
        VHE fixed = effect();
        fixed.setEnable(true);
        float[] expected = render(fixed, new int[] {4096}, 32768);
        VHE varied = effect();
        varied.setEnable(true);
        float[] actual = render(varied, new int[] {333, 5000, 7, 2048}, 32768);
        assertTrue(actual.length >= 8192);
        assertEquals(1f, actual[0], 1e-4f);
        assertEquals(.25f, actual[1], 1e-4f);
        for (int i = 0; i < Math.min(actual.length, expected.length); i++) {
            assertEquals(expected[i], actual[i], 1e-4f, "sample " + i);
        }
        VHE right = effect();
        right.setSampleRate(48000);
        right.setEffectLevel(4);
        right.setEnable(true);
        float[] mirrored = render(right, new int[] {1700, 3000}, 16384, 1);
        assertEquals(.25f, mirrored[0], 1e-4f);
        assertEquals(1f, mirrored[1], 1e-4f);
    }

    @Test void bundledFilesExistForEveryLevelAndRateAndProduceBothEars() throws Exception {
        for (int rate : new int[] {44100, 48000}) {
            for (int level = 0; level < 5; level++) {
                for (String side : new String[] {"near", "far"}) {
                    String path = "/assets/sh_better_ui/audio/vhe/L" + level + "_" + rate + "_" + side + ".f32";
                    try (InputStream in = VHE.class.getResourceAsStream(path)) {
                        assertNotNull(in, path);
                        byte[] bytes = in.readAllBytes();
                        assertTrue(bytes.length >= 8 && bytes.length <= 65536 && bytes.length % 4 == 0);
                        ByteBuffer values = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                        boolean nonzero = false;
                        while (values.hasRemaining()) {
                            float value = values.getFloat();
                            assertTrue(Float.isFinite(value));
                            nonzero |= value != 0f;
                        }
                        assertTrue(nonzero);
                    }
                }
                VHE effect = new VHE(directory, VHE.class::getResourceAsStream);
                effect.setSampleRate(rate);
                effect.setEffectLevel(level);
                effect.setEnable(true);
                float[] output = render(effect, new int[] {4096}, 16384);
                float leftPeak = 0, rightPeak = 0;
                for (int i = 0; i < output.length; i += 2) {
                    leftPeak = Math.max(leftPeak, Math.abs(output[i]));
                    rightPeak = Math.max(rightPeak, Math.abs(output[i + 1]));
                }
                assertTrue(leftPeak > 1e-6f && rightPeak > 1e-6f);
            }
        }
    }

    @Test void validUserPairOverridesBuiltinsWithoutCrossEarMixing() throws Exception {
        Files.write(directory.resolve("L0_44100_L.f32"), floats(.5f, 0));
        Files.write(directory.resolve("L0_44100_R.f32"), floats(.1f, 0));
        VHE effect = effect();
        effect.setEnable(true);
        float[] output = render(effect, new int[] {4096}, 16384);
        assertEquals(.5f * 2.94595f, output[0], 1e-4f);
        assertEquals(0f, output[1], 1e-4f);
    }

    @Test void incompleteOrInvalidUserPairBypassesInsteadOfMixingWithBuiltins() throws Exception {
        Files.write(directory.resolve("L0_44100_L.f32"), floats(1, 0));
        VHE effect = effect();
        effect.setEnable(true);
        float[] input = {1, .5f};
        float[] output = new float[2];
        assertEquals(1, effect.process(input, output, 1));
        assertArrayEquals(input, output);
        Files.write(directory.resolve("L0_44100_R.f32"), floats(Float.NaN, 0));
        effect.reset();
        assertEquals(1, effect.process(input, output, 1));
        assertArrayEquals(input, output);
    }

    @Test void unsupportedRatesAndDisabledEffectBypass() {
        VHE effect = effect();
        effect.setEnable(true);
        effect.setSampleRate(96000);
        float[] input = {1, .5f};
        float[] output = new float[2];
        assertEquals(1, effect.process(input, output, 1));
        assertArrayEquals(input, output);
        effect.setSampleRate(48000);
        effect.setEnable(false);
        assertEquals(1, effect.process(input, output, 1));
        assertArrayEquals(input, output);
    }
}
