// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource.Stream;
import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionAudioCacheTest {
    private static SessionAudioCache.Key key(String id) {
        return new SessionAudioCache.Key("test", id, id, new Stream(URI.create("https://example.org/"+id),0,1000,false));
    }
    @Test void evictsLeastRecentlyUsedToByteAndEntryLimits() {
        var cache = new SessionAudioCache(10, 2, 100, () -> 0);
        cache.put(key("a"), new byte[4], 0); cache.put(key("b"), new byte[4], 0);
        assertNotNull(cache.get(key("a"))); cache.put(key("c"), new byte[4], 0);
        assertNull(cache.get(key("b"))); assertEquals(8, cache.info().retainedBytes());
        cache.put(key("d"), new byte[9], 0);
        assertEquals(1, cache.info().entries()); assertEquals(9, cache.info().retainedBytes());
    }
    @Test void oversizedEmptyAndInvalidatedInflightWritesAreNotRetained() {
        var cache = new SessionAudioCache(10, 2, 100, () -> 0);
        cache.put(key("big"), new byte[11], 0); cache.put(key("empty"), new byte[0], 0);
        cache.clear(); cache.put(key("late"), new byte[1], 0);
        assertEquals(0, cache.info().entries());
        cache.put(key("fresh"), new byte[1], cache.revision()); assertEquals(1, cache.info().entries());
    }
    @Test void ttlIsMonotonicAndReadingDoesNotExtendIt() {
        var now = new AtomicLong(0); var cache = new SessionAudioCache(10, 2, 100, now::get);
        cache.put(key("a"), new byte[4], 0); now.set(99); assertNotNull(cache.get(key("a")));
        now.set(100); assertNull(cache.get(key("a"))); assertEquals(0, cache.info().retainedBytes());
    }
    @Test void streamGrantMetadataAndProviderArePartOfIdentity() {
        var cache = new SessionAudioCache(10, 8, 100, () -> 0); var original = key("a");
        cache.put(original,new byte[1],0);
        assertNull(cache.get(new SessionAudioCache.Key("other","a","a",original.stream())));
        assertNull(cache.get(new SessionAudioCache.Key("test","a","a",new Stream(original.stream().uri(),0,1000,true))));
        assertNull(cache.get(new SessionAudioCache.Key("test","a","a",new Stream(original.stream().uri(),500,1000,false))));
        assertEquals("Audio cache key", original.toString());
    }
}
