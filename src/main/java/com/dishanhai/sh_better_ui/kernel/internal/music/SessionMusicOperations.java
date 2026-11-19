// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Bounded session-owned read jobs. Notification snapshots never contain queries or account data. */
public final class SessionMusicOperations implements AutoCloseable {
    public enum Kind { SEARCH, PLAYLISTS, PLAYLIST, LIKED }
    public enum State { RUNNING, SUCCEEDED, FAILED, CANCELLED, SUPERSEDED }
    public enum Error { NONE, LOGIN_REQUIRED, UNAVAILABLE }
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
    private <T> Handle<T> start(Request request,Supplier<CompletableFuture<T>> action) {
        // Lock order is always player -> registry; credentials invalidation uses the same order.
        synchronized(player) {
            Handle<T> handle;
            synchronized(this) {
                if(closed) throw new IllegalStateException("Music session closed");
                prune();
                while(entries.size()>=LIMIT) { var old=entries.values().stream().filter(h->h.state!=State.RUNNING).findFirst(); if(old.isEmpty())break;entries.remove(old.get().id); }
                if(entries.size()>=LIMIT) throw new IllegalStateException("Too many music operations");
                handle=new Handle<>(IDS.incrementAndGet(),request);entries.put(handle.id,handle);
            }
            CompletableFuture<T> owner;
            try { owner=action.get(); }
            catch(RuntimeException error) { owner=CompletableFuture.failedFuture(error); }
            synchronized(this) { handle.owner=owner; }
            owner.whenComplete((value,error)->complete(handle,value,error));
            return handle;
        }
    }
    private synchronized <T> void complete(Handle<T> handle,T value,Throwable error) {
        if(closed||handle.state!=State.RUNNING)return;
        handle.completed=clock.getAsLong();
        if(error==null) { handle.state=State.SUCCEEDED; }
        else if(error instanceof CancellationException)handle.state=State.CANCELLED;
        else { handle.state=State.FAILED;while(error instanceof CompletionException && error.getCause()!=null)error=error.getCause();
            handle.error=error instanceof OnlineMusicSource.LoginRequired?Error.LOGIN_REQUIRED:Error.UNAVAILABLE; }
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
        private State state=State.RUNNING;private Error error=Error.NONE;private boolean background;
        private long completed, accountEpoch;
        private Handle(long id,Request request){this.id=id;this.request=request;this.accountEpoch=accountEpochs.getOrDefault(request.source(),0L); }
        public long id(){return id;}
        public boolean valid(){synchronized(SessionMusicOperations.this){prune();return displayValid() && entries.get(id)==this;}}
        /** Already-rendered data remains authorized after history retention expires, but never across account changes. */
        public boolean displayValid(){synchronized(SessionMusicOperations.this){return !closed && state!=State.SUPERSEDED && accountEpoch==accountEpochs.getOrDefault(request.source(),0L);}}
        public Request request(){return request;}
        public Snapshot snapshot(){synchronized(SessionMusicOperations.this){return new Snapshot(id,request.kind(),request.source(),state,background,error);}}
        /** Independent observer: cancelling this future never cancels session-owned work. */
        public CompletableFuture<T> observe(){synchronized(SessionMusicOperations.this){
            if(!valid())return CompletableFuture.failedFuture(new CancellationException());
            return owner.copy();
        }}
        /** Called only while the view still has an unconsumed observer. Completion may race page removal. */
        public void detach(){synchronized(SessionMusicOperations.this){
            if(valid() && (state==State.RUNNING || state==State.SUCCEEDED || state==State.FAILED))background=true;
        }}
        public void attach(){synchronized(SessionMusicOperations.this){background=false;}}
        public void cancel(){CompletableFuture<T> future;synchronized(SessionMusicOperations.this){
            if(state!=State.RUNNING)return;state=State.CANCELLED;completed=clock.getAsLong();future=owner;
        }if(future!=null)future.cancel(true);}
    }
}
