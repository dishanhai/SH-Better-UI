// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer.State;
import static com.dishanhai.sh_better_ui.kernel.internal.music.PlaybackOutput.CrossfadeStatus;
import static org.junit.jupiter.api.Assertions.*;

class OnlineCrossfadeSessionTest {
    private static final long DURATION = 15000;
    private static final OnlineTrack A = track("a"), B = track("b"), C = track("c");
    private static OnlineTrack track(String id) { return new OnlineTrack("test",id,id,id,"Artist","",DURATION); }
    private static class Source implements OnlineMusicSource {
        public String id() { return "test"; }
        public List<OnlineTrack> search(String query,int page,int limit) { return List.of(); }
        public Stream resolve(OnlineTrack track) throws Exception {
            return new Stream(URI.create("https://example.org/"+track.id()),0,DURATION,false);
        }
        public LyricTimeline lyrics(OnlineTrack track) { return LyricTimeline.empty(); }
    }
    private static class Output implements PlaybackOutput {
        volatile State state=State.LOADING;
        volatile long position, token, cancelled;
        volatile int preparations, adopts, starts;
        volatile boolean closed;
        volatile CrossfadeStatus phase=CrossfadeStatus.NONE;
        public void start(byte[] bytes,long position,boolean playing) {
            ++starts; this.position=position; state=playing?State.PLAYING:State.PAUSED;
        }
        public void pause() { state=State.PAUSED; }
        public void resume() { state=State.PLAYING; }
        public State state() { return state; }
        public long positionMs() { return position; }
        public void close() { closed=true; }
        public boolean supportsCrossfade() { return true; }
        public synchronized boolean queueCrossfade(long id,byte[] bytes,int seconds,long duration) {
            if (closed || id<=cancelled) return false;
            assertEquals(3,seconds); assertEquals(DURATION,duration);
            token=id; ++preparations; phase=CrossfadeStatus.READY; return true;
        }
        public synchronized CrossfadeStatus crossfadeStatus(long id) {
            return id==token?phase:CrossfadeStatus.NONE;
        }
        public synchronized boolean cancelCrossfade(long id) {
            if(id==token&&(phase==CrossfadeStatus.MIXING||phase==CrossfadeStatus.COMPLETE))return false;
            cancelled=Math.max(cancelled,id); if(id==token)phase=CrossfadeStatus.NONE; return true;
        }
        public synchronized void acceptCrossfade(long id) {
            assertEquals(token,id);assertEquals(CrossfadeStatus.COMPLETE,phase);
            ++adopts;position=3040;phase=CrossfadeStatus.NONE;
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!condition.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(5);
        assertTrue(condition.getAsBoolean(),"asynchronous state did not settle");
    }

    @Test void sameOutputIsPromotedOnlyWhenConsumedBoundaryCompletes() throws Exception {
        var out=new Output(); var outputs=new AtomicInteger();
        try(var session=new OnlinePlayerSession(List.of(new Source()),()->{outputs.incrementAndGet();return out;},uri->new byte[]{1})){
            assertEquals(0,session.crossfadeSeconds());
            assertThrows(IllegalArgumentException.class,()->session.crossfadeSeconds(13));
            session.crossfadeSeconds(3);
            session.queue().replace(List.of(A,B,C),0);
            await(()->session.snapshot().state()==State.PLAYING);
            out.position=7000;
            await(()->out.phase==CrossfadeStatus.READY);
            assertEquals(0,session.queue().snapshot().index());assertEquals(A,session.snapshot().track());
            out.phase=CrossfadeStatus.MIXING;
            assertThrows(IllegalStateException.class,()->session.queue().move(1,2));
            assertEquals(List.of(A,B,C),session.queue().snapshot().tracks());
            session.crossfadeSeconds(0); // Don't truncate an already-written overlap.
            session.pause();assertEquals(State.PAUSED,out.state);
            session.resume();
            out.phase=CrossfadeStatus.COMPLETE;
            await(()->B.equals(session.snapshot().track()));
            assertEquals(3040,session.snapshot().positionMs());assertEquals(1,session.queue().snapshot().index());
            assertEquals(1,outputs.get());assertEquals(1,out.starts);assertEquals(1,out.adopts);assertFalse(out.closed);
        }
    }

    @Test void rejectedSuccessorReusesDownloadedBytesOnlyAfterResolvingAgain() throws Exception {
        assertDryHandoff("same", 1, State.PLAYING);
    }

    @Test void rejectedSuccessorDoesNotReuseBytesForChangedGrant() throws Exception {
        assertDryHandoff("changed", 2, State.PLAYING);
    }

    @Test void rejectedSuccessorDoesNotBypassRevokedGrant() throws Exception {
        assertDryHandoff("revoked", 1, State.FAILED);
    }

    @Test void explicitCacheClearDropsPreparedBytesFromNormalHandoff() throws Exception {
        assertDryHandoff("cleared", 2, State.PLAYING);
    }

