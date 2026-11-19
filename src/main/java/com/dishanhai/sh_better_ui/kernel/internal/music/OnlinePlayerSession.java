// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.client.lyrics.core.sync.TrackIdentity;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** One playback owner. Generations reject stale network, overlap and lyric completions. */
final class OnlinePlayerSession implements OnlineMusicPlayer {
    @FunctionalInterface interface Download { byte[] read(java.net.URI uri) throws Exception; }
    private final Map<String, OnlineMusicSource> sources = new LinkedHashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(3, task -> {
        var thread = new Thread(task, "shb-online-music"); thread.setDaemon(true); return thread;
    });
    private final Supplier<PlaybackOutput> outputs;
    private final Download download;
    private final LocalLyricFeed feed = new LocalLyricFeed("sh_better_ui:online_music");
    private final Map<CompletableFuture<?>, String> searches = new HashMap<>();
    private record LikeKey(String source, String id) {}
    private final Set<LikeKey> runningLikes = new HashSet<>();
    private long likeLaneRevision;
    synchronized long likeLaneRevision() { return likeLaneRevision; }
    private void releaseLikeLane(LikeKey key) { if (key != null && runningLikes.remove(key)) likeLaneRevision++; }
    static final class LikeBusy extends IllegalStateException {}
    synchronized boolean likeInFlight(OnlineTrack track) {
        return runningLikes.contains(new LikeKey(track.source(),track.id()));
    }

    private final SessionMusicOperations operations = new SessionMusicOperations(this);
    SessionMusicOperations operations() { return operations; }
    private final Set<String> authenticated = new HashSet<>();
    private final Map<String, String> credentials = new HashMap<>();
    private final Set<String> remembered = new HashSet<>();
    private final Map<String, Long> persistenceVersions = new HashMap<>();
    private final Map<String, Long> accountGenerations = new HashMap<>();
    private final OnlineMusicSearch searchIndex = new OnlineMusicSearch();
    private final MusicCredentialStore credentialStore;
    private final MusicQueueStore queueStore;
    private final ScheduledThreadPoolExecutor loginWorker = new ScheduledThreadPoolExecutor(1, task -> {
        var thread = new Thread(task, "shb-music-login"); thread.setDaemon(true); return thread;
    });
    private final ScheduledThreadPoolExecutor queueWorker = new ScheduledThreadPoolExecutor(1, task -> {
        var thread = new Thread(task, "shb-music-queue"); thread.setDaemon(true); return thread;
    });
    private final PlayerQueue queue = new PlayerQueue();
    private ScheduledFuture<?> queueTask;
    private long queueWatch, crossfadeToken;
    private int crossfadeSeconds;
    private boolean crossfadeAttempted;
    private PendingCrossfade crossfade;

    private record PendingCrossfade(long token, long generation, PlaybackOutput output,
                                    OnlineTrack song, OnlineMusicSource provider,
                                    long cacheRevision, int seconds, long outgoingDuration) {}
    private record PreparedCrossfade(byte[] bytes, SessionAudioCache.Key key, OnlineMusicSource.Stream stream) {}
    private Future<?> crossfadeTask;
    private PreparedCrossfade crossfadeAudio;
    private QrLoginSession login;
    private String loginSource;
    private PlaybackOutput output;
    private float volume = 1f;
    private OnlineMusicSoundEffect soundEffect = OnlineMusicSoundEffect.OFF;
    private OnlineMusicEqualizerPreset equalizerPreset = OnlineMusicEqualizerPreset.FLAT;
    private Future<?> audioTask, lyricTask;
    private OnlineTrack track;
    private byte[] audio;
    private final SessionAudioCache audioCache = new SessionAudioCache();
    private SessionAudioCache.Key audioKey;
    private long generation, duration, offset;
    private boolean preview, playRequested, savedCredentialsRestored;
    private State state = State.IDLE;
    private Failure failure = Failure.NONE;
    private LyricsState lyricsState = LyricsState.EMPTY;
    private LyricTimeline lyrics = LyricTimeline.empty();

