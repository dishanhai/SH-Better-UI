// Author: dishanhai
package repackage.com.jsyn.util.soundfile.streamed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repackage.com.jsyn.data.FloatSample;
import repackage.com.jsyn.util.SampleLoader;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlacPlaybackRegressionTest {
    private static final int FRAMES = 200_123;

    @Test
    void playbackLoaderPreservesBothChannelsThroughSeeksAndThePartialFinalBlock(@TempDir Path temp) throws Exception {
        Path file = temp.resolve("stereo.flac");
        try (var fixture = getClass().getResourceAsStream("/music/prefetch-stereo-24.flac")) {
            Files.copy(fixture, file);
        }

        var sample = SampleLoader.loadStreamedFloatSample(file.toFile());
        try {
            assertEquals(2, sample.getChannelsPerFrame());
            assertEquals(48_000, sample.getFrameRate());
            assertEquals(FRAMES, sample.getNumFrames());
            for (int frame : new int[]{0, 4095, 4096, 145_117, 145_116, 20_031, FRAMES - 1, 9}) {
                checkFrame(sample, frame);
            }
            assertEquals(0, sample.readDouble(FRAMES * 2));
            for (int frame = 45_000; frame < 85_000; frame++) checkFrame(sample, frame);
        } finally {
            sample.cleanUp();
        }
    }

    private static void checkFrame(FloatSample sample, int frame) {
        int block = frame / 4096;
        assertEquals((1000 + block * 7) / 32768.0, sample.readDouble(frame * 2), 0.0000001,
                () -> "left channel at frame " + frame);
        assertEquals((-2000 - block * 11) / 32768.0, sample.readDouble(frame * 2 + 1), 0.0000001,
                () -> "right channel at frame " + frame);
    }
}
