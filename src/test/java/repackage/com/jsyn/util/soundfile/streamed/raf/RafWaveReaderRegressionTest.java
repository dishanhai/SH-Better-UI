package repackage.com.jsyn.util.soundfile.streamed.raf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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
}