    OnlinePlayerSession(List<OnlineMusicSource> sources) { this(sources, Mp3Output::new, new MusicHttp()::audio); }
    OnlinePlayerSession(List<OnlineMusicSource> sources, MusicCredentialStore store) {
        this(sources, Mp3Output::new, new MusicHttp()::audio, store);
    }
    OnlinePlayerSession(List<OnlineMusicSource> providers, Supplier<PlaybackOutput> outputs, Download download) {
        this(providers, outputs, download, null);
    }
    OnlinePlayerSession(List<OnlineMusicSource> providers, Supplier<PlaybackOutput> outputs, Download download, MusicCredentialStore store) {
        this(providers, outputs, download, store, null);
    }
    OnlinePlayerSession(List<OnlineMusicSource> providers, Supplier<PlaybackOutput> outputs, Download download,
                        MusicCredentialStore store, MusicQueueStore queueStore) {
        this.outputs = outputs; this.download = download;
        this.queueStore = queueStore;
        this.credentialStore = store;
        loginWorker.setRemoveOnCancelPolicy(true);
        queueWorker.setRemoveOnCancelPolicy(true);
        for (var provider : providers) {
            if (sources.putIfAbsent(provider.id(), provider) != null) throw new IllegalArgumentException("Duplicate music source");
        }
    }
    @Override public synchronized List<String> sources() { return List.copyOf(sources.keySet()); }
    @Override public void credentials(String sourceId, String cookie) {
        List<CompletableFuture<?>> obsolete;
        synchronized (this) { obsolete = replaceCredentials(sourceId, cookie, true); }
        cancelRequests(obsolete);
    }
    private List<CompletableFuture<?>> replaceCredentials(String sourceId, String cookie, boolean cancelLogin) {
        return replaceCredentials(sourceId,cookie,cancelLogin,null);
    }
    private List<CompletableFuture<?>> replaceCredentials(String sourceId, String cookie, boolean cancelLogin, CompletableFuture<?> failedRead) {
        requireOpen();
        var provider = source(sourceId);
        String valid = MusicHttp.cookie(cookie);
        provider.credentials(valid);
        try {
            if (credentialStore != null) {
                if (valid.isBlank() && sourceId.matches("[a-z0-9_-]{1,40}")) {
                    if (cancelLogin) credentialStore.revoke(sourceId);
                    else credentialStore.revokeIfCurrent(sourceId, persistenceVersions.getOrDefault(sourceId, -1L));
                    remembered.remove(sourceId); persistenceVersions.remove(sourceId);
                }
                else if (remembersCredentials(sourceId)) saveCredentials(sourceId, valid);
            }
        } catch (java.io.IOException | RuntimeException error) {
            // The requested credential replacement is not committed if saving fails.
            provider.credentials(credentials.getOrDefault(sourceId, ""));
            if (error instanceof java.io.IOException io) throw new java.io.UncheckedIOException("Music credentials could not be saved", io);
            throw (RuntimeException) error;
        }
        var obsolete = applyCredentials(sourceId, valid, failedRead);
        stop();
        return obsolete;
    }
    private void saveCredentials(String sourceId, String cookie) throws java.io.IOException {
        long next = credentialStore.replaceIfCurrent(sourceId, cookie, persistenceVersions.getOrDefault(sourceId, -1L));
        if (next < 0) {
            remembered.remove(sourceId); persistenceVersions.remove(sourceId);
        } else persistenceVersions.put(sourceId, next);
    }
    private List<CompletableFuture<?>> applyCredentials(String sourceId, String cookie) { return applyCredentials(sourceId,cookie,null); }
    private List<CompletableFuture<?>> applyCredentials(String sourceId, String cookie, CompletableFuture<?> failedRead) {
        accountGenerations.merge(sourceId, 1L, Long::sum);
        if (login != null && sourceId.equals(loginSource) && login.completion() != failedRead) {
            login.close(); login = null; loginSource = null;
        }
        operations.invalidate(sourceId,failedRead);
        var obsolete = new ArrayList<CompletableFuture<?>>();
        for (var entry : List.copyOf(searches.entrySet())) if (entry.getValue().equals(sourceId)) {
            obsolete.add(entry.getKey()); searches.remove(entry.getKey());
        }
        searchIndex.clear(sourceId);
        audioCache.clear();
        if (cookie.isBlank()) { authenticated.remove(sourceId); credentials.remove(sourceId); }
        else { authenticated.add(sourceId); credentials.put(sourceId, cookie); }
        return obsolete;
    }
    private static void cancelRequests(List<CompletableFuture<?>> obsolete) {
        // Cancellation invokes consumer callbacks synchronously; never hold the session lock here.
        for (var future : obsolete) future.cancel(true);
    }
    @Override public synchronized void rememberCredentials(String sourceId, boolean remember) {
        requireOpen(); source(sourceId);
        if (credentialStore == null && remember) throw new UnsupportedOperationException("Credential storage unavailable");
        try {
            if (credentialStore != null) {
                String cookie = credentials.getOrDefault(sourceId, "");
                if (remember) {
                    long revision = credentialStore.revision(sourceId);
                    if (!cookie.isBlank()) {
                        revision = credentialStore.replaceIfCurrent(sourceId, cookie, revision);
                        if (revision < 0) throw new java.io.IOException("Music credential preference changed");
                    }
                    persistenceVersions.put(sourceId, revision);
                } else { credentialStore.revoke(sourceId); persistenceVersions.remove(sourceId); }
            }
        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException("Music credentials could not be saved", error); }
        if (remember) remembered.add(sourceId); else remembered.remove(sourceId);
    }
    @Override public synchronized boolean remembersCredentials(String sourceId) {
        requireOpen(); source(sourceId);
        return remembered.contains(sourceId) && credentialStore != null
                && persistenceVersions.getOrDefault(sourceId, -1L) == credentialStore.revision(sourceId);
    }
    /** Only disk access; construction never validates accounts or starts network requests. */
    void restoreSavedCredentials() {
        var obsolete = new ArrayList<CompletableFuture<?>>();
        synchronized (this) {
            requireOpen();
            if (credentialStore == null || savedCredentialsRestored) return;
            savedCredentialsRestored = true;
            for (var sourceId : sources.keySet()) {
                // Restore is startup-only; never overwrite explicit session credentials or logout.
                if (accountGenerations.containsKey(sourceId)) continue;
                try {
                    var saved = credentialStore.loadWithRevision(sourceId);
                    if (saved.cookie().isBlank()) continue;
                    source(sourceId).credentials(saved.cookie());
                    obsolete.addAll(applyCredentials(sourceId, saved.cookie())); remembered.add(sourceId);
                    persistenceVersions.put(sourceId, saved.revision());
                } catch (java.io.IOException | IllegalArgumentException ignored) { /* A damaged source never blocks another account. */ }
            }
        }
        cancelRequests(obsolete);
    }
    @Override public synchronized boolean authenticated(String sourceId) {
        requireOpen(); source(sourceId); return authenticated.contains(sourceId);
    }
    synchronized Optional<OnlineMusicLogin> currentLogin(String sourceId) {
        return sourceId.equals(loginSource) ? Optional.ofNullable(login) : Optional.empty();
    }
    @Override public synchronized OnlineMusicLogin login(String sourceId) {
        requireOpen();
        var provider = source(sourceId);
        if (login != null) login.close();
        loginSource = sourceId;
        login = new QrLoginSession(provider, (attempt, cookie) -> {
            var obsolete = new ArrayList<CompletableFuture<?>>();
            synchronized (OnlinePlayerSession.this) {
                if (state == State.CLOSED || login != attempt) { attempt.close(); return; }
                attempt.confirm(() -> {
                    String valid = MusicHttp.cookie(cookie);
                    provider.credentials(valid);
                    try { if (remembersCredentials(sourceId)) saveCredentials(sourceId, valid); }
                    catch (java.io.IOException | RuntimeException error) {
                        provider.credentials(credentials.getOrDefault(sourceId, ""));
                        if (error instanceof java.io.IOException io) throw new java.io.UncheckedIOException("Music credentials could not be saved", io);
                        throw (RuntimeException) error;
                    }
                    obsolete.addAll(applyCredentials(sourceId, valid, attempt.completion())); stop();
                });
            }
            cancelRequests(obsolete);
        }, loginWorker);
        login.start();
        return login;
    }
    @Override public synchronized CompletableFuture<List<OnlineTrack>> search(String sourceId, String query, int page) {
        requireOpen();
        var provider = source(sourceId).requestSnapshot();
        String text = Objects.requireNonNull(query).strip();
        if (text.isEmpty() || text.length() > 200 || page < 1 || page > 1000) throw new IllegalArgumentException("Invalid music search");
        return request(sourceId, false, () -> {
            List<OnlineTrack> remote;
            try { remote = List.copyOf(provider.search(text, page, 20)); }
            catch (java.io.IOException error) {
                var local = searchIndex.search(sourceId, text, 20);
                if (page != 1 || local.isEmpty() || error instanceof OnlineMusicSource.LoginRequired) throw error;
                return local;
            }
            if (page != 1) return remote;
            var combined = new LinkedHashMap<String, OnlineTrack>();
            for (var item : remote) combined.put(item.id(), item);
            for (var item : searchIndex.search(sourceId, text, 20)) combined.putIfAbsent(item.id(), item);
            return combined.values().stream().limit(20).toList();
        });
    }
    @Override public synchronized CompletableFuture<OnlineMusicAccount> account(String sourceId) {
        var provider = source(sourceId).requestSnapshot(); return request(sourceId, true, provider::account);
    }
    @Override public synchronized CompletableFuture<List<OnlinePlaylist>> playlists(String sourceId, int page) {
        checkPage(page); var provider = source(sourceId).requestSnapshot();
        return request(sourceId, true, () -> List.copyOf(provider.playlists(page, 20)));
    }
    @Override public synchronized CompletableFuture<List<OnlineTrack>> playlist(OnlinePlaylist playlist, int page) {
        Objects.requireNonNull(playlist); checkPage(page); var provider = source(playlist.source()).requestSnapshot();
        return request(playlist.source(), true, () -> List.copyOf(provider.playlist(playlist, page, 20)));
    }
    @Override public synchronized CompletableFuture<List<OnlineTrack>> liked(String sourceId, int page) {
        checkPage(page); var provider = source(sourceId).requestSnapshot();
        return request(sourceId, true, () -> List.copyOf(provider.liked(page, 20)));
    }
    @Override public synchronized CompletableFuture<Void> like(OnlineTrack track, boolean liked) {
        Objects.requireNonNull(track); var provider = source(track.source()).requestSnapshot();
        return request(track.source(), true, () -> { provider.like(track, liked); return null; }, new LikeKey(track.source(),track.id()));
    }
    @Override public synchronized boolean supportsLikes(String sourceId) { requireOpen(); return source(sourceId).supportsLikes(); }
    @Override public synchronized CompletableFuture<List<String>> suggest(String sourceId, String query) {
        requireOpen(); var provider = source(sourceId).requestSnapshot(); String text = Objects.requireNonNull(query).strip();
        if (text.length() > 200) throw new IllegalArgumentException("Invalid music search");
        if (text.isEmpty()) return CompletableFuture.completedFuture(List.of());
        return request(sourceId, false, () -> {
            var values = new LinkedHashSet<>(searchIndex.suggest(sourceId, text, 8));
            try { values.addAll(provider.suggest(text, 8)); }
            catch (java.io.IOException | UnsupportedOperationException error) { if (values.isEmpty()) throw error; }
            return values.stream().filter(value -> value != null && !value.isBlank()).limit(8).toList();
        });
    }
    private static void checkPage(int page) { if (page < 1 || page > 1000) throw new IllegalArgumentException("Invalid music page"); }
    private synchronized <T> CompletableFuture<T> request(String sourceId, boolean accountRequired, Callable<T> action) {
        return request(sourceId,accountRequired,action,null);
    }
    private synchronized <T> CompletableFuture<T> request(String sourceId, boolean accountRequired, Callable<T> action, LikeKey write) {
        requireOpen(); source(sourceId);
        if (accountRequired && !authenticated.contains(sourceId)) return CompletableFuture.failedFuture(new OnlineMusicSource.LoginRequired());
        if (write != null) {
            if (runningLikes.size() >= 32 || !runningLikes.add(write)) return CompletableFuture.failedFuture(new LikeBusy());
            likeLaneRevision++;
        }
        long epoch = accountGenerations.getOrDefault(sourceId, 0L);
        var result = new CompletableFuture<T>(); searches.put(result, sourceId);
        Future<?> task;
        try { task = workers.submit(() -> {
            try {
                synchronized (this) {
                    if (result.isDone() || accountGenerations.getOrDefault(sourceId, 0L) != epoch) {
                        releaseLikeLane(write);
                        return;
                    }
                }
                T value;
                try { value = action.call(); }
                finally { if (write != null) synchronized(this) { releaseLikeLane(write); } }
                synchronized (this) {
                    if (result.isDone() || state == State.CLOSED || accountGenerations.getOrDefault(sourceId, 0L) != epoch) return;
                    if (value instanceof List<?> list) {
                        var tracks = new ArrayList<OnlineTrack>();
                        for (var item : list) if (item instanceof OnlineTrack song) tracks.add(song);
                        searchIndex.addAll(tracks);
                    }
                }
                result.complete(value);
            } catch (Exception error) {
                List<CompletableFuture<?>> obsolete = List.of();
                synchronized (this) {
                    if (result.isDone() || state == State.CLOSED || accountGenerations.getOrDefault(sourceId, 0L) != epoch) return;
                    if (error instanceof OnlineMusicSource.LoginRequired) {
                        searches.remove(result);
                        try { obsolete = replaceCredentials(sourceId, "", false, result); } catch (java.io.UncheckedIOException ignored) {
                            source(sourceId).credentials(""); obsolete = applyCredentials(sourceId, "", result); stop();
                        }
                    }
                }
                cancelRequests(obsolete);
                result.completeExceptionally(error);
            }
        }); } catch (RejectedExecutionException failure) {
            releaseLikeLane(write);
            searches.remove(result);
            return CompletableFuture.failedFuture(failure);
        }
        result.whenComplete((value, error) -> {
            // Cancelling observation must not cancel a queued write task before it can release its lane.
            if (result.isCancelled() && write == null) task.cancel(true);
            synchronized (this) { searches.remove(result); }
        });
        return result;
    }
    @Override public OnlineMusicQueue queue() { return queue; }

