// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Bounded session-owned read jobs. Notification snapshots never contain queries or account data. */
public final class SessionMusicOperations implements AutoCloseable {
    public enum Kind { SEARCH, PLAYLISTS, PLAYLIST, LIKED, LIKE, UNLIKE, LOGIN; public boolean mutation() { return this == LIKE || this == UNLIKE; } }
    public enum State { RUNNING, SUCCEEDED, FAILED, CANCELLED, SUPERSEDED }
    public enum Error { BUSY, EXPIRED, NONE, LOGIN_REQUIRED, UNAVAILABLE, UNKNOWN, REJECTED }
    public record Snapshot(long id, Kind kind, String source, State state, boolean background, Error error) {}
    /** Private-to-the-music-view restoration metadata, never a notice payload. */
    public record Request(Kind kind, String source, String query, int page, OnlinePlaylist playlist) {}
    private final OnlineMusicPlayer player;
    private final LongSupplier clock;
    private final LinkedHashMap<Long, Handle<?>> entries = new LinkedHashMap<>();
    private final Map<String,Long> accountEpochs = new HashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong IDS = new java.util.concurrent.atomic.AtomicLong();
    private boolean closed;
    private static final int LIMIT=32;
    private static final long TTL=600_000_000_000L;
    SessionMusicOperations(OnlineMusicPlayer player) { this(player,System::nanoTime); }
    SessionMusicOperations(OnlineMusicPlayer player, LongSupplier clock) { this.player=player;this.clock=clock; }
    public static Optional<SessionMusicOperations> forPlayer(OnlineMusicPlayer player) {
        return player instanceof OnlinePlayerSession session ? Optional.of(session.operations()) : Optional.empty();
    }
    public Handle<List<OnlineTrack>> startSearch(String source,String query,int page) {
        return start(new Request(Kind.SEARCH,source,query,page,null),()->player.search(source,query,page));
    }
    public Handle<List<OnlinePlaylist>> startPlaylists(String source,int page) {
        return start(new Request(Kind.PLAYLISTS,source,"",page,null),()->player.playlists(source,page));
    }
    public Handle<List<OnlineTrack>> startPlaylist(OnlinePlaylist playlist,int page) {
        return start(new Request(Kind.PLAYLIST,playlist.source(),"",page,playlist),()->player.playlist(playlist,page));
    }
    public Handle<List<OnlineTrack>> startLiked(String source,int page) {
        return start(new Request(Kind.LIKED,source,"",page,null),()->player.liked(source,page));
    }
    public OnlineMusicLogin startLogin(String source) {
        OnlineMusicLogin[] attempt = new OnlineMusicLogin[1];
        start(new Request(Kind.LOGIN, source, "", 0, null), () -> {
            attempt[0] = player.login(source);
            return ((QrLoginSession)attempt[0]).completion();
        });
        if (attempt[0] == null) throw new IllegalStateException("Could not start music login");
        return attempt[0];
    }
    public Optional<OnlineMusicLogin> currentLogin(String source) {
        return player instanceof OnlinePlayerSession session ? session.currentLogin(source) : Optional.empty();
    }
    public void setLoginBackground(String source, OnlineMusicLogin attempt, boolean background) {
        if (!(attempt instanceof QrLoginSession qr)) return;
        synchronized (player) {
            if (currentLogin(source).orElse(null) != attempt) return;
            synchronized (this) {
                latest(Kind.LOGIN, source).filter(handle -> handle.owner == qr.completion()).ifPresent(handle -> {
                    if (background) handle.detach();
                    else if (handle.state == State.RUNNING) handle.attach();
                });
            }
        }
    }
    public Handle<Void> startLike(OnlineTrack track, boolean liked) {
        return start(new Request(liked ? Kind.LIKE : Kind.UNLIKE, track.source(), track.id(), 0, null),
                () -> player.like(track, liked));
    }
    /** Revoke only the account context the foreground logout action was bound to. */
    public boolean signOutIfCurrent(String source, long version, OnlineMusicLogin expectedAttempt) {
        return player instanceof OnlinePlayerSession session && session.signOutIfCurrent(source, version, expectedAttempt);
    }
    public synchronized long accountVersion(String source) { return accountEpochs.getOrDefault(source,0L); }
    public long likeLaneRevision() {
        return player instanceof OnlinePlayerSession session ? session.likeLaneRevision() : 0;
    }
    public boolean likePending(OnlineTrack track) {
        if (player instanceof OnlinePlayerSession session && session.likeInFlight(track)) return true;
        synchronized(this) { return entries.values().stream().anyMatch(h -> h.state == State.RUNNING && h.request.kind().mutation()
                && h.request.source().equals(track.source()) && h.request.query().equals(track.id())); }
    }
    private <T> Handle<T> start(Request request,Supplier<CompletableFuture<T>> action) {
        // Lock order is always player -> registry; credentials invalidation uses the same order.
        synchronized(player) {
            Handle<T> handle;
            synchronized(this) {
                if(closed) throw new IllegalStateException("Music session closed");
                prune();
                if (request.kind().mutation() && entries.values().stream().anyMatch(h -> h.state == State.RUNNING
                        && h.request.kind().mutation() && h.request.source().equals(request.source())
                        && h.request.query().equals(request.query()))) throw new IllegalStateException("Music update in progress");
                while(entries.size()>=LIMIT) { var old=entries.values().stream().filter(h->h.state!=State.RUNNING).findFirst(); if(old.isEmpty())break;entries.remove(old.get().id); }
                if(entries.size()>=LIMIT) throw new IllegalStateException("Too many music operations");
                handle=new Handle<>(IDS.incrementAndGet(),request);entries.put(handle.id,handle);
            }
            CompletableFuture<T> owner;
            try { owner=action.get(); }
            catch(RuntimeException error) { owner=CompletableFuture.failedFuture(error); }
            synchronized(this) { handle.owner=owner; }
            owner.whenComplete((value,error)-> {
                complete(handle,value,error);
                // Observers can update the view immediately. Publish only after the registry
                // has recorded the terminal state and the history-retention timestamp.
                if (error == null) handle.settled.complete(value);
                else handle.settled.completeExceptionally(error);
            });
            return handle;
        }
    }
    private synchronized <T> void complete(Handle<T> handle,T value,Throwable error) {
        if(closed||handle.state!=State.RUNNING)return;
        handle.completed=clock.getAsLong();
        if(error==null) { handle.state=State.SUCCEEDED; }
        else if(error instanceof CancellationException)handle.state=State.CANCELLED;
        else { handle.state=State.FAILED; handle.error=classify(handle.request.kind(),error); }
    }
    public static Error classify(Kind kind,Throwable error) {
        while(error instanceof CompletionException && error.getCause()!=null)error=error.getCause();
        if(error instanceof OnlinePlayerSession.LikeBusy)return Error.BUSY;
        if(error instanceof QrLoginSession.Expired)return Error.EXPIRED;
        if(error instanceof OnlineMusicSource.LoginRequired)return Error.LOGIN_REQUIRED;
        if(!kind.mutation() || error instanceof QQFavorites.NotSent)return Error.UNAVAILABLE;
        return error instanceof MusicWriteRejected || error instanceof IllegalArgumentException || error instanceof UnsupportedOperationException
                ? Error.REJECTED : Error.UNKNOWN;
    }
    private void prune() {
        long now=clock.getAsLong();
        entries.values().removeIf(h->h.state!=State.RUNNING && now-h.completed>=TTL);

    }
    public synchronized List<Snapshot> snapshots() { if(closed)return List.of();prune();return entries.values().stream().map(Handle::snapshot).toList(); }
    public synchronized Optional<Handle<?>> find(long id) { if(closed)return Optional.empty();prune();return Optional.ofNullable(entries.get(id)); }
    public synchronized Optional<Handle<?>> latest(Kind kind,String source) {
        if(closed)return Optional.empty();prune();Handle<?> latest=null;
        for(var h:entries.values())if(h.request.kind()==kind&&h.request.source().equals(source)&&h.state!=State.SUPERSEDED&&h.state!=State.CANCELLED)latest=h;
        return Optional.ofNullable(latest);
    }
    /** Called under player lock before old account requests are cancelled. Drop all restoration data. */
    synchronized void invalidate(String source, CompletableFuture<?> failedRead) {
        long epoch=accountEpochs.merge(source,1L,Long::sum);
        for(var h:entries.values())if(h.request.source().equals(source) && h.owner==failedRead && failedRead!=null)h.accountEpoch=epoch;
        for(var h:entries.values())if(h.request.source().equals(source) && h.owner!=failedRead){h.state=State.SUPERSEDED;}
        entries.values().removeIf(h->h.request.source().equals(source) && h.owner!=failedRead);
    }
    @Override public synchronized void close() { closed=true;for(var h:entries.values()){h.state=State.SUPERSEDED;}entries.clear(); }
    public final class Handle<T> {
        private final long id; private final Request request;
        private CompletableFuture<T> owner;
        private final CompletableFuture<T> settled = new CompletableFuture<>();
        private State state=State.RUNNING;private Error error=Error.NONE;private boolean background;
        private long completed, accountEpoch;
        private Handle(long id,Request request){this.id=id;this.request=request;this.accountEpoch=accountEpochs.getOrDefault(request.source(),0L); }
        public long id(){return id;}
        public long accountVersion() { synchronized (SessionMusicOperations.this) { return accountEpoch; } }
        public boolean valid(){synchronized(SessionMusicOperations.this){prune();return displayValid() && entries.get(id)==this;}}
        /** Already-rendered data remains authorized after history retention expires, but never across account changes. */
        public boolean displayValid(){synchronized(SessionMusicOperations.this){return !closed && state!=State.SUPERSEDED && accountEpoch==accountEpochs.getOrDefault(request.source(),0L);}}
        public Request request(){return request;}
        public Snapshot snapshot(){synchronized(SessionMusicOperations.this){return new Snapshot(id,request.kind(),request.source(),state,background,error);}}
        /** Independent observer: cancelling this future never cancels session-owned work. */
        public CompletableFuture<T> observe(){synchronized(SessionMusicOperations.this){
            if(!valid())return CompletableFuture.failedFuture(new CancellationException());
            return settled.copy();
        }}
        /** Called only while the view still has an unconsumed observer. Completion may race page removal. */
        public void detach(){synchronized(SessionMusicOperations.this){
            if(valid() && (state==State.RUNNING || state==State.SUCCEEDED || state==State.FAILED))background=true;
        }}
        public void attach(){synchronized(SessionMusicOperations.this){background=false;}}
        public void cancel(){CompletableFuture<T> future;synchronized(SessionMusicOperations.this){
            if(state!=State.RUNNING || request.kind().mutation() || request.kind()==Kind.LOGIN)return;state=State.CANCELLED;completed=clock.getAsLong();future=owner;
        }if(future!=null)future.cancel(true);}
    }
}
