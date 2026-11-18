// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer.State;

class OnlineAudioCacheTest {
    static final OnlineTrack A = new OnlineTrack("test","a","a","A","Artist","",1000);
    static class Source implements OnlineMusicSource {
        final AtomicInteger resolves = new AtomicInteger();
        volatile boolean granted = true, preview;
        public String id() { return "test"; }
        public void credentials(String value) {}
        public List<OnlineTrack> search(String query,int page,int limit) { return List.of(); }
        public Stream resolve(OnlineTrack track) { resolves.incrementAndGet(); return granted
            ? new Stream(URI.create("https://example.org/a"),0,preview?300:1000,preview) : null; }
        public LyricTimeline lyrics(OnlineTrack track) { return LyricTimeline.empty(); }
    }
    static class Output implements PlaybackOutput {
        volatile State state;
        public void start(byte[] bytes,long position,boolean playing) { state=playing?State.PLAYING:State.PAUSED; }
        public void pause() { state=State.PAUSED; }
        public void resume() { state=State.PLAYING; }
        public State state() { return state; }
        public long positionMs() { return 0; }
        public void close() {}
    }
    static void await(BooleanSupplier condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!condition.getAsBoolean() && System.nanoTime()<until) Thread.sleep(5);
        assertTrue(condition.getAsBoolean());
    }
    static void play(OnlinePlayerSession session) throws Exception {
        session.play(A); await(()->session.snapshot().state()==State.PLAYING);
    }
    @Test void repeatReusesAudioButAlwaysChecksCurrentProviderGrant() throws Exception {
        var source=new Source();var downloads=new AtomicInteger();
        try(var session=new OnlinePlayerSession(List.of(source),Output::new,uri->{downloads.incrementAndGet();return new byte[8];})) {
            play(session);session.stop();play(session);
            assertEquals(2,source.resolves.get());assertEquals(1,downloads.get());
            assertEquals(8,session.audioCacheInfo().retainedBytes());
            source.granted=false;session.play(A);await(()->session.snapshot().state()==State.FAILED);
            assertEquals(OnlineMusicPlayer.Failure.UNAVAILABLE,session.snapshot().failure());
            assertEquals(1,downloads.get());
        }
    }
    @Test void newPreviewGrantDoesNotReuseFullSongBytes() throws Exception {
        var source=new Source();var downloads=new AtomicInteger();
        try(var session=new OnlinePlayerSession(List.of(source),Output::new,uri->{downloads.incrementAndGet();return new byte[8];})) {
            play(session);source.preview=true;play(session);
            assertEquals(2,downloads.get());assertTrue(session.snapshot().preview());assertEquals(300,session.snapshot().durationMs());
        }
    }
    @Test void clearingAndReplacingCredentialsEvictWithoutLeakingAcrossAccounts() throws Exception {
        var source=new Source();var downloads=new AtomicInteger();
        var session=new OnlinePlayerSession(List.of(source),Output::new,uri->{downloads.incrementAndGet();return new byte[8];});
        try {
            play(session);session.clearAudioCache();assertEquals(State.PLAYING,session.snapshot().state());
            assertEquals(0,session.audioCacheInfo().entries());play(session);assertEquals(2,downloads.get());
            session.credentials("test","new-account");assertEquals(0,session.audioCacheInfo().entries());
            play(session);assertEquals(3,downloads.get());
        } finally {session.close();}
        assertEquals(0,session.audioCacheInfo().entries());
    }
    @Test void clearDuringDownloadDoesNotAllowLateCacheRepopulation() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var session=new OnlinePlayerSession(List.of(new Source()),Output::new,uri->{entered.countDown();release.await();return new byte[8];})) {
            session.play(A);assertTrue(entered.await(2,TimeUnit.SECONDS));session.clearAudioCache();release.countDown();
            await(()->session.snapshot().state()==State.PLAYING);assertEquals(0,session.audioCacheInfo().entries());
        } finally {release.countDown();}
    }
    @Test void cancelledDownloadCannotPopulateCacheAfterStop() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var returned=new CountDownLatch(1);
        try(var session=new OnlinePlayerSession(List.of(new Source()),Output::new,uri->{
            entered.countDown();while(release.getCount()>0) try{release.await();}catch(InterruptedException ignored){}
            returned.countDown();return new byte[8];})) {
            session.play(A);assertTrue(entered.await(2,TimeUnit.SECONDS));session.stop();release.countDown();
            assertTrue(returned.await(2,TimeUnit.SECONDS));Thread.sleep(50);
            assertEquals(State.IDLE,session.snapshot().state());assertEquals(0,session.audioCacheInfo().entries());
        } finally {release.countDown();}
    }
    @Test void failedDecoderBytesAreEvictedBeforeRetry() throws Exception {
        var downloads=new AtomicInteger();var outputs=new CopyOnWriteArrayList<Output>();
        try(var session=new OnlinePlayerSession(List.of(new Source()),()->{var out=new Output();outputs.add(out);return out;},uri->{downloads.incrementAndGet();return new byte[8];})) {
            play(session);outputs.get(0).state=State.FAILED;play(session);
            assertEquals(2,downloads.get());
        }
    }
    @Test void networkFailuresAreNotCached() throws Exception {
        var downloads=new AtomicInteger();
        try(var session=new OnlinePlayerSession(List.of(new Source()),Output::new,uri->{
            if(downloads.incrementAndGet()==1)throw new java.io.IOException("fixture");return new byte[8];})) {
            session.play(A);await(()->session.snapshot().state()==State.FAILED);assertEquals(0,session.audioCacheInfo().entries());
            play(session);assertEquals(2,downloads.get());
        }
    }
    @Test void backgroundRepeatRevalidatesGrantAndReusesDownloadedAudio() throws Exception {
        var source=new Source();var downloads=new AtomicInteger();var outputs=new CopyOnWriteArrayList<Output>();
        try(var session=new OnlinePlayerSession(List.of(source),()->{var out=new Output();outputs.add(out);return out;},uri->{downloads.incrementAndGet();return new byte[8];})) {
            session.queue().mode(OnlineMusicQueue.Mode.REPEAT_ONE);session.queue().replace(List.of(A),0);
            await(()->session.snapshot().state()==State.PLAYING);outputs.get(0).state=State.ENDED;
            await(()->outputs.size()==2 && outputs.get(1).state==State.PLAYING);
            assertEquals(2,source.resolves.get());assertEquals(1,downloads.get());
        }
    }
}