    private void validateTrack(OnlineTrack next) {
        Objects.requireNonNull(next); source(next.source());
        if (!next.id().matches("[A-Za-z0-9]{1,40}") || !next.mediaId().matches("[A-Za-z0-9]{1,40}"))
            throw new IllegalArgumentException("Invalid platform track identifier");
    }

    @Override public synchronized void play(OnlineTrack next) {
        requireOpen();
        validateTrack(next);
        var provider = source(next.source()).requestSnapshot();
        reset();
        track = next; duration = next.durationMs(); state = State.LOADING; playRequested = true;
        lyricsState = LyricsState.LOADING;
        long request = generation;
        long cacheRevision = audioCache.revision();
        audioTask = workers.submit(() -> loadAudio(provider, next, request, cacheRevision));
        startLyrics(provider, next, request);
    }
    private void startLyrics(OnlineMusicSource provider, OnlineTrack next, long request) {
        lyricTask = workers.submit(() -> {
            LyricTimeline timeline;
            try { timeline = provider.lyrics(next); } catch (Exception ignored) { timeline = LyricTimeline.empty(); }
            synchronized (this) {
                if (generation != request || state == State.CLOSED) return;
                lyrics = timeline;
                lyricsState = timeline.isEmpty() ? LyricsState.UNAVAILABLE : LyricsState.READY;
            }
        });
    }
    private void loadAudio(OnlineMusicSource provider, OnlineTrack next, long request, long cacheRevision) {
        Failure problem = Failure.NETWORK;
        try {
            var stream = provider.resolve(next);
            if (stream == null) { problem = Failure.UNAVAILABLE; throw new IllegalStateException("No granted stream"); }
            // Always resolve first: an earlier cache hit never overrides revoked access or a new preview.
            var key = new SessionAudioCache.Key(next.source(), next.id(), next.mediaId(), stream);
            byte[] bytes;
            synchronized (this) {
                if (generation != request || state == State.CLOSED) return;
                bytes = audioCache.get(key);
            }
            boolean downloaded = bytes == null;
            if (downloaded) bytes = download.read(stream.uri());
            synchronized (this) {
                if (generation != request || state == State.CLOSED) return;
                if (downloaded) audioCache.put(key, bytes, cacheRevision);
                audioKey = key;
                audio = bytes; offset = Math.max(0, stream.offsetMs());
                duration = Math.max(0, stream.durationMs()); preview = stream.preview();
                startOutput(0, playRequested);
            }
            return;
        } catch (OnlineMusicSource.LoginRequired ignored) { problem = Failure.LOGIN_REQUIRED; }
        catch (Exception ignored) { }
        synchronized (this) {
            if (generation == request && state != State.CLOSED) {
                failPlayback(problem);
            }
        }
    }
    @Override public synchronized AudioCacheInfo audioCacheInfo() { return audioCache.info(); }
    @Override public synchronized void clearAudioCache() { requireOpen(); audioCache.clear(); }
    @Override public boolean supportsVolume() { return true; }
    @Override public synchronized float volume() { return volume; }
    @Override public synchronized void volume(float value) {
        requireOpen();
        volume = PcmGain.normalize(value);
        if (output != null) output.volume(volume);
    }
    @Override public synchronized boolean supportsEqualizer() {
        return state != State.CLOSED && output != null && output.supportsEqualizer();
    }
    @Override public synchronized OnlineMusicEqualizerPreset equalizerPreset() { return equalizerPreset; }
    @Override public synchronized void equalizerPreset(OnlineMusicEqualizerPreset preset) {
        requireOpen();
        Objects.requireNonNull(preset, "preset");
        if (!supportsEqualizer()) throw new UnsupportedOperationException("Music equalizer unavailable");
        output.equalizerPreset(preset);
        equalizerPreset = preset;
    }
    private boolean isQqTrack() { return track != null && "qq".equals(track.source()); }
    @Override public synchronized boolean supportsSoundEffects() {
        if (!isQqTrack() || state == State.CLOSED || output == null || !output.supportsSoundEffects()) return false;
        return switch (output.state()) {
            case LOADING, PLAYING, PAUSED, ENDED -> true;
            default -> false;
        };
    }
    @Override public synchronized OnlineMusicSoundEffect soundEffect() { return soundEffect; }
    @Override public synchronized void soundEffect(OnlineMusicSoundEffect effect) {
        requireOpen();
        Objects.requireNonNull(effect, "effect");
        if (!supportsSoundEffects()) throw new UnsupportedOperationException("Music sound effects unavailable");
        output.soundEffect(effect);
        soundEffect = effect;
    }
    @Override public synchronized boolean supportsCrossfade() { return state != State.CLOSED; }
    @Override public synchronized int crossfadeSeconds() { return crossfadeSeconds; }
    @Override public synchronized void crossfadeSeconds(int seconds) {
        requireOpen();
        if (seconds < 0 || seconds > 12) throw new IllegalArgumentException("Crossfade must be off or 1–12 seconds");
        if (crossfadeSeconds == seconds) return;
        if (tryCancelCrossfade()) crossfadeAttempted = false;
        // Once mixed frames are queued, finish that handoff. This setting applies to future overlaps.
        crossfadeSeconds = seconds;
    }
    @Override public synchronized void pause() { playRequested = false; if (output != null) output.pause(); }
    @Override public synchronized void resume() {
        playRequested = true;
        if (output != null && output.state() == State.PAUSED) output.resume();
        else if (track != null && snapshot().state() == State.ENDED) seek(0);
    }
    @Override public synchronized void seek(long positionMs) {
        requireOpen();
        if (audio == null || output == null) return;
        boolean playing = output.state() != State.PAUSED;
        discardCrossfade();
        crossfadeAttempted = false;
        long target = Math.max(0, duration > 0 ? Math.min(duration, positionMs) : positionMs);
        var previous = output;
        output = null;
        try { previous.close(); }
        catch (RuntimeException error) { failPlayback(Failure.AUDIO); return; }
        if (startOutput(target, playing) && queue.attachedGeneration == generation && queueTask == null) queue.watch();
        feed.clear();
    }
    /** Publish an output only within the session lock; failed setup never masks the terminal state. */
    private boolean startOutput(long position, boolean playing) {
        try {
            output = Objects.requireNonNull(outputs.get(), "Missing audio output");
            output.volume(volume);
            if (output.supportsEqualizer()) output.equalizerPreset(equalizerPreset);
            if (isQqTrack() && output.supportsSoundEffects()) output.soundEffect(soundEffect);
            output.start(audio, position, playing);
            return true;
        } catch (RuntimeException error) {
            failPlayback(Failure.AUDIO);
            return false;
        }
    }
    private void failPlayback(Failure problem) {
        var failed = output;
        output = null;
        // A faulty device must not prevent state publication, stop, shutdown or another play attempt.
        if (failed != null) try { failed.close(); } catch (RuntimeException ignored) { }
        audioCache.remove(audioKey);
        audioKey = null; audio = null;
        failure = problem; state = State.FAILED; playRequested = false;
        cancelQueueProgress(); feed.clear();
    }
    @Override public synchronized float audioActivity() {
        return output != null && output.state() == State.PLAYING ? output.audioActivity() : 0f;
    }

