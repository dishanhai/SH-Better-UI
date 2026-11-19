// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class SessionMusicOperationsTest {
    private OnlineMusicPlayer player(AtomicReference<CompletableFuture<List<OnlineTrack>>> next) {
        return (OnlineMusicPlayer)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{OnlineMusicPlayer.class},
                (proxy,method,args)->{if(method.getName().equals("search"))return next.get();throw new AssertionError(method.getName());});
    }
    @Test void detachAndObserverCancellationDoNotCancelOwnerButExplicitCancelDoes() {
        var owner=new CompletableFuture<List<OnlineTrack>>();var next=new AtomicReference<>(owner);
        var operations=new SessionMusicOperations(player(next));var handle=operations.startSearch("qq","private query",1);
        handle.observe().cancel(true);handle.detach();assertFalse(owner.isCancelled());
        owner.complete(List.of());assertEquals(SessionMusicOperations.State.SUCCEEDED,handle.snapshot().state());
        assertTrue(handle.snapshot().background());assertEquals(List.of(),handle.observe().join());
        assertFalse(handle.snapshot().toString().contains("private query"));handle.attach();assertFalse(handle.snapshot().background());
        next.set(new CompletableFuture<>());var cancelled=operations.startSearch("qq","other",1);cancelled.cancel();
        assertTrue(next.get().isCancelled());assertEquals(SessionMusicOperations.State.CANCELLED,cancelled.snapshot().state());
    }
    @Test void unconsumedTerminalResultCanDetachButCancelledOrInvalidResultCannot() {
        var owner=new CompletableFuture<List<OnlineTrack>>();var next=new AtomicReference<>(owner);
        var operations=new SessionMusicOperations(player(next));var ready=operations.startSearch("qq","q",1);
        owner.complete(List.of());ready.detach();assertTrue(ready.snapshot().background());
        ready.attach();assertFalse(ready.snapshot().background());
        next.set(new CompletableFuture<>());var failed=operations.startSearch("qq","q",1);
        next.get().completeExceptionally(new IllegalStateException());failed.detach();assertTrue(failed.snapshot().background());
        next.set(new CompletableFuture<>());var cancelled=operations.startSearch("qq","q",1);
        cancelled.cancel();cancelled.detach();assertFalse(cancelled.snapshot().background());
        operations.invalidate("qq",null);ready.detach();assertFalse(ready.snapshot().background());
    }
    @Test void accountInvalidationAndShutdownRejectEvenCompletedResults() {
        var next=new AtomicReference<>(CompletableFuture.<List<OnlineTrack>>completedFuture(List.of()));
        var operations=new SessionMusicOperations(player(next));var old=operations.startSearch("qq","old",1);
        operations.invalidate("qq",null);assertFalse(old.valid());assertThrows(CancellationException.class,()->old.observe().join());
        assertTrue(operations.snapshots().isEmpty());var current=operations.startSearch("qq","new",1);operations.close();
        assertFalse(current.valid());assertTrue(operations.snapshots().isEmpty());assertThrows(IllegalStateException.class,()->operations.startSearch("qq","later",1));
    }
    @Test void historyIsBoundedExpiresAndCannotEvictRunningJobs() {
        var clock=new AtomicLong(1);var next=new AtomicReference<>(CompletableFuture.<List<OnlineTrack>>completedFuture(List.of()));
        var operations=new SessionMusicOperations(player(next),clock::get);var first=operations.startSearch("qq","first",1);
        for(int i=0;i<40;i++)operations.startSearch("qq","q",1);
        assertEquals(32,operations.snapshots().size());assertFalse(first.valid());
        clock.addAndGet(600_000_000_000L);assertTrue(operations.snapshots().isEmpty());
        for(int i=0;i<32;i++){next.set(new CompletableFuture<>());operations.startSearch("qq","pending",1);}
        assertThrows(IllegalStateException.class,()->operations.startSearch("qq","overflow",1));assertEquals(32,operations.snapshots().size());
    }
    @Test void authenticationFailureSurvivesItsOwnGenerationInvalidationOnly() {
        var owner=new CompletableFuture<List<OnlineTrack>>();var next=new AtomicReference<>(owner);
        var operations=new SessionMusicOperations(player(next));var handle=operations.startSearch("qq","q",1);handle.detach();
        operations.invalidate("qq",owner);owner.completeExceptionally(new OnlineMusicSource.LoginRequired());
        assertEquals(SessionMusicOperations.Error.LOGIN_REQUIRED,handle.snapshot().error());assertTrue(handle.valid());
        operations.invalidate("qq",null);assertFalse(handle.valid());
    }
    @Test void displayAuthorizationOutlivesHistoryRetentionButNotAccountOrSession() {
        var clock=new AtomicLong(1);var next=new AtomicReference<>(CompletableFuture.<List<OnlineTrack>>completedFuture(List.of()));
        var operations=new SessionMusicOperations(player(next),clock::get);
        var expired=operations.startSearch("qq","reading",1);
        clock.addAndGet(600_000_000_000L);assertTrue(operations.snapshots().isEmpty());
        assertFalse(expired.valid());assertTrue(expired.displayValid());
        var evicted=operations.startSearch("qq","reading again",1);
        for(int i=0;i<33;i++)operations.startSearch("qq","later",1);
        assertFalse(evicted.valid());assertTrue(evicted.displayValid());
        operations.invalidate("qq",null);assertFalse(expired.displayValid());assertFalse(evicted.displayValid());
        var current=operations.startSearch("qq","current",1);assertTrue(current.displayValid());
        operations.close();assertFalse(current.displayValid());
    }

    @Test void unconfirmedBackgroundLikeDoesNotRetryOrClaimRejection() {
        var owner=new AtomicReference<>(new CompletableFuture<Void>());
        var writes=new AtomicInteger();
        var player=(OnlineMusicPlayer)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{OnlineMusicPlayer.class}, (proxy,method,args)->{
            if(method.getName().equals("like")) { writes.incrementAndGet();return owner.get(); }
            throw new AssertionError(method.getName());
        });
        var registry=new SessionMusicOperations(player);
        var track=new OnlineTrack("qq","song123","song123","do not disclose","artist","album",1000);
        var first=registry.startLike(track,true);
        first.detach();
        first.cancel();
        assertFalse(owner.get().isCancelled());
        assertThrows(IllegalStateException.class,()->registry.startLike(track,false));
        owner.get().completeExceptionally(new java.net.SocketTimeoutException("private transport detail"));
        assertEquals(SessionMusicOperations.Error.UNKNOWN,first.snapshot().error());
        var notices=new MusicOperationNotices();
        var result=notices.observe(registry.snapshots(),true).stream().filter(update->!update.cancel()).toList();
        assertEquals(1,result.size());
        assertEquals("operation_like_unknown",result.get(0).key());
        assertFalse(result.get(0).error());
        assertTrue(notices.observe(registry.snapshots(),true).isEmpty());
        assertFalse(first.snapshot().toString().contains("song123"));
        assertEquals(1,writes.get());
        owner.set(new CompletableFuture<>());
        var next=registry.startLike(track,false);
        owner.get().completeExceptionally(new MusicWriteRejected("private rejection"));
        assertEquals(SessionMusicOperations.Error.REJECTED,next.snapshot().error());
        registry.invalidate("qq",null);
        assertFalse(next.displayValid());
        assertTrue(registry.snapshots().isEmpty());
    }
}
