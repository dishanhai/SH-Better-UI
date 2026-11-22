// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.kernel.api.music.platform.QQTrackFavorites;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/** Batches visible tracks and publishes only results belonging to the current account. */
public final class ClientQQTrackFavorites implements QQTrackFavorites {
    private static final int BATCH_SIZE = 50;
    private static final long RETRY_NANOS = 10_000_000_000L;
    private static final long REFRESH_NANOS = 120_000_000_000L;

    private record Entry(State state, long retryAt) {}
    private static final Entry LOADING = new Entry(State.LOADING, 0L);
    private final Executor executor;
    private final Supplier<QQMusicSource> providerFactory;
    private final Map<String, Entry> tracks = new LinkedHashMap<>();
    private final LinkedHashSet<String> pendingReads = new LinkedHashSet<>();
    private String cookie = "";
    private QQMusicSource source;
    private boolean reading;

    public ClientQQTrackFavorites(Executor executor) {
        this(executor, QQMusicSource::new);
    }

    ClientQQTrackFavorites(Executor executor, Supplier<QQMusicSource> providerFactory) {
        this.executor = Objects.requireNonNull(executor);
        this.providerFactory = providerFactory;
    }

    @Override public synchronized void credentials(String cookie) {
        String value = MusicHttp.cookie(cookie);
        if (this.cookie.equals(value)) return;
        var provider = value.isEmpty() ? null : providerFactory.get();
        if (provider != null) provider.credentials(value);
        this.cookie = value;
        this.source = provider;
        tracks.clear();
        pendingReads.clear();
        reading = false;
    }

    @Override public synchronized State state(String mid) {
        checkMid(mid);
        if (source == null) return State.SIGNED_OUT;
        var entry = tracks.get(mid);
        if (entry == null || entry.retryAt != 0L && System.nanoTime() - entry.retryAt >= 0L) {
            entry = entry == null || entry.state == State.UNAVAILABLE ? LOADING : new Entry(entry.state, 0L);
            tracks.put(mid, entry);
            pendingReads.add(mid);
        }
        return entry.state;
    }

    @Override public synchronized void loadPending() {
        if (source == null || reading || pendingReads.isEmpty()) return;
        List<String> mids = pendingReads.stream().limit(BATCH_SIZE).toList();
        mids.forEach(pendingReads::remove);
        Map<String, Entry> requested = new LinkedHashMap<>();
        mids.forEach(mid -> requested.put(mid, tracks.get(mid)));
        QQMusicSource account = source;
        reading = true;
        try {
            CompletableFuture.supplyAsync(() -> {
                try {
                    return account.favoriteStatuses(mids);
                } catch (Exception failure) {
                    throw new CompletionException(failure);
                }
            }, executor).whenComplete((statuses, failure) -> finishLoad(account, requested, statuses));
        } catch (RejectedExecutionException failure) {
            finishLoad(account, requested, null);
        }
    }

    private synchronized void finishLoad(QQMusicSource account, Map<String, Entry> requested,
                                         Map<String, Boolean> statuses) {
        if (source != account) return;
        reading = false;
        for (var lookup : requested.entrySet()) {
            String mid = lookup.getKey();
            var before = lookup.getValue();
            if (tracks.get(mid) != before) continue;
            Boolean liked = statuses == null ? null : statuses.get(mid);
            State retryState = before.state == State.LOADING ? State.UNAVAILABLE : before.state;
            tracks.put(mid, liked == null ? new Entry(retryState, System.nanoTime() + RETRY_NANOS) : confirmed(liked));
        }
    }

    @Override public synchronized CompletableFuture<Void> set(String mid, boolean liked) {
        checkMid(mid);
        QQMusicSource account = source;
        if (account == null) return CompletableFuture.failedFuture(new OnlineMusicSource.LoginRequired());
        State expected = liked ? State.UNLIKED : State.LIKED;
        if (state(mid) != expected) {
            return CompletableFuture.failedFuture(new IllegalStateException("QQ favorite is not ready to change"));
        }
        pendingReads.remove(mid);
        tracks.put(mid, new Entry(liked ? State.ADDING : State.REMOVING, 0L));
        try {
            return CompletableFuture.runAsync(() -> {
                try {
                    account.like(new OnlineTrack("qq", mid, mid, "", "", "", 0), liked);
                } catch (Exception failure) {
                    throw new CompletionException(failure);
                }
            }, executor).whenComplete((unused, failure) -> finishWrite(account, mid, liked, failure));
        } catch (RejectedExecutionException failure) {
            finishWrite(account, mid, liked, failure);
            return CompletableFuture.failedFuture(failure);
        }
    }

    private synchronized void finishWrite(QQMusicSource account, String mid, boolean liked, Throwable failure) {
        if (source != account) return;
        tracks.put(mid, confirmed(failure == null ? liked : !liked));
        if (failure != null) pendingReads.add(mid);
    }

    private static Entry confirmed(boolean liked) {
        return new Entry(liked ? State.LIKED : State.UNLIKED, System.nanoTime() + REFRESH_NANOS);
    }

    private static void checkMid(String mid) {
        if (mid == null || !mid.matches("[A-Za-z0-9]{1,40}")) throw new IllegalArgumentException("Invalid QQ track");
    }
}
