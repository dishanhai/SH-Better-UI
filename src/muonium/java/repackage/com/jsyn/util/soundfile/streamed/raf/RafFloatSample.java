// Modified for SHBUI by dishanhai. See META-INF/third-party/muonium-player-2.6.0/SOURCE.
package repackage.com.jsyn.util.soundfile.streamed.raf;

import lombok.SneakyThrows;
import repackage.com.jsyn.data.FloatSample;
import repackage.com.jsyn.util.soundfile.streamed.PrefetchedPcm;

public class RafFloatSample extends FloatSample {
    public final RafWAVEFileParser.StreamedByteDataReader reader;
    private final PrefetchedPcm prefetch;

    public RafFloatSample(RafWAVEFileParser.StreamedByteDataReader reader, int numFrames, int channelsPerFrame) {
        this(reader, numFrames, channelsPerFrame, 48_000);
    }

    RafFloatSample(RafWAVEFileParser.StreamedByteDataReader reader, int numFrames, int channelsPerFrame,
                   double frameRate) {
        this.reader = reader;
        this.numFrames = numFrames;
        this.channelsPerFrame = channelsPerFrame;
        this.prefetch = new PrefetchedPcm(
                index -> reader.read((long) index * reader.getSampleSizeInBytes()),
                (long) numFrames * channelsPerFrame, frameRate, channelsPerFrame);
    }

    @Override
    public void allocate(int numFrames, int channelsPerFrame) {
    }

    @Override
    public void read(int startFrame, float[] data, int startIndex, int numFrames) {
    }

    @Override
    public double readDouble(int index) {
        return prefetch.read(index);
    }

    @Override
    @SneakyThrows
    public void cleanUp() {
        prefetch.shutdown();
        reader.parser.raf.close();
    }

    long slowReadCount() {
        return prefetch.slowReadCount();
    }

    boolean prefetchAlive() {
        return prefetch.isAlive();
    }

    public boolean primeWindowAt(int interleavedIndex) {
        return prefetch.prime(interleavedIndex);
    }
}
