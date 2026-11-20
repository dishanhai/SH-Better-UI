// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicQueue;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlinePlaylist;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlinePlaylistDetail;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToLongFunction;

/** Source-owned library reads. Only displayed, current account snapshots can become playback queues. */
final class MusicFrontendLibrary {
    static final int PAGE_SIZE = 20;
    private static final int MAX_PAGE = 1000;
    private static final int MAX_BOOKMARKS = 16;

    enum Kind { PLAYLISTS, LIKED, DETAIL }
    enum Status { IDLE, LOADING, READY, EMPTY, SIGN_IN, AUTHORIZATION, CHANGED, FAILED, UNSUPPORTED }

    record Key(String source, Kind kind, String id) {
        Key {
            Objects.requireNonNull(source); Objects.requireNonNull(kind); Objects.requireNonNull(id);
            if (source.isBlank() || (kind == Kind.DETAIL) != !id.isBlank()) throw new IllegalArgumentException("Invalid library route");
        }
        static Key collection(String source, Kind kind) { return new Key(source, kind, ""); }
        static Key detail(String source, String id) { return new Key(source, Kind.DETAIL, id); }
    }
    record View(Key key, int page, List<OnlinePlaylist> playlists, List<OnlineTrack> tracks,
                OnlinePlaylist detail, boolean more, Status status) {}

    private final OnlineMusicPlayer player;
    private final ToLongFunction<String> accountVersion;
    private final LinkedHashMap<Key, Integer> pages = new LinkedHashMap<>(16, .75f, true);
    private Pending pending;
    private View view = blank(null, 1, Status.IDLE);
    private long seenVersion;
    private final LinkedHashMap<Key, Feed> feeds = new LinkedHashMap<>(4, .75f, true);
    private record Feed(View view, long epoch, int failedPage) {}
    private int failedExtension;

    private record Pending(CompletableFuture<?> future, Key key, int page, long version, boolean append) {}

    MusicFrontendLibrary(OnlineMusicPlayer player) {
        this(player, SessionMusicOperations.forPlayer(player)
                .<ToLongFunction<String>>map(operations -> operations::accountVersion).orElse(source -> 0));
    }
    MusicFrontendLibrary(OnlineMusicPlayer player, ToLongFunction<String> accountVersion) {
        this.player = Objects.requireNonNull(player);
        this.accountVersion = Objects.requireNonNull(accountVersion);
    }
    private static View blank(Key key, int page, Status status) {
        return new View(key, page, List.of(), List.of(), null, false, status);
    }
    View view() { return view; }

    List<String> providers() {
        return player.sources().stream().filter(source -> NativePlaylistDiscovery.discoveryUrl(source) != null).distinct().toList();
    }

    void open(Key key) {
        if (Objects.equals(view.key(), key)) return;
        rememberFeed();
        if (key == null) { discard(); view = blank(null, 1, Status.IDLE); failedExtension = 0; return; }
        var saved = feeds.remove(key);
        if (saved != null) {
            synchronized (player) {
                if (saved.epoch() == accountVersion.applyAsLong(key.source()) && player.authenticated(key.source())) {
                    discard();
                    view = saved.view();
                    seenVersion = saved.epoch();
                    failedExtension = saved.failedPage();
                    return;
                }
            }
        }
        load(key, pages.getOrDefault(key, 1));
    }

    private void rememberFeed() {
        if (view.key() == null || !current(view)) return;
        if ((view.page() > 1 && view.page() != pages.getOrDefault(view.key(),1)) || failedExtension > 0
                || pending != null && pending.append()) {
            var saved = view.status() == Status.LOADING
                    ? new View(view.key(), view.page(), view.playlists(), view.tracks(), null, true, Status.READY)
                    : view;
            feeds.put(view.key(), new Feed(saved, seenVersion, failedExtension));
            if (feeds.size() > 4) feeds.remove(feeds.keySet().iterator().next());
        }
    }

    void retry() {
        if (failedExtension > 0 && view.key() != null) {
            load(view.key(), failedExtension, true);
        } else if (view.key() != null && view.status() != Status.LOADING) load(view.key(), view.page());
    }

    void page(int direction) {
        if (view.key() == null || view.key().kind() == Kind.DETAIL || view.status() == Status.LOADING) return;
        if (direction < 0 && view.page() > 1 || direction > 0 && view.more()) load(view.key(), view.page() + Integer.signum(direction));
    }

    boolean canExtend() { return pending == null && failedExtension == 0 && view.status() == Status.READY && view.more(); }

    void extend() { if (canExtend()) load(view.key(), view.page() + 1, true); }

