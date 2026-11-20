// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** UI-thread-owned search; painting may drain results but can never initiate a request. */
final class MusicFrontendSearch {
    private static final int RESULTS_PER_PAGE = 20;
    private static final int LAST_PAGE = 1000;
    enum State { IDLE, LOADING, EMPTY, END, FAILED, UNAVAILABLE }
    private record Query(String source, String text, int page) {}
    private record Job(Query query, MusicFrontendRoutes.Request token, CompletableFuture<List<OnlineTrack>> result,
                       SessionMusicOperations.Handle<List<OnlineTrack>> operation) {}

    private final OnlineMusicPlayer player;
    private final MusicFrontendRoutes routes;
    private final SessionMusicOperations operations;
    private SessionMusicOperations.Handle<List<OnlineTrack>> displayedOperation;
    private String source = "";
    private Query displayed, failed;
    private Job pending;
    private List<OnlineTrack> tracks = List.of();
    private State state = State.IDLE;
    private int first;
    private boolean more;

    MusicFrontendSearch(OnlineMusicPlayer player, MusicFrontendRoutes routes) {
        this.player = player;
        this.routes = routes;
        this.operations = SessionMusicOperations.forPlayer(player).orElse(null);
        updateSources();
    }

    void updateSources() {
        var available = player.sources();
        if (!available.contains(source)) {
            String next = available.isEmpty() ? "" : available.get(0);
            if (!source.equals(next)) {
                cancel();
                source = next;
                clear();
            }
        }
        if (source.isEmpty()) state = State.UNAVAILABLE;
    }

    void cycleSource() {
        var available = player.sources();
        if (available.size() < 2) return;
        cancel();
        source = available.get((available.indexOf(source) + 1) % available.size());
        clear();
        submit();
    }

    String source() { return source; }
    List<OnlineTrack> tracks() {
        return displayedOperation != null && !displayedOperation.displayValid() ? List.of() : tracks;
    }
    int first() { return first; }
    int page() { return displayed == null ? 1 : displayed.page(); }
    State state() { return state; }
    boolean canRetry() { return failed != null && pending == null; }
    boolean hasPrevious() { return pending == null && (first > 0 || displayed != null && displayed.page() > 1); }
    boolean hasNext(int visible) {
        return pending == null && visible > 0 && (first + visible < tracks.size() || more);
    }

    void submit() {
        updateSources();
        String draft = routes.searchDraft().strip();
        if (source.isEmpty() || draft.isEmpty()) return;
        var query = new Query(source, draft, 1);
        if (pending != null && pending.query().equals(query)) return;
        if (displayed != null && (!displayed.text().equals(draft) || displayed.page() != 1)) clear();
        start(query);
    }

    void scrollTo(int index, int visible) {
        first = Math.max(0, Math.min(index, tracks.size() - Math.max(1, visible)));
    }

    void previous(int visible) {
        if (!hasPrevious() || visible < 1) return;
        if (first > 0) first = Math.max(0, first - visible);
        else start(new Query(source, displayed.text(), displayed.page() - 1));
    }

    void next(int visible) {
        if (!hasNext(visible)) return;
        if (first + visible < tracks.size()) scrollTo(first + visible, visible);
        else start(new Query(source, displayed.text(), displayed.page() + 1));
    }

    void retry() {
        if (canRetry()) start(failed);
    }

    void poll() {
        updateSources();
        if (displayedOperation != null && !displayedOperation.displayValid()) clear();
        if (pending == null || !pending.result().isDone()) return;
        var job = pending;
        pending = null;
        if (!routes.complete(job.token()) || !source.equals(job.query().source())
                || job.operation() != null && !job.operation().displayValid()) {
            state = State.IDLE;
            return;
        }
        try {
            var result = List.copyOf(job.result().join());
            if (result.isEmpty() && displayed != null && job.query().page() > displayed.page()) {
                more = false;
                state = State.END;
            } else {
                displayed = job.query();
                displayedOperation = job.operation();
                tracks = result;
                first = 0;
                more = result.size() >= RESULTS_PER_PAGE && displayed.page() < LAST_PAGE;
                state = result.isEmpty() ? State.EMPTY : State.IDLE;
            }
        } catch (RuntimeException unavailable) {
            failed = job.query();
            state = State.FAILED;
        }
    }

    void cancel() { discard(false); }
    void detach() { discard(true); }

    private void discard(boolean keepBackground) {
        if (pending != null) {
            var job = pending;
            pending = null;
            routes.complete(job.token());
            if (job.operation() != null) {
                if (keepBackground) job.operation().detach();
                else job.operation().cancel();
            }
            job.result().cancel(true);
        }
        if (state == State.LOADING) state = State.IDLE;
    }

    private void clear() {
        failed = displayed = null;
        displayedOperation = null;
        tracks = List.of();
        first = 0;
        more = false;
        state = State.IDLE;
    }

    private void start(Query query) {
        if (routes.current().page() != MusicFrontendRoutes.Page.SEARCH) return;
        cancel();
        failed = null;
        var token = routes.request("search");
        try {
            var operation = operations == null ? null : operations.startSearch(query.source(), query.text(), query.page());
            var result = operation == null ? player.search(query.source(), query.text(), query.page()) : operation.observe();
            pending = new Job(query, token, java.util.Objects.requireNonNull(result), operation);
            state = State.LOADING;
        } catch (RuntimeException unavailable) {
            routes.complete(token);
            failed = query;
            state = State.FAILED;
        }
    }
}
