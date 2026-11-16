// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.client.lyrics.core.sync.TrackIdentity;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** One owner, one audible track. Generations reject stale network and lyric completions. */
final class OnlinePlayerSession implements OnlineMusicPlayer {
    @FunctionalInterface interface Download { byte[] read(java.net.URI uri) throws Exception; }
    private final Map<String, OnlineMusicSource> sources = new LinkedHashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(3, task -> {
        var thread = new Thread(task, "shb-online-music"); thread.setDaemon(true); return thread;
    });
    private final Supplier<PlaybackOutput> outputs;
    private final Download download;
    private final LocalLyricFeed feed = new LocalLyricFeed("sh_better_ui:online_music");
    private final Set<CompletableFuture<?>> searches = new HashSet<>();
    private final Set<String> authenticated = new HashSet<>();
    private final ScheduledThreadPoolExecutor loginWorker = new ScheduledThreadPoolExecutor(1, task -> {
        var thread = new Thread(task, "shb-music-login"); thread.setDaemon(true); return thread;
    });
    private QrLoginSession login;
    private PlaybackOutput output;
    private Future<?> audioTask, lyricTask;
    private OnlineTrack track;
    private byte[] audio;
    private long generation, duration, offset;
    private boolean preview, playRequested;
    private State state = State.IDLE;
    private Failure failure = Failure.NONE;
    private LyricsState lyricsState = LyricsState.EMPTY;
    private LyricTimeline lyrics = LyricTimeline.empty();

    OnlinePlayerSession(List<OnlineMusicSource> sources) { this(sources, Mp3Output::new, new MusicHttp()::audio); }
    OnlinePlayerSession(List<OnlineMusicSource> providers, Supplier<PlaybackOutput> outputs, Download download) {
        this.outputs = outputs; this.download = download;
        loginWorker.setRemoveOnCancelPolicy(true);
        for (var provider : providers) {
            if (sources.putIfAbsent(provider.id(), provider) != null) throw new IllegalArgumentException("Duplicate music source");
        }
    }
    @Override public synchronized List<String> sources() { return List.copyOf(sources.keySet()); }
    @Override public synchronized void credentials(String sourceId, String cookie) {
        requireOpen();
        var provider = source(sourceId);
        provider.credentials(cookie);
        if (login != null) login.close();
        if (cookie.isBlank()) authenticated.remove(sourceId); else authenticated.add(sourceId);
        stop();
    }
    @Override public synchronized boolean authenticated(String sourceId) {
        requireOpen(); source(sourceId); return authenticated.contains(sourceId);
    }
    @Override public synchronized OnlineMusicLogin login(String sourceId) {
        requireOpen();
        var provider = source(sourceId);
        if (login != null) login.close();
        login = new QrLoginSession(provider, (attempt, cookie) -> {
            synchronized (OnlinePlayerSession.this) {
                if (state == State.CLOSED || login != attempt) { attempt.close(); return; }
                attempt.confirm(() -> {
                    provider.credentials(cookie); authenticated.add(sourceId); stop();
                });
            }
        }, loginWorker);
        login.start();
        return login;
    }
    @Override public synchronized CompletableFuture<List<OnlineTrack>> search(String sourceId, String query, int page) {
        requireOpen();
        var provider = source(sourceId);
        String text = Objects.requireNonNull(query).strip();
        if (text.isEmpty() || text.length() > 200 || page < 1 || page > 1000) throw new IllegalArgumentException("Invalid music search");
        var result = new CompletableFuture<List<OnlineTrack>>();
        searches.add(result);
        var task = workers.submit(() -> {
            try { result.complete(List.copyOf(provider.search(text, page, 20))); }
            catch (Exception error) { result.completeExceptionally(error); }
        });
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) task.cancel(true);
            synchronized (this) { searches.remove(result); }
        });
        return result;
    }
    @Override public synchronized void play(OnlineTrack next) {
        requireOpen();
        var provider = source(next.source());
        if (!next.id().matches("[A-Za-z0-9]{1,40}") || !next.mediaId().matches("[A-Za-z0-9]{1,40}"))
            throw new IllegalArgumentException("Invalid platform track identifier");
        reset();
        track = next; duration = next.durationMs(); state = State.LOADING; playRequested = true;
        lyricsState = LyricsState.LOADING;
        long request = generation;
        audioTask = workers.submit(() -> loadAudio(provider, next, request));
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
    private void loadAudio(OnlineMusicSource provider, OnlineTrack next, long request) {
        Failure problem = Failure.NETWORK;
        try {
            var stream = provider.resolve(next);
            if (stream == null) { problem = Failure.UNAVAILABLE; throw new IllegalStateException("No granted stream"); }
            var bytes = download.read(stream.uri());
            synchronized (this) {
                if (generation != request || state == State.CLOSED) return;
                audio = bytes; offset = Math.max(0, stream.offsetMs());
                duration = Math.max(0, stream.durationMs()); preview = stream.preview();
                output = outputs.get(); output.start(audio, 0, playRequested);
            }
            return;
        } catch (OnlineMusicSource.LoginRequired ignored) { problem = Failure.LOGIN_REQUIRED; }
        catch (Exception ignored) { }
        synchronized (this) {
            if (generation == request && state != State.CLOSED) { failure = problem; state = State.FAILED; }
        }
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
        long target = Math.max(0, duration > 0 ? Math.min(duration, positionMs) : positionMs);
        output.close();
        output = outputs.get();
        output.start(audio, target, playing);
        feed.clear();
    }
    @Override public synchronized Snapshot snapshot() {
        State current = output == null ? state : output.state();
        long position = output == null ? 0 : output.positionMs();
        if (duration > 0) position = Math.min(duration, position);
        return new Snapshot(track, current, position, duration, lyricsState, lyrics,
                current == State.FAILED && failure == Failure.NONE ? Failure.AUDIO : failure, preview);
    }
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
        generation++;
        if (audioTask != null) audioTask.cancel(true);
        if (lyricTask != null) lyricTask.cancel(true);
        if (output != null) output.close();
        output = null; audio = null; duration = offset = 0; preview = false;
        failure = Failure.NONE; lyricsState = LyricsState.EMPTY; lyrics = LyricTimeline.empty(); feed.clear();
    }
    private OnlineMusicSource source(String id) {
        var provider = sources.get(id);
        if (provider == null) throw new IllegalArgumentException("Unknown music source");
        return provider;
    }
    private void requireOpen() { if (state == State.CLOSED) throw new IllegalStateException("Music session closed"); }
    @Override public synchronized void close() {
        if (state == State.CLOSED) return;
        reset(); track = null; state = State.CLOSED;
        if (login != null) { login.close(); login = null; }
        authenticated.clear();
        for (var search : List.copyOf(searches)) search.cancel(true);
        workers.shutdownNow();
        loginWorker.shutdownNow();
        for (var provider : sources.values()) {
            try { provider.credentials(""); } catch (UnsupportedOperationException ignored) { }
        }
        sources.clear();
    }
}