    private static void assertDryHandoff(String scenario, int expectedDownloads, State finalState) throws Exception {
        var out = new Output(); var next = new Output(); var outputs = new AtomicInteger();
        var grants = new AtomicInteger(); var downloads = new AtomicInteger();
        var source = new Source() {
            @Override public Stream resolve(OnlineTrack track) throws Exception {
                if (B.equals(track) && grants.incrementAndGet() == 2) {
                    if ("revoked".equals(scenario)) return null;
                    if ("changed".equals(scenario)) {
                        return new Stream(URI.create("https://example.org/b"), 4000, 6000, true);
                    }
                }
                return super.resolve(track);
            }
        };
        try (var session = new OnlinePlayerSession(List.of(source), () -> outputs.getAndIncrement() == 0 ? out : next,
                uri -> { if (uri.getPath().equals("/b")) downloads.incrementAndGet(); return new byte[]{1}; })) {
            session.crossfadeSeconds(3); session.queue().replace(List.of(A,B),0);
            await(() -> session.snapshot().state() == State.PLAYING);
            out.position = 7000;
            await(() -> out.phase == CrossfadeStatus.READY);
            assertEquals(1, downloads.get());
            if ("cleared".equals(scenario)) session.clearAudioCache();
            out.phase = CrossfadeStatus.REJECTED;
            await(() -> out.phase == CrossfadeStatus.NONE);
            assertEquals(A, session.snapshot().track()); assertEquals(1, outputs.get());
            out.state = State.ENDED;
            await(() -> B.equals(session.snapshot().track()) && session.snapshot().state() == finalState);
            assertEquals(2, grants.get());
            assertEquals(expectedDownloads, downloads.get());
            assertEquals(finalState == State.FAILED ? 1 : 2, outputs.get());
            assertEquals(0, out.adopts);
        }
    }

    @Test void seekCancelsPreparedSuccessorAndContinuesSameQueueTrack() throws Exception {
        var out=new Output();var next=new Output();var outputs=new AtomicInteger();
        try(var session=new OnlinePlayerSession(List.of(new Source()),()->outputs.getAndIncrement()==0?out:next,uri->new byte[]{1})){
            session.crossfadeSeconds(3);session.queue().replace(List.of(A,B),0);
            await(()->session.snapshot().state()==State.PLAYING);
            out.position=7000;
            await(()->out.phase==CrossfadeStatus.READY);
            session.seek(1000);
            assertTrue(out.closed);assertEquals(CrossfadeStatus.NONE,out.phase);
            assertEquals(A,session.snapshot().track());assertEquals(1000,session.snapshot().positionMs());
            assertEquals(0,session.queue().snapshot().index());
        }
    }

    @Test void obsoleteDownloadCannotInstallAnIncomingTrackAfterQueueChanges() throws Exception {
        var requested=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var out=new Output();
        try(var session=new OnlinePlayerSession(List.of(new Source()),()->out,uri->{
            if(uri.getPath().equals("/b")){
                requested.countDown();
                // A provider may finish even after the HTTP request was cancelled.
                for(;;)try {if(release.await(3,TimeUnit.SECONDS))break;else throw new IllegalStateException("Timeout");}
                catch(InterruptedException ignored){ }
                finally {if(release.getCount()==0)finished.countDown();}
            }
            return new byte[]{1};
        })){
            session.crossfadeSeconds(3);session.queue().replace(List.of(A,B,C),0);
            await(()->session.snapshot().state()==State.PLAYING);
            out.position=7000;assertTrue(requested.await(3,TimeUnit.SECONDS));
            session.crossfadeSeconds(0);
            session.queue().remove(1);
            release.countDown();
            assertTrue(finished.await(3,TimeUnit.SECONDS));
            assertEquals(List.of(A,C),session.queue().snapshot().tracks());
            assertEquals(A,session.snapshot().track());assertEquals(0,out.preparations);
            assertTrue(out.cancelled>0);
        } finally {release.countDown();}
    }

    @Test void providerChangeKeepsTheNormalHandoffWithoutMixingEffects() throws Exception {
        var a=new Output();var b=new Output();var outputs=new AtomicInteger();var grants=new AtomicInteger();
        var other=new Source() {
            @Override public String id() { return "other"; }
            @Override public Stream resolve(OnlineTrack track) throws Exception {
                grants.incrementAndGet();return super.resolve(track);
            }
        };
        var successor=new OnlineTrack("other","b","b","Other","Artist","",DURATION);
        try(var session=new OnlinePlayerSession(List.of(new Source(),other),()->outputs.getAndIncrement()==0?a:b,
                uri->new byte[]{1})){
            session.crossfadeSeconds(3);session.queue().replace(List.of(A,successor),0);
            await(()->session.snapshot().state()==State.PLAYING);
            a.position=7000;
            // Drive the actual poll so we can assert the crossfade admission rule without a sleep.
            var watch=OnlinePlayerSession.class.getDeclaredField("queueWatch");watch.setAccessible(true);
            var advance=OnlinePlayerSession.class.getDeclaredMethod("advanceQueue",long.class,long.class);advance.setAccessible(true);
            synchronized(session){advance.invoke(session,session.playbackGeneration(),watch.getLong(session));}
            assertEquals(0,grants.get());assertEquals(0,a.preparations);
            a.state=State.ENDED;
            await(()->successor.equals(session.snapshot().track())&&session.snapshot().state()==State.PLAYING);
            assertEquals(1,grants.get());assertEquals(2,outputs.get());
        }
    }
}
