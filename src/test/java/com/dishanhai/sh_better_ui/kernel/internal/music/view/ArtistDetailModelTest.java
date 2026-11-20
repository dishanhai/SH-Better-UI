package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
class ArtistDetailModelTest {
    static final OnlineArtist ARTIST=new OnlineArtist("test","1","Artist");
    static final OnlineTrack A=new OnlineTrack("test","a","a","A","Artist","",1000),B=new OnlineTrack("test","b","b","B","Artist","",1000);
    static final class Rig {
        final List<CompletableFuture<OnlineArtistTracks>> requests=new ArrayList<>();
        List<OnlineTrack> queued=List.of();int selected=-1;boolean supported=true;
        long epoch;
        List<String> sources=List.of("test");
        final List<Integer> pages=new ArrayList<>();
        OnlineMusicQueue.Mode mode=OnlineMusicQueue.Mode.SHUFFLE;
        final OnlineMusicQueue queue=(OnlineMusicQueue)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{OnlineMusicQueue.class},(o,m,a)->{
            switch(m.getName()){
                case "supportsModeReplacement":return true;
                case "mode":if(a!=null&&a.length==1){mode=(OnlineMusicQueue.Mode)a[0];return null;}return mode;
                case "replace":assertEquals(OnlineMusicQueue.Mode.SEQUENTIAL,a[2]);mode=(OnlineMusicQueue.Mode)a[2];queued=List.copyOf((List<OnlineTrack>)a[0]);selected=(Integer)a[1];return null;
                default:throw new AssertionError(m.getName());
            }
        });
        final OnlineMusicPlayer player=(OnlineMusicPlayer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{OnlineMusicPlayer.class},(o,m,a)->{
            switch(m.getName()){
                case "supportsArtistTracks":return supported;
                case "sources":return sources;
                case "artistTracks":var f=new CompletableFuture<OnlineArtistTracks>();requests.add(f);pages.add((Integer)a[1]);return f;
                case "queue":return queue;
                default:throw new AssertionError(m.getName());
            }
        });
        final ArtistDetailModel model=new ArtistDetailModel(player, source -> epoch);
    }
    @Test void selectedSongPreservesDisplayedOrderDuplicatesAndForcesSequential(){
        var r=new Rig();r.model.open(ARTIST);r.requests.get(0).complete(new OnlineArtistTracks(List.of(A,B,A),true));r.model.poll();
        r.model.play(2);assertEquals(List.of(A,B,A),r.queued);assertEquals(2,r.selected);assertEquals(1,r.requests.size());
        r.model.play(0);assertEquals(0,r.selected);assertEquals(1,r.requests.size());
    }
    @Test void backCancelsAndLateCompletionCannotReplaceAnotherArtist(){
        var r=new Rig();r.model.open(ARTIST);var old=r.requests.get(0);r.model.cancel();assertTrue(old.isCancelled());
        r.model.open(new OnlineArtist("test","2","Other"));old.complete(new OnlineArtistTracks(List.of(A),false));r.model.poll();assertTrue(r.model.tracks.isEmpty());
        r.requests.get(1).complete(new OnlineArtistTracks(List.of(B),false));r.model.poll();assertEquals(List.of(B),r.model.tracks);
    }
    @Test void emptyFilteredPageCanExplicitlyContinueButNeverAutomaticallyFetches(){
        var r=new Rig();r.model.open(ARTIST);r.requests.get(0).complete(new OnlineArtistTracks(List.of(),true));r.model.poll();
        assertEquals("empty",r.model.status);assertTrue(r.model.more);r.model.play(0);assertEquals(-1,r.selected);assertEquals(1,r.requests.size());
        r.model.load(2);assertEquals(2,r.requests.size());assertEquals(2,r.model.page);
    }
    @Test void extensionFetchesTheNextProviderPageAndAppendsWithoutDeduplicating(){
        var r=new Rig();r.model.open(ARTIST);
        r.requests.get(0).complete(new OnlineArtistTracks(List.of(A,B),true));r.model.poll();
        var oldDisplay=r.model.tracks;
        r.model.extend();r.model.extend();
        assertEquals(List.of(1,2),r.pages);
        r.requests.get(1).complete(new OnlineArtistTracks(List.of(A),false));r.model.poll();
        assertEquals(List.of(A,B,A),r.model.tracks);
        assertFalse(r.model.play(oldDisplay,0));
        assertTrue(r.model.play(r.model.tracks,2));
        assertEquals(2,r.selected);assertEquals(List.of(A,B,A),r.queued);
    }
    @Test void staleAccountAndRemovedSourceInvalidateDisplayedRowsAndPendingResults(){
        var r=new Rig();r.model.open(ARTIST);
        r.requests.get(0).complete(new OnlineArtistTracks(List.of(A),true));r.model.poll();
        var shown=r.model.tracks;
        r.model.extend();
        r.epoch++;
        assertFalse(r.model.play(shown,0));
        assertTrue(r.requests.get(1).isCancelled());
        r.model.poll();assertTrue(r.model.tracks.isEmpty());assertEquals(-1,r.selected);
        r.model.open(ARTIST);r.sources=List.of("other");
        r.requests.get(2).complete(new OnlineArtistTracks(List.of(B),false));r.model.poll();
        assertEquals("unavailable",r.model.status);assertTrue(r.model.tracks.isEmpty());
    }
    @Test void responseFromDifferentProviderCannotBecomePlayable(){
        var r=new Rig();r.model.open(ARTIST);
        r.requests.get(0).complete(new OnlineArtistTracks(List.of(new OnlineTrack("other","id","id","Wrong","","",1000)),false));
        r.model.poll();assertEquals("failed",r.model.status);assertFalse(r.model.play(0));
    }
    @Test void failureRetryAndUnsupportedNeverQueueOrAutoretry(){
        var r=new Rig();r.model.open(ARTIST);r.requests.get(0).completeExceptionally(new RuntimeException());r.model.poll();r.model.poll();
        assertEquals("failed",r.model.status);assertEquals(1,r.requests.size());r.model.load(1);assertEquals(2,r.requests.size());
        r.supported=false;r.model.open(ARTIST);assertEquals("unavailable",r.model.status);assertEquals(2,r.requests.size());assertEquals(-1,r.selected);
    }
}