    @Override public synchronized Snapshot snapshot() {
        State current = output == null ? state : output.state();
        long position = output == null ? 0 : output.positionMs();
        if (duration > 0) position = Math.min(duration, position);
        return new Snapshot(track, current, position, duration, lyricsState, lyrics,
                current == State.FAILED && failure == Failure.NONE ? Failure.AUDIO : failure, preview);
    }
    record MediaObservation(Snapshot snapshot, long offsetMs, long generation) {}
    /** Called on the bridge worker, under one session lock, never on the render thread. */
    synchronized MediaObservation mediaObservation() { return new MediaObservation(snapshot(), offset, generation); }
    /** Identity of the current playback attempt; reads share the playback mutation lock. */
    synchronized long playbackGeneration() { return generation; }
    @Override public LyricFeed lyricFeed() {
        return new LyricFeed() {
            @Override public Frame frame() {
                synchronized (OnlinePlayerSession.this) {
                    var current = snapshot();
                    if (current.track() == null || current.lyricsState() != LyricsState.READY
                            || current.state() == State.FAILED || current.state() == State.CLOSED || current.state() == State.LOADING) {
                        feed.clear();
                    } else {
                        var song = current.track();
                        feed.publish(new TrackIdentity(song.title(), song.artist(), song.album(), song.durationMs()), lyrics,
                                current.positionMs() + offset, false);
                    }
                    return feed.frame();
                }
            }
            @Override public boolean isAlive() { return frame().alive(); }
            @Override public com.dishanhai.sh_better_ui.client.lyrics.core.sync.PlayerSnapshot pollSnapshot() { return frame().snapshot(); }
            @Override public LyricTimeline lyric() { return frame().timeline(); }
            @Override public long lyricSignature() { return frame().signature(); }
        };
    }
    @Override public synchronized void stop() {
        if (state == State.CLOSED) return;
        reset(); state = State.IDLE; track = null;
    }
    private void reset() {
        cancelQueueProgress();
        generation++;
        if (audioTask != null) audioTask.cancel(true);
        if (lyricTask != null) lyricTask.cancel(true);
        if (output != null) {
            if (output.state() == State.FAILED) audioCache.remove(audioKey);
            try { output.close(); } catch (RuntimeException ignored) { }
        }
        audioKey = null;
        output = null; audio = null; duration = offset = 0; preview = false;
        failure = Failure.NONE; lyricsState = LyricsState.EMPTY; lyrics = LyricTimeline.empty(); feed.clear();
    }
    private void cancelQueueProgress() {
        discardCrossfade();
        crossfadeAttempted = false;
        queueWatch++;
        if (queueTask != null) queueTask.cancel(false);
        queueTask = null;
    }
    /** Watches the current output, not UI frames. All transitions share the session lock. */
    private synchronized void advanceQueue(long request, long watch) {
        if (queueWatch != watch || generation != request || state == State.CLOSED) return;
        if (state == State.FAILED) { cancelQueueProgress(); return; }
        if (output == null) return;
        State current = output.state();
        if (current == State.FAILED) { cancelQueueProgress(); return; }
        if (advanceCrossfade(current) || !playRequested || current != State.ENDED) return;
        cancelQueueProgress();
        int next = queue.order.next(true);
        if (next >= 0) queue.playIndex(next);
    }

