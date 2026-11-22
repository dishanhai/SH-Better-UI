// Author: dishanhai
package repackage.com.jsyn.util.soundfile.streamed;

import lombok.SneakyThrows;
import repackage.com.jsyn.data.FloatSample;
import repackage.org.kc7bfi.jflac.ChannelData;
import repackage.org.kc7bfi.jflac.FLACDecoder;
import repackage.org.kc7bfi.jflac.frame.Frame;
import repackage.org.kc7bfi.jflac.io.RandomFileInputStream;
import repackage.org.kc7bfi.jflac.metadata.SeekPoint;
import repackage.org.kc7bfi.jflac.metadata.StreamInfo;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/** Seekable jFLAC playback with fixed-size PCM storage. */
public final class FlacFloatSample extends FloatSample {
    private final RandomFileInputStream input;
    private final PrefetchedPcm prefetch;

    public static FlacFloatSample open(File file) throws IOException {
        RandomFileInputStream input = new RandomFileInputStream(file);
        try {
            return new FlacFloatSample(input);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                input.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private FlacFloatSample(RandomFileInputStream input) throws IOException {
        this.input = input;
        WindowReader reader = new WindowReader(input);
        StreamInfo info = reader.info;
        numFrames = (int) info.getTotalSamples();
        channelsPerFrame = info.getChannels();
        setFrameRate(info.getSampleRate());
        setPitch(60.0);
        prefetch = new PrefetchedPcm(reader, reader.sampleCount, info.getSampleRate(), channelsPerFrame);
    }

    @Override
    public double readDouble(int index) {
        return prefetch.read(index);
    }

    @Override
    public void read(int startFrame, float[] data, int startIndex, int numFrames) {
        int firstSample = startFrame * channelsPerFrame;
        int count = numFrames * channelsPerFrame;
        for (int i = 0; i < count; i++) data[startIndex + i] = (float) readDouble(firstSample + i);
    }

    @Override
    @SneakyThrows
    public void cleanUp() {
        prefetch.shutdown();
        input.close();
    }

    private static final class WindowReader implements PrefetchedPcm.Reader {
        private final RandomFileInputStream input;
        private final StreamInfo info;
        private final int channels;
        private final int sampleCount;
        private final float scale;
        private final float[] window = new float[PrefetchedPcm.WINDOW_SAMPLES];
        private FLACDecoder decoder;
        private int frameStart;
        private int frameEnd;

        WindowReader(RandomFileInputStream input) throws IOException {
            this.input = input;
            rewind();
            info = decoder.getStreamInfo();
            channels = info.getChannels();
            long frames = info.getTotalSamples();
            if (channels < 1 || channels > 8 || info.getSampleRate() <= 0
                    || frames < 1 || frames > Integer.MAX_VALUE / channels) {
                throw new IOException("Invalid FLAC stream dimensions");
            }
            sampleCount = (int) (frames * channels);
            scale = Math.scalb(1f, 1 - info.getBitsPerSample());
        }

        @Override
        public float[] read(int start) throws IOException {
            if (start < frameStart || start - frameEnd >= window.length) seek(start);
            int count = Math.min(window.length, sampleCount - start);
            int written = 0;
            while (written < count) {
                int index = start + written;
                while (index >= frameEnd && readFrame()) {
                    // Skip whole frames between the approximate seek point and the requested sample.
                }
                if (index >= frameEnd) break;
                if (index < frameStart) {
                    int gap = Math.min(count - written, frameStart - index);
                    Arrays.fill(window, written, written + gap, 0f);
                    written += gap;
                    continue;
                }
                int length = Math.min(count - written, frameEnd - index);
                int source = index - frameStart;
                ChannelData[] pcm = decoder.getChannelData();
                for (int i = 0; i < length; i++, source++) {
                    window[written + i] = pcm[source % channels].getOutput()[source / channels] * scale;
                }
                written += length;
            }
            return written == window.length ? window : Arrays.copyOf(window, written);
        }

        private boolean readFrame() throws IOException {
            if (decoder.getSamplesDecoded() >= info.getTotalSamples()) return false;
            Frame frame = decoder.readNextFrame();
            if (frame == null) return false;
            long first = frame.header.sampleNumber;
            long end = first + frame.header.blockSize;
            if (first < 0 || first >= info.getTotalSamples() || end * channels <= frameEnd) {
                throw new IOException("Invalid FLAC frame position");
            }
            frameStart = (int) (first * channels);
            frameEnd = (int) (Math.min(end, info.getTotalSamples()) * channels);
            decoder.setSamplesDecoded(end);
            return true;
        }

        private void seek(int index) throws IOException {
            int target = index / channels;
            long guess = Math.max(0, target - 2L * info.getMaxBlockSize());
            if (guess > 0) {
                decoder.setSamplesDecoded(0);
                SeekPoint point = decoder.seek(guess);
                if (point != null) {
                    decoder.seekToFrame(point);
                    decoder.setSamplesDecoded(0);
                    frameStart = frameEnd = 0;
                    if (readFrame() && frameStart <= index) return;
                }
            }
            rewind();
        }

        private void rewind() throws IOException {
            input.seek(0);
            decoder = new FLACDecoder(input);
            decoder.readMetadata();
            frameStart = frameEnd = 0;
        }
    }
}
