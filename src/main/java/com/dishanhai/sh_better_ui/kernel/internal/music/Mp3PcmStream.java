// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.internal.music.decoder.Bitstream;
import com.dishanhai.sh_better_ui.kernel.internal.music.decoder.Decoder;
import com.dishanhai.sh_better_ui.kernel.internal.music.decoder.JavaLayerException;
import com.dishanhai.sh_better_ui.kernel.internal.music.decoder.Header;
import com.dishanhai.sh_better_ui.kernel.internal.music.decoder.SampleBuffer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;

/** Decoder-only PCM cursor. No mixer or audio device opens until a playback owner explicitly does so. */
final class Mp3PcmStream implements PcmTrackTransition.Frames, AutoCloseable {
    private final Bitstream bits;
    private final Decoder decoder = new Decoder();
    private PcmTrackTransition.Format format;
    private SampleBuffer buffer;
    private int offset;
    private boolean ended, closed;

    Mp3PcmStream(byte[] mp3) throws IOException {
        bits = new Bitstream(new ByteArrayInputStream(Objects.requireNonNull(mp3, "audio")));
        try {
            if (!hasMore()) throw new IOException("No MP3 PCM frames");
        } catch (IOException | RuntimeException failure) {
            try { close(); } catch (IOException closing) { failure.addSuppressed(closing); }
            throw failure;
        }
    }

    @Override public PcmTrackTransition.Format format() { return format; }

    /** Non-consuming probe, retaining the first decoded block of the remainder if one exists. */
    @Override public boolean hasMore() throws IOException {
        if (closed) throw new IllegalStateException("PCM stream is closed");
        if (ended) return false;
        while (buffer == null || offset >= buffer.getBufferLength()) {
            try {
                var header = bits.readFrame();
                if (header == null) {
                    ended = true;
                    return false;
                }
                try {
                    var actual = new PcmTrackTransition.Format(header.frequency(), header.mode() == Header.SINGLE_CHANNEL ? 1 : 2);
                    if (format != null && !format.equals(actual)) {
                        ended = true;
                        throw new IOException("MP3 PCM format changed");
                    }
                    buffer = (SampleBuffer) decoder.decodeFrame(header, bits);
                    format = actual;
                    offset = 0;
                } finally {
                    bits.closeFrame();
                }
            } catch (JavaLayerException failure) {
                ended = true;
                throw new IOException("Could not decode MP3 PCM", failure);
            }
        }
        return true;
    }

    @Override public int read(short[] target, int from, int count) throws IOException {
        int channels = format.channels();
        Objects.checkFromIndexSize(from, count, target.length / channels);
        int copied = 0;
        while (copied < count && hasMore()) {
            int samples = Math.min((count - copied) * channels, buffer.getBufferLength() - offset);
            System.arraycopy(buffer.getBuffer(), offset, target, (from + copied) * channels, samples);
            offset += samples;
            copied += samples / channels;
        }
        return copied;
    }

    @Override public void close() throws IOException {
        if (closed) return;
        closed = true;
        try { bits.close(); } catch (JavaLayerException failure) { throw new IOException("Could not close MP3 PCM", failure); }
    }
}
