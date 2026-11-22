// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToLongFunction;

/** Account-scoped detail data. Track order is supplied by the provider. */
final class ArtistDetailModel {
    final OnlineMusicPlayer player;
    private final ToLongFunction<String> epoch;
    OnlineArtist artist;
    OnlineAlbum album;
    OnlineAlbumDetail release;
    OnlineArtistOverview overview;
    List<OnlineAlbum> albums = List.of();
    int albumPage;
    long total = -1;
    boolean overviewFailed;
    private CompletableFuture<OnlineArtistOverview> overviewRequest;
    int page = 1;
    String status = "empty";
    List<OnlineTrack> tracks = List.of();
    boolean more;
    private long openedEpoch;
    private boolean extending;
    private int requestPage;
    private CompletableFuture<?> pending;

    ArtistDetailModel(OnlineMusicPlayer player) {
        this(player, SessionMusicOperations.forPlayer(player).<ToLongFunction<String>>map(ops -> ops::accountVersion)
                .orElse(source -> 0L));
    }

    ArtistDetailModel(OnlineMusicPlayer player, ToLongFunction<String> epoch) {
        this.player = Objects.requireNonNull(player);
        this.epoch = Objects.requireNonNull(epoch);
    }

    void open(OnlineArtist artist) {
        synchronized (player) {
            this.artist = Objects.requireNonNull(artist);
            album = null;
            openedEpoch = epoch.applyAsLong(artist.source());
            load(1);
            if (player.supportsArtistOverview(artist.source())) loadOverview();
        }
    }

    void open(OnlineAlbum album) {
        synchronized (player) {
            artist = null;
            this.album = Objects.requireNonNull(album);
            openedEpoch = epoch.applyAsLong(album.source());
            load(1);
        }
    }

    String source() { return artist != null ? artist.source() : album != null ? album.source() : ""; }

    void loadOverview() {
        synchronized (player) {
            if (artist == null || overviewLoading() || !ensureContext()) return;
            overviewFailed = false;
            try { overviewRequest = Objects.requireNonNull(player.artistOverview(artist, albumPage + 1)); }
            catch (RuntimeException failure) { overviewFailed = true; }
        }
    }

    boolean overviewLoading() { return overviewRequest != null; }

    void load(int requestedPage) {
        synchronized (player) {
            cancel();
            page = Math.max(1, requestedPage);
            fetch(page, false);
        }
    }

    void extend() {
        synchronized (player) {
            if (more && !loading() && page < 1000) fetch(page + 1, true);
        }
    }

    void retry() {
        synchronized (player) {
            if (!loading() && status.equals("failed")) fetch(requestPage, extending);
        }
    }

    private boolean contextCurrent() {
        String source = source();
        return !source.isEmpty() && epoch.applyAsLong(source) == openedEpoch && player.sources().contains(source)
                && (artist != null ? player.supportsArtistTracks(source) : player.supportsAlbumDetail(source));
    }

    private boolean ensureContext() {
        if (contextCurrent()) return true;
        cancel();
        status = "unavailable";
        return false;
    }

    private void fetch(int page, boolean append) {
        extending = append;
        requestPage = page;
        if (!ensureContext()) return;
        status = "loading";
        try { pending = Objects.requireNonNull(artist != null ? player.artistTracks(artist, page) : player.albumDetail(album)); }
        catch (RuntimeException failure) { fail(); }
    }

    void poll() {
        synchronized (player) {
            if (source().isEmpty() || !ensureContext()) return;
            pollOverview();
            if (pending == null || !pending.isDone()) return;
            var request = pending;
            pending = null;
            try {
                var value = request.join();
                var incoming = value instanceof OnlineAlbumDetail detail ? detail.tracks() : ((OnlineArtistTracks) value).tracks();
                if (incoming.stream().anyMatch(track -> !source().equals(track.source()))) {
                    fail();
                    return;
                }
                if (value instanceof OnlineAlbumDetail detail) {
                    if (!album.id().equals(detail.album().id()) || !album.source().equals(detail.album().source())) {
                        fail();
                        return;
                    }
                    release = detail;
                    total = incoming.size();
                } else total = ((OnlineArtistTracks) value).total();
                tracks = extending ? MusicPageAppend.append(tracks, incoming) : incoming;
                page = requestPage;
                more = value instanceof OnlineArtistTracks result && result.mayHaveMore() && page < 1000;
                status = tracks.isEmpty() ? "empty" : "ready";
            } catch (RuntimeException failure) { fail(); }
        }
    }

    private void pollOverview() {
        if (overviewRequest == null || !overviewRequest.isDone()) return;
        var request = overviewRequest;
        overviewRequest = null;
        try {
            var next = request.join();
            var releases = new java.util.LinkedHashMap<String, OnlineAlbum>();
            albums.forEach(item -> releases.put(item.id(), item));
            next.albums().stream().filter(item -> source().equals(item.source()))
                    .forEach(item -> releases.putIfAbsent(item.id(), item));
            albums = List.copyOf(releases.values());
            overview = overview == null ? next : new OnlineArtistOverview(overview.artworkUrl(), overview.followers(),
                    overview.description(), albums, next.albumCount(), next.mayHaveMore());
            albumPage++;
        } catch (RuntimeException failure) { overviewFailed = true; }
    }

    private void fail() {
        more = false;
        status = "failed";
    }

    boolean loading() { return pending != null; }

    void cancel() {
        var request = pending;
        pending = null;
        if (request != null) request.cancel(true);
        if (overviewRequest != null) overviewRequest.cancel(true);
        overviewRequest = null;
        overview = null;
        overviewFailed = false;
        release = null;
        albumPage = 0;
        albums = List.of();
        total = -1;
        tracks = List.of();
        more = false;
        status = "cancelled";
    }

    boolean play(int index) { return play(tracks, index); }

    boolean play(List<OnlineTrack> displayed, int index) {
        synchronized (player) {
            if (!ensureContext() || displayed != tracks || index < 0 || index >= tracks.size()) return false;
            var queue = player.queue();
            if (queue == null || !queue.supportsModeReplacement())
                throw new UnsupportedOperationException("Artist context playback unavailable");
            queue.replace(tracks, index, OnlineMusicQueue.Mode.SEQUENTIAL);
            return true;
        }
    }
}
