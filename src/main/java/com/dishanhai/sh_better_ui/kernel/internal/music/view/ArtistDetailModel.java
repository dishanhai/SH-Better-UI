// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToLongFunction;

/** A cancellable, account-scoped feed of provider artist matches, in provider order. */
final class ArtistDetailModel {
    final OnlineMusicPlayer player;
    private final ToLongFunction<String> epoch;
    OnlineArtist artist;
    int page = 1;
    String status = "empty";
    List<OnlineTrack> tracks = List.of();
    boolean more;
    private long openedEpoch;
    private boolean extending;
    private int requestPage;
    private CompletableFuture<OnlineArtistTracks> pending;

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
            openedEpoch = epoch.applyAsLong(artist.source());
            load(1);
        }
    }

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
        return artist != null && epoch.applyAsLong(artist.source()) == openedEpoch
                && player.sources().contains(artist.source()) && player.supportsArtistTracks(artist.source());
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
        try { pending = Objects.requireNonNull(player.artistTracks(artist, page)); }
        catch (RuntimeException failure) { fail(); }
    }

    void poll() {
        synchronized (player) {
            if (artist == null || !ensureContext() || pending == null || !pending.isDone()) return;
            var request = pending;
            pending = null;
            try {
                var result = request.join();
                if (result.tracks().stream().anyMatch(track -> !artist.source().equals(track.source()))) {
                    fail();
                    return;
                }
                tracks = extending ? MusicPageAppend.append(tracks, result.tracks()) : result.tracks();
                page = requestPage;
                more = result.mayHaveMore() && page < 1000;
                status = tracks.isEmpty() ? "empty" : "ready";
            } catch (RuntimeException failure) { fail(); }
        }
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
