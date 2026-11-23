// Author: dishanhai
package repackage.com.jsyn.util.soundfile.streamed;

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.LockSupport;

/** Single-consumer PCM cache with one background reader and bounded lookahead. */
public final class PrefetchedPcm {
    public static final int WINDOW_SAMPLES = 4096;
    private static final int MIN_CAPACITY = 64 * 1024;
    private static final int MAX_CAPACITY = 512 * 1024;

    @FunctionalInterface
    public interface Reader {
        /** Returns up to WINDOW_SAMPLES from this absolute index, with a short window at EOF. */
        float[] read(int sampleIndex) throws IOException;
    }

    private final Reader reader;
    private final int sampleCount;
    private final float[] ring;
    private final int mask;
    private final int lookahead;
    private final Object ioLock = new Object();
    private final Thread worker;

    // High and low words hold the actual initialized [start, end) as one atomic snapshot.
    private volatile long range;
    private volatile int consumed;
    private volatile boolean running = true;
    private volatile int eof;
    private boolean failureLogged;
    private long slowReads;

    public PrefetchedPcm(Reader reader, long sampleCount, double frameRate, int channels) {
        this.reader = reader;
        this.sampleCount = (int) Math.min(Integer.MAX_VALUE, Math.max(0, sampleCount));
        // Keep headroom for PCM at high sample rates and for WSOLA's overlapping input windows.
        int target = (int) Math.max(MIN_CAPACITY, Math.min(MAX_CAPACITY, frameRate * channels / 2));
        int capacity = Integer.highestOneBit(target - 1) << 1;
        ring = new float[capacity];
        mask = capacity - 1;
        lookahead = capacity * 3 / 4;
        eof = this.sampleCount;
        worker = new Thread(this::prefetch, "muonium-pcm-prefetch");
        worker.setDaemon(true);
        worker.start();
    }

    public double read(int index) {
        if (index < 0 || index >= eof || !running) return 0;
        long snapshot = range;
        if (contains(snapshot, index)) {
            float value = ring[index & mask];
            // A later write can reuse the slot after its old range expires.
            VarHandle.loadLoadFence();
            if (contains(range, index)) {
                if ((index & (WINDOW_SAMPLES - 1)) == 0) advance(index);
                return value;
            }
        }
        synchronized (ioLock) {
            if (!running || index >= eof) return 0;
            if (!contains(range, index)) {
                slowReads++;
                // Align to retain the prefix needed by interpolation or a short backward seek.
                int start = index & -WINDOW_SAMPLES;
                fill(start);
            }
            advance(index);
            return contains(range, index) ? ring[index & mask] : 0;
        }
    }

    /** Warm the underlying file without moving the playback window. */
    public boolean prime(int index) {
        synchronized (ioLock) {
            if (!running || index < 0 || index >= eof) return false;
            try {
                return reader.read(index).length > 0;
            } catch (IOException e) {
                logFailure(e);
                return false;
            }
        }
    }

    private void advance(int index) {
        consumed = index;
        LockSupport.unpark(worker);
    }

    private void prefetch() {
        while (running) {
            synchronized (ioLock) {
                if (!running) return;
                int end = (int) range;
                if (end < eof && end - consumed < lookahead && fill(end)) continue;
            }
            LockSupport.park(this);
        }
    }

    private boolean fill(int start) {
        float[] samples;
        try {
            samples = reader.read(start);
        } catch (IOException e) {
            logFailure(e);
            return false;
        }
        int length = Math.min(samples.length, sampleCount - start);
        if (length == 0) {
            eof = Math.min(eof, start);
            return false;
        }
        long previous = range;
        boolean contiguous = start == (int) previous;
        if (!contiguous) range = 0;
        int offset = start & mask;
        int first = Math.min(length, ring.length - offset);
        System.arraycopy(samples, 0, ring, offset, first);
        if (first < length) System.arraycopy(samples, first, ring, 0, length - first);
        int end = start + length;
        // Leave one window outside the published range so the producer never overwrites a live slot.
        int firstValid = contiguous
                ? Math.max((int) (previous >>> 32), end - (ring.length - WINDOW_SAMPLES)) : start;
        range = ((long) firstValid << 32) | end;
        if (length < WINDOW_SAMPLES) eof = Math.min(eof, end);
        return true;
    }

    private static boolean contains(long snapshot, int index) {
        return index >= (int) (snapshot >>> 32) && index < (int) snapshot;
    }

    private void logFailure(IOException e) {
        if (!failureLogged) {
            failureLogged = true;
            System.err.println("[MuoniumPlayer/Audio] PCM prefetch failed: " + e);
        }
    }

    public void shutdown() {
        synchronized (ioLock) {
            running = false;
        }
        LockSupport.unpark(worker);
        try {
            worker.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public long slowReadCount() {
        return slowReads;
    }

    public boolean isAlive() {
        return worker.isAlive();
    }
}
