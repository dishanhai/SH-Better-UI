// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import java.io.IOException;
import java.util.Objects;

/**
 * Bounded preparation and sample-aligned overlap for a single PCM sink. Deliberately has
 * no device/session dependency; the owner must schedule and consume the transition.
 */
final class PcmTrackTransition {
    record Format(int sampleRate, int channels) {
        Format {
            if (sampleRate < 8000 || sampleRate > 192000 || channels < 1 || channels > 2)
                throw new IllegalArgumentException("Expected 8-192 kHz mono/stereo PCM");
        }
    }

    /** A read may return fewer frames only at EOF. The caller retains ownership. */
    interface Frames {
        Format format();
        int read(short[] target, int from, int count) throws IOException;
        boolean hasMore() throws IOException;
    }

    /** A bounded prefix; any remaining decoded frames still belong to the same stream cursor. */
    static final class Span {
        private final Format format;
        private final short[] samples;
        private final int frames;
        private final boolean reachesEnd;

        private Span(Format format, short[] samples, int frames, boolean reachesEnd) {
            this.format = format;
            this.samples = samples;
            this.frames = frames;
            this.reachesEnd = reachesEnd;
        }
        int frames() { return frames; }
        boolean reachesEnd() { return reachesEnd; }
        Format format() { return format; }
        int read(short[] target, int targetFrame, int first, int count) {
            Objects.checkFromIndexSize(first, count, frames);
            int ch = format.channels();
            Objects.checkFromIndexSize(targetFrame, count, target.length / ch);
            System.arraycopy(samples, first * ch, target, targetFrame * ch, count * ch);
            return count;
        }
    }

    static final int MAX_SECONDS = 12;

    /** Reads at most the requested PCM budget, plus one non-consuming lookup to verify the true tail. */
    static Span capture(Frames source, int seconds) throws IOException {
        if (seconds < 1 || seconds > MAX_SECONDS) throw new IllegalArgumentException("Expected 1–12 seconds");
        return captureFrames(source, Math.multiplyExact(source.format().sampleRate(), seconds));
    }

    /** One extra second of outgoing capture is allowed to reconcile container metadata with true PCM EOF. */
    static Span captureFrames(Frames source, int limit) throws IOException {
        Format format = source.format();
        if (limit < 1 || limit > Math.multiplyExact(format.sampleRate(), MAX_SECONDS + 1))
            throw new IllegalArgumentException("PCM capture outside bounded span");
        short[] samples = new short[Math.multiplyExact(limit, format.channels())];
        int read = 0;
        // Decode in bounded audio blocks and make cancellation responsive during speculative predecode.
        while (read < limit) {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("PCM preparation cancelled");
            int n = Math.min(4096, limit - read);
            int count = source.read(samples, read, n);
            if (count < 0 || count > n) throw new IOException("Invalid PCM frame count");
            read += count;
            if (count < n) break;
        }
        return new Span(format, samples, read, !source.hasMore());
    }

    /**
     * Returns no blend unless the outgoing span reaches its physical EOF and the complete
     * requested incoming head is ready in the same format. In that case the old span and
     * live decoder are still available for a dry fallback; nothing is attenuated here.
     */
    static Blend prepare(Span outgoing, Span incoming, int frames) {
        if (frames < 2 || frames > outgoing.format.sampleRate() * MAX_SECONDS)
            throw new IllegalArgumentException("Crossfade frames outside the bounded playback range");
        if (!outgoing.reachesEnd || !outgoing.format.equals(incoming.format) || incoming.frames < frames
                || outgoing.frames < frames) return null;
        return new Blend(outgoing, incoming, frames);
    }

    /** Emits one continuous stream: dry old prefix, overlap of true tail/head, then dry new prefix remainder. */
    static final class Blend {
        private final Span outgoing, incoming;
        private final int overlap, start, end;
        private int at;

        private Blend(Span outgoing, Span incoming, int overlap) {
            this.outgoing = outgoing;
            this.incoming = incoming;
            this.overlap = overlap;
            start = outgoing.frames - overlap;
            end = outgoing.frames + incoming.frames - overlap;
        }

        Format format() { return outgoing.format; }
        /** Emit-frame offset at which foreground metadata may switch after the sink consumes the overlap. */
        int handoffFrame() { return outgoing.frames; }
        /** Track-local position of the successor at that midpoint-free handoff. */
        int successorHandoffFrame() { return overlap; }
        int remaining() { return end - at; }

        int read(short[] target, int targetFrame, int count) {
            int ch = format().channels();
            Objects.checkFromIndexSize(targetFrame, count, target.length / ch);
            int produced = Math.min(count, remaining());
            int written = 0;
            while (written < produced) {
                if (at < start) {
                    int dry = Math.min(start - at, produced - written);
                    outgoing.read(target, targetFrame + written, at, dry);
                    at += dry;
                    written += dry;
                } else if (at < outgoing.frames) {
                    int overlapAt = at - start;
                    double inGain = overlapAt / (double) (overlap - 1);
                    int destination = (targetFrame + written) * ch;
                    for (int c = 0; c < ch; c++) {
                        int a = outgoing.samples[at * ch + c];
                        int b = incoming.samples[overlapAt * ch + c];
                        target[destination + c] = (short) Math.round(a + (b - a) * inGain);
                    }
                    at++;
                    written++;
                } else {
                    int dry = produced - written;
                    incoming.read(target, targetFrame + written, at - start, dry);
                    at += dry;
                    written += dry;
                }
            }
            return produced;
        }
    }

    private PcmTrackTransition() {}
}
