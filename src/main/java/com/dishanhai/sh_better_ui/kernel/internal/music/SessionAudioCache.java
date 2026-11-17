// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource.Stream;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer.AudioCacheInfo;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Memory only, confined to the session lock. Stream keys and bytes are never logged or persisted. */
final class SessionAudioCache {
    record Key(String source, String track, String media, Stream stream) {
        @Override public String toString() { return "Audio cache key"; }
    }
    private record Entry(byte[] bytes, long created) {}
    private final long capacity, ttl;
    private final int maxEntries;
    private final LongSupplier clock;
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private long retained, revision;
    SessionAudioCache() { this(16L * 1024 * 1024, 8, TimeUnit.MINUTES.toNanos(5), System::nanoTime); }
    SessionAudioCache(long capacity, int maxEntries, long ttl, LongSupplier clock) {
        this.capacity = capacity; this.maxEntries = maxEntries; this.ttl = ttl; this.clock = clock;
    }
    long revision() { return revision; }
    byte[] get(Key key) {
        expire(); var entry = entries.get(key); return entry == null ? null : entry.bytes();
    }
    void put(Key key, byte[] bytes, long expectedRevision) {
        if (revision != expectedRevision || bytes.length == 0 || bytes.length > capacity) return;
        expire(); remove(key);
        entries.put(key, new Entry(bytes, clock.getAsLong())); retained += bytes.length;
        var iterator = entries.entrySet().iterator();
        while (retained > capacity || entries.size() > maxEntries) {
            retained -= iterator.next().getValue().bytes().length; iterator.remove();
        }
    }
    void remove(Key key) {
        var removed = entries.remove(key); if (removed != null) retained -= removed.bytes().length;
    }
    void clear() { revision++; entries.clear(); retained = 0; }
    AudioCacheInfo info() { expire(); return new AudioCacheInfo(entries.size(), retained, capacity); }
    private void expire() {
        long now = clock.getAsLong(); var iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.created() >= ttl) { retained -= entry.bytes().length; iterator.remove(); }
        }
    }
}