    private boolean tryCancelCrossfade() {
        if (crossfade == null) return true;
        if (!crossfade.output().cancelCrossfade(crossfade.token())) return false;
        clearCrossfade();
        return true;
    }

    /** Transport commands close this output even if the PCM overlap was already submitted. */
    private void discardCrossfade() {
        if (crossfade != null) crossfade.output().cancelCrossfade(crossfade.token());
        clearCrossfade();
    }

    private void clearCrossfade() {
        crossfade = null;
        crossfadeAudio = null;
        if (crossfadeTask != null) crossfadeTask.cancel(true);
        crossfadeTask = null;
    }

    private void beforeQueueEdit() {
        if (crossfade != null && crossfade.output().crossfadeStatus(crossfade.token()) == PlaybackOutput.CrossfadeStatus.COMPLETE)
            promoteCrossfade();
        if (!tryCancelCrossfade()) throw new IllegalStateException("Wait for the active track transition");
        crossfadeAttempted = false;
    }

    /** Called only under the session lock. Queue advancement waits for the sink's consumed frame boundary. */
    private boolean advanceCrossfade(State playback) {
        if (crossfade != null) {
            switch (output.crossfadeStatus(crossfade.token())) {
                case COMPLETE -> { promoteCrossfade(); return true; }
                case MIXING -> { return true; }
                case REJECTED -> discardCrossfade();
                default -> { }
            }
        }
        long requested = crossfadeSeconds * 1000L;
        long left = duration - output.positionMs();
        if (requested == 0 || crossfadeAttempted || playback != State.PLAYING || !playRequested
                || !output.supportsCrossfade() || preview || offset != 0 || duration < requested * 2
                || left > requested + 8000 || left < requested) return false;
        int next = queue.peekCrossfadeSuccessor();
        if (next < 0) return false;
        var song = queue.tracks.get(next);
        if (!song.source().equals(track.source()) || song.id().equals(track.id())) return false;
        crossfadeAttempted = true;
        OnlineMusicSource provider;
        try { provider=source(song.source()).requestSnapshot(); }
        catch (RuntimeException unavailable) { return false; }
        var pending = new PendingCrossfade(++crossfadeToken,generation,output,song,provider,
                audioCache.revision(),crossfadeSeconds,duration);
        crossfade = pending;
        crossfadeTask = workers.submit(() -> prepareCrossfade(pending));
        return false;
    }

