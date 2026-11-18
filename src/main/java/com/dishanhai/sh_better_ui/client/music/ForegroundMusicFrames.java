// Author: dishanhai
package com.dishanhai.sh_better_ui.client.music;

/** A single scoped in-process source. Rendering only reads immutable frames, never player locks. */
public final class ForegroundMusicFrames {
    private volatile Lease owner;
    public synchronized Lease acquire() {
        var lease = new Lease();
        owner = lease;
        return lease;
    }
    public MusicService.Frame select(MusicService.Frame system, long nowNanos) {
        var lease = owner;
        var sample = lease == null ? null : lease.sample;
        return sample != null && nowNanos - sample.at >= 0 && nowNanos - sample.at < 1_000_000_000L
                ? sample.frame : system;
    }
    private record Sample(MusicService.Frame frame, long at) {}
    public final class Lease implements AutoCloseable {
        private volatile Sample sample;
        private boolean closed;
        public synchronized void publish(MusicService.Frame frame, long at) {
            if (!closed) sample = frame == null ? null : new Sample(frame, at);
        }
        @Override public void close() {
            synchronized (this) { closed = true; sample = null; }
            synchronized (ForegroundMusicFrames.this) { if (owner == this) owner = null; }
        }
    }
}
