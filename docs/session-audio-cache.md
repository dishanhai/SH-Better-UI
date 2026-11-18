# Session audio reuse

This is a backend-only follow-up to the MP parity investigation. Reference:
[MuoniumPlayer MusicAudioCache](https://github.com/zihe114514/MuoniumPlayer-multiversion/blob/9b4cc7e7bd143315da716075a6d024a0d4c0c81b/muonium-common/src/main/java/com/muoniumplayer/core/ncm/music/MusicAudioCache.java).
MP has disk cache management; this implementation is intentionally smaller,
independent, memory-only, and does not copy MP code or assets.

## Behavior

- Each player owns its cache. At most **16 MiB / 8 entries** of reusable compressed
  MP3 data is retained. LRU eviction enforces both limits; larger tracks still play
  through the existing bounded download but are not retained for reuse.
- Entries expire **5 minutes after insertion**, measured by a monotonic clock.
  Reads do not extend expiry. Expired entries are removed on cache operations;
  there is no background sweep or disk I/O.
- Every Play still calls the provider's current `resolve` operation first. Only
  a matching source/track/media identifier and exact granted stream URI, preview
  flag, offset and duration reuse data. A null/failed grant never falls back to
  cached audio. A new signed URI may therefore miss the cache intentionally.
- Switching/replacing/clearing account credentials clears reusable audio. Session
  close also clears it. Stop retains eligible data for an explicit later play;
  it does not initiate any background download.
- Cancelled or superseded requests cannot insert bytes. Explicit cache clear has
  a separate revision, so an already-running download can finish playback without
  refilling the cleared cache. Download failures are not cached; a failed output's
  entry is evicted before replaying/replacing/stopping it.
- Stream URLs (which may carry provider signatures), credentials and audio are
  never persisted by this cache. Keys have a redacted string representation.

## Public API

```java
OnlineMusicPlayer.AudioCacheInfo info = player.audioCacheInfo();
player.clearAudioCache(); // Does not stop/pause or delete credentials/files.
```

The reported byte count counts compressed bytes referenced by cache entries once,
even when shared with active playback. Additional playback-only buffers, PCM
decoder and output-device memory are not counted. Eviction removes the cache
reference; active playback may retain its own reference until it stops. Legacy player implementations
report zero capacity and may reject the clear operation. There is no cache-settings
screen in this backend slice.

## Remaining scope

No prefetch, disk/offline download library, persistent history, FLAC/AAC/Opus,
gapless playback, crossfade, DSP/Automix or new platform login is added. This is not
an authorization bypass or a claim of complete MP parity. Tests use fake providers
and outputs to verify cache/lifecycle behavior; audible real-device playback and
live paid-platform entitlements are not asserted by those tests.