    private void prepareCrossfade(PendingCrossfade pending) {
        boolean queued = false;
        try {
            var song = pending.song();
            var stream = pending.provider().resolve(song);
            if (stream == null || stream.preview() || stream.offsetMs() != 0
                    || stream.durationMs() < 2000L * pending.seconds()) return;
            var key = new SessionAudioCache.Key(song.source(), song.id(), song.mediaId(), stream);
            byte[] bytes;
            synchronized (this) {
                if (crossfade != pending) return;
                bytes = audioCache.get(key);
            }
            if (bytes == null) bytes = download.read(stream.uri());
            if (bytes.length == 0 || bytes.length > MusicHttp.AUDIO_LIMIT) return;
            synchronized (this) {
                if (crossfade != pending || generation != pending.generation()) return;
                // Metadata must exist before the audio worker can claim the registered successor.
                crossfadeAudio = new PreparedCrossfade(bytes, key, stream);
            }
            queued = pending.output().queueCrossfade(pending.token(), bytes, pending.seconds(), pending.outgoingDuration());
        } catch (Exception ignored) {
            // Optional prefetch has no authority to replace the current track on failure.
        } finally {
            synchronized (this) {
                if (crossfade != pending) pending.output().cancelCrossfade(pending.token());
                else if (!queued) discardCrossfade();
            }
        }
    }