    private void load(Key key, int page) { load(key, page, false); }
    private void load(Key key, int page, boolean append) {
        if (!append) { discard(); pages.put(key, page); }
        if (pages.size() > MAX_BOOKMARKS) {
            var oldest = pages.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        synchronized (player) {
            if (append && !current(view)) return;
            failedExtension = 0;
            seenVersion = accountVersion.applyAsLong(key.source());
            if (!providers().contains(key.source()) || key.kind() == Kind.DETAIL && !player.supportsPlaylistDetail(key.source())) {
                view = blank(key, page, Status.UNSUPPORTED); return;
            }
            if (!player.authenticated(key.source())) { view = blank(key, page, Status.SIGN_IN); return; }
            view = append ? new View(key, view.page(), view.playlists(), view.tracks(), null, false, Status.LOADING)
                    : blank(key, page, Status.LOADING);
            try {
                var future = switch (key.kind()) {
                    case PLAYLISTS -> player.playlists(key.source(), page);
                    case LIKED -> player.liked(key.source(), page);
                    case DETAIL -> player.playlistDetail(key.source(), key.id());
                };
                pending = new Pending(future, key, page, seenVersion, append);
            } catch (RuntimeException failure) {
                fail(key, page, append, error(failure));
            }
        }
    }

    void poll() {
        Pending obsolete;
        synchronized (player) { obsolete = pollLocked(); }
        if (obsolete != null) obsolete.future().cancel(true);
    }

    private Pending pollLocked() {
        Key key = view.key();
        if (key == null) return null;
        long actual = accountVersion.applyAsLong(key.source());
        boolean rejected = pending != null && pending.future().isCompletedExceptionally()
                && error(pending.future().handle((result, failure) -> failure).join()) == Status.AUTHORIZATION;
        if (actual != seenVersion) {
            var obsolete = pending;
            pending = null;
            seenVersion = actual;
            failedExtension = 0;
            view = blank(key, view.page(), rejected && !player.authenticated(key.source())
                    ? Status.AUTHORIZATION : Status.CHANGED);
            return obsolete;
        }
        if ((view.status() == Status.READY || view.status() == Status.EMPTY || view.status() == Status.LOADING)
                && !player.authenticated(key.source())) {
            var obsolete = pending;
            pending = null;
            view = blank(key, view.page(), rejected ? Status.AUTHORIZATION : Status.SIGN_IN);
            return obsolete;
        }
        if (pending == null || !pending.future().isDone()) return null;
        var current = pending;
        pending = null;
        try {
            if (current.version() == actual && current.key().equals(key)) {
                var loaded = parse(current, current.future().join());
                if (!current.append()) view = loaded;
                else if (loaded.status() == Status.FAILED) fail(key, current.page(), true, Status.FAILED);
                else view = new View(key, loaded.page(),
                        MusicPageAppend.append(view.playlists(), loaded.playlists()),
                        MusicPageAppend.append(view.tracks(), loaded.tracks()), null, loaded.more(), Status.READY);
            }
        } catch (RuntimeException failure) {
            fail(key, current.page(), current.append(), error(failure));
        }
        return null;
    }

    private void fail(Key key, int page, boolean append, Status status) {
        if (append && status == Status.FAILED) {
            failedExtension = page;
            view = new View(key, view.page(), view.playlists(), view.tracks(), null, false, status);
        } else view = blank(key, page, status);
    }

    boolean extensionFailed() { return failedExtension > 0; }

    private static View parse(Pending request, Object result) {
        var key = request.key();
        int page = request.page();
        if (key.kind() == Kind.DETAIL) {
            var detail = (OnlinePlaylistDetail) result;
            if (!detail.playlist().source().equals(key.source()) || !detail.playlist().id().equals(key.id()))
                return blank(key, page, Status.FAILED);
            return new View(key, 1, List.of(), detail.tracks(), detail.playlist(), false,
                    detail.tracks().isEmpty() ? Status.EMPTY : Status.READY);
        }
        var values = (List<?>) result;
        boolean more = values.size() == PAGE_SIZE && page < MAX_PAGE;
        if (values.size() > PAGE_SIZE) return blank(key, page, Status.FAILED);
        if (key.kind() == Kind.PLAYLISTS) {
            var playlists = values.stream().map(OnlinePlaylist.class::cast).toList();
            if (playlists.stream().anyMatch(item -> !item.source().equals(key.source()))) return blank(key, page, Status.FAILED);
            return new View(key, page, playlists, List.of(), null, more, playlists.isEmpty() ? Status.EMPTY : Status.READY);
        }
        var songs = values.stream().map(OnlineTrack.class::cast).toList();
        if (songs.stream().anyMatch(song -> !song.source().equals(key.source()))) return blank(key, page, Status.FAILED);
        return new View(key, page, List.of(), songs, null, more, songs.isEmpty() ? Status.EMPTY : Status.READY);
    }

    private static Status error(Throwable failure) {
        return LibraryAccessFeedback.AUTHORIZATION_REQUIRED.equals(LibraryAccessFeedback.failure(failure))
                ? Status.AUTHORIZATION : Status.FAILED;
    }

    boolean current(View shown) {
        synchronized (player) {
            return shown != null && shown == view && shown.key() != null && seenVersion == accountVersion.applyAsLong(shown.key().source())
                    && player.authenticated(shown.key().source());
        }
    }

    boolean play(View shown, int index) {
        synchronized (player) {
            if (!current(shown) || index < 0 || index >= shown.tracks().size()) return false;
            var queue = player.queue();
            if (queue == null || !queue.supportsModeReplacement()) return false;
            queue.replace(shown.tracks(), index, OnlineMusicQueue.Mode.SEQUENTIAL);
            return true;
        }
    }

    private void discard() {
        var obsolete = pending;
        pending = null;
        if (obsolete != null) obsolete.future().cancel(true);
    }
    void clear() { discard(); feeds.clear(); failedExtension = 0; view = blank(null, 1, Status.IDLE); }
}
