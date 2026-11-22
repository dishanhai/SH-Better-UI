package repackage.com.jsyn.util.soundfile.streamed.raf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RafWaveReaderRegressionTest {
    @TempDir
    Path temp;

    @Test
    void readsLastSampleAndSeeksWithoutCrossingDataChunk() throws Exception {
        ByteBuffer bytes = ByteBuffer.allocate(44 + 18 + 8).order(ByteOrder.LITTLE_ENDIAN);
        bytes.position(44);
        for (short i = 1; i <= 9; i++) bytes.putShort((short) (i * 1000));
        bytes.putLong(-1L);
        Path file = temp.resolve("tail.wav");
        Files.write(file, bytes.array());

        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            input.seek(44);
            var reader = new RafWAVEFileParser.StreamedByteDataReader(
                    new RafIFFParser(input), 18, 9, 1, 16, 1);
            assertArrayEquals(new float[] { 9000f / 32768f }, reader.read(16), 0.00001f);
            assertArrayEquals(new float[0], reader.read(18));
            assertArrayEquals(new float[] { 1000f / 32768f, 2000f / 32768f },
                    java.util.Arrays.copyOf(reader.read(0), 2), 0.00001f);
            assertArrayEquals(new float[] { 9000f / 32768f }, reader.read(16), 0.00001f);
        }
    }

    @Test
    void seekingPastThePrefetchWindowDoesNotInventSamplesBeforeTheSeek() throws Exception {
        int count = 200_000;
        int seek = 150_000;
        ByteBuffer bytes = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) bytes.putShort((short) (i % 30_000 + 1));
        Path file = temp.resolve("seek.pcm");
        Files.write(file, bytes.array());

        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            var reader = new RafWAVEFileParser.StreamedByteDataReader(
                    new RafIFFParser(input), bytes.capacity(), count, 1, 16, 1);
            var sample = new RafFloatSample(reader, count, 1);
            try {
                for (int index : new int[]{0, seek, seek - 1, 512, seek + 7_123, count - 1, 0}) {
                    assertEquals((index % 30_000 + 1) / 32768f, sample.readDouble(index), 0.000001,
                            "sample at " + index);
                }
                assertEquals(0, sample.readDouble(count));
            } finally {
                sample.cleanUp();
            }
        }
    }
}