    private void promoteCrossfade() {
        var pending = crossfade;
        var prepared = Objects.requireNonNull(crossfadeAudio);
        output.acceptCrossfade(pending.token());
        cancelQueueProgress();
        // Keep the existing line and already-playing decoder, but rotate session jobs and metadata.
        generation++;
        if (lyricTask != null) lyricTask.cancel(true);
        track = pending.song(); audio = prepared.bytes(); audioKey = prepared.key();
        audioCache.put(audioKey, audio, pending.cacheRevision());
        duration = prepared.stream().durationMs(); offset = 0; preview = false;
        failure = Failure.NONE; state = State.PLAYING;
        lyrics = LyricTimeline.empty(); lyricsState = LyricsState.LOADING; feed.clear();
        queue.index = queue.order.next(true);
        queue.attachedGeneration = generation;
        startLyrics(pending.provider(),track,generation);
        queue.watch();
    }

    private final class PlayerQueue implements OnlineMusicQueue {
        private List<OnlineTrack> tracks = List.of(), undoTracks;
        private int index = -1, undoIndex;
        private final PlaybackOrder order = new PlaybackOrder();
        private long attachedGeneration = -1, undoGeneration;
        private PlaybackOrder.Saved undoOrder;
        private List<Integer> undoPositions;
        @Override public boolean supportsCheckpoints() { return queueStore != null; }
        private MusicQueueStore checkpointStore() {
            requireOpen();
            if (queueStore == null) throw new UnsupportedOperationException("Queue checkpoints unavailable");
            return queueStore;
        }
        @Override public void saveCheckpoint() throws java.io.IOException {
            MusicQueueStore store; MusicQueueStore.Saved saved;
            synchronized (OnlinePlayerSession.this) { store = checkpointStore(); saved = new MusicQueueStore.Saved(tracks, order.mode()); }
            store.save(saved);
        }
        @Override public boolean restoreCheckpoint() throws java.io.IOException {
            MusicQueueStore store; List<OnlineTrack> previous; Mode previousMode; int previousIndex; long request;
            synchronized (OnlinePlayerSession.this) {
                store = checkpointStore(); previous = tracks; previousMode = order.mode(); previousIndex = index; request = generation;
            }
            var saved = store.load();
            synchronized (OnlinePlayerSession.this) {
                requireOpen();
                if (saved.isEmpty()) return false;
                if (tracks != previous || order.mode() != previousMode || index != previousIndex || generation != request)
                    throw new java.io.IOException("Queue changed while loading the checkpoint; retry explicitly");
                for (var song : saved.get().tracks()) validateTrack(song);
                beforeQueueEdit();
                cancelQueueProgress();
                tracks = saved.get().tracks(); index = -1; undoTracks = null; undoOrder = null; undoPositions = null; attachedGeneration = -1;
                order.mode(saved.get().mode()); order.reset(tracks.size(), -1);
                return true;
            }
        }
        @Override public void deleteCheckpoint() throws java.io.IOException {
            MusicQueueStore store;
            synchronized (OnlinePlayerSession.this) { store = checkpointStore(); }
            store.delete();
        }
        @Override public Snapshot snapshot() {
            synchronized (OnlinePlayerSession.this) { return new Snapshot(tracks, index, undoTracks != null, order.mode(),
                    order.canPrevious(), order.canNext()); }
        }
        @Override public void replace(List<OnlineTrack> next, int selected) {
            synchronized (OnlinePlayerSession.this) {
                requireOpen();
                if (selected < 0 || selected >= next.size()) return;
                var copy = List.copyOf(next);
                for (var song : copy) validateTrack(song);
                // Start before committing the collection so a rejected selection preserves it.
                play(copy.get(selected));
                tracks = copy; index = selected; undoTracks = null;
                order.reset(tracks.size(), index); attachedGeneration = generation;
                watch();
            }
        }
        @Override public void select(int selected) {
            synchronized (OnlinePlayerSession.this) {
                requireOpen();
                if (selected < 0 || selected >= tracks.size()) return;
                order.reset(tracks.size(), selected); playIndex(selected);
            }
        }
        @Override public boolean supportsModes() { return true; }
        @Override public Mode mode() { synchronized (OnlinePlayerSession.this) { return order.mode(); } }
        @Override public void mode(Mode mode) {
            synchronized (OnlinePlayerSession.this) {
                requireOpen(); Objects.requireNonNull(mode);
                if (mode == order.mode()) return;
                beforeQueueEdit();
                order.mode(mode);
                cancelQueueProgress();
                if (attachedGeneration == generation && index >= 0
                        && OnlinePlayerSession.this.snapshot().state() != State.ENDED) watch();
            }
        }
        private void playIndex(int selected) {
            play(tracks.get(selected)); index = selected; attachedGeneration = generation; watch();
        }
        private int peekCrossfadeSuccessor() {
            if (attachedGeneration != generation || index < 0 || index >= tracks.size()) return -1;
            return switch (order.mode()) {
                case SEQUENTIAL -> index + 1 < tracks.size() ? index + 1 : -1;
                case REPEAT_ALL -> tracks.size() > 1 ? (index + 1) % tracks.size() : -1;
                default -> -1;
            };
        }
        private void watch() {
            if (attachedGeneration == generation && index >= 0) {
                long request = generation, watch = queueWatch;
                queueTask = queueWorker.scheduleWithFixedDelay(() -> advanceQueue(request, watch), 50, 50, TimeUnit.MILLISECONDS);
            }
        }
        @Override public void next() {
            synchronized (OnlinePlayerSession.this) { requireOpen(); int next = order.next(false); if (next >= 0) playIndex(next); }
        }
        @Override public void previous() {
            synchronized (OnlinePlayerSession.this) { requireOpen(); int previous = order.previous(); if (previous >= 0) playIndex(previous); }
        }
        private void remember() {
            undoTracks = tracks; undoIndex = index; undoGeneration = generation; undoOrder = order.save();
            undoPositions = new ArrayList<>();
            for (int i = 0; i < tracks.size(); i++) undoPositions.add(i);
        }
        @Override public boolean supportsEditing() { return true; }
        @Override public void append(List<OnlineTrack> additions) {
            synchronized (OnlinePlayerSession.this) {
                requireOpen();
                var added = List.copyOf(additions);
                if (added.isEmpty()) return;
                for (var song : added) validateTrack(song);
                beforeQueueEdit();
                remember();
                var copy = new ArrayList<>(tracks); copy.addAll(added);
                for (int i = 0; i < added.size(); i++) undoPositions.add(-1);
                tracks = List.copyOf(copy); order.reset(tracks.size(), index);
            }
        }
        @Override public void move(int from, int to) {
            synchronized (OnlinePlayerSession.this) {
                requireOpen();
                if (from < 0 || to < 0 || from >= tracks.size() || to >= tracks.size() || from == to) return;
                beforeQueueEdit();
                remember();
                var copy = new ArrayList<>(tracks); copy.add(to, copy.remove(from));
                undoPositions.add(to, undoPositions.remove(from));
                tracks = List.copyOf(copy);
                if (index == from) index = to;
                else if (from < index && index <= to) index--;
                else if (to <= index && index < from) index++;
                order.reset(tracks.size(), index);
            }
        }
        @Override public void remove(int selected) {
            synchronized (OnlinePlayerSession.this) {
                requireOpen();
                if (selected < 0 || selected >= tracks.size()) return;
                beforeQueueEdit();
                remember(); var copy = new ArrayList<>(tracks); copy.remove(selected); tracks = List.copyOf(copy);
                undoPositions.remove(selected);
                if (selected == index) { index = -1; attachedGeneration = -1; cancelQueueProgress(); }
                else if (selected < index) index--;
                order.reset(tracks.size(), index);
            }
        }
        @Override public void clear() {
            synchronized (OnlinePlayerSession.this) {
                requireOpen(); if (tracks.isEmpty()) return;
                beforeQueueEdit();
                remember(); undoPositions.clear(); tracks = List.of(); index = -1; order.reset(0, -1); attachedGeneration = -1; cancelQueueProgress();
            }
        }
        @Override public void undo() {
            synchronized (OnlinePlayerSession.this) {
                requireOpen(); if (undoTracks == null) return;
                beforeQueueEdit();
                boolean advancing = queueTask != null;
                int restoredIndex = index < 0 || attachedGeneration != generation ? -1 : undoPositions.get(index);
                tracks = undoTracks; index = restoredIndex;
                undoTracks = null; undoPositions = null;
                if (generation == undoGeneration) {
                    index = undoIndex;
                    if (order.mode() == undoOrder.mode()) order.restore(undoOrder);
                    else order.reset(tracks.size(), index);
                } else order.reset(tracks.size(), index);
                cancelQueueProgress();
                // Keep an attached queue running; restoring a detached one never restarts it.
                if (advancing) watch();
            }
        }
    }

    private OnlineMusicSource source(String id) {
        var provider = sources.get(id);
        if (provider == null) throw new IllegalArgumentException("Unknown music source");
        return provider;
    }
    private void requireOpen() { if (state == State.CLOSED) throw new IllegalStateException("Music session closed"); }
    @Override public void close() {
        List<CompletableFuture<?>> obsolete;
        synchronized (this) {
            if (state == State.CLOSED) return;
            reset(); audioCache.clear(); track = null; state = State.CLOSED;
            operations.close();
            if (login != null) { login.close(); login = null; }
            authenticated.clear(); credentials.clear(); remembered.clear(); persistenceVersions.clear(); searchIndex.clear();
            obsolete = List.copyOf(searches.keySet()); searches.clear(); runningLikes.clear();
            workers.shutdownNow();
            loginWorker.shutdownNow();
            queueWorker.shutdownNow();
            queue.tracks = List.of(); queue.undoTracks = null; queue.undoPositions = null; queue.index = -1; queue.order.reset(0, -1);
            for (var provider : sources.values()) {
                try { provider.credentials(""); } catch (UnsupportedOperationException ignored) { }
            }
            sources.clear();
        }
        cancelRequests(obsolete);
    }
}
