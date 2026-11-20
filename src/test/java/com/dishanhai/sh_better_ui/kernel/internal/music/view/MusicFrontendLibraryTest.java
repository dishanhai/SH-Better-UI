// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendLibrary.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicFrontendLibraryTest {
    private static final OnlineTrack A = track("qq", "a"), B = track("qq", "b");
    private static final Key DETAIL = Key.detail("qq","44"), PLAYLISTS = Key.collection("qq",Kind.PLAYLISTS),
            LIKED = Key.collection("qq",Kind.LIKED);

    @Test void minimumCompactLayoutHasOneItemAndNonoverlappingPager() {
        var frame=new com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds(0,0,208,240);
        var layout=MusicFrontendLayout.compute(frame,MusicFrontendLayout.Route.LIBRARY,false);
        assertTrue(layout.supported());
        var area=layout.body();
        assertEquals(1,MusicFrontendLibraryPanel.visibleRows(area));
        var row=MusicFrontendLibraryPanel.rowBounds(area,0);
        var prev=MusicFrontendLibraryPanel.previousBounds(area);
        var next=MusicFrontendLibraryPanel.nextBounds(area);
        assertTrue(row.y()>=layout.header().bottom());
        assertTrue(row.bottom()<=prev.y());
        assertTrue(prev.right()<=next.x());
        assertTrue(next.bottom()<=layout.transport().y());
    }

    @Test void supportedProvidersAndSignedOutRoutesDoNotStartReads() {
        var rig=new Rig();
        assertEquals(List.of("qq","netease"),rig.model.providers());
        rig.authenticated=false;
        rig.model.open(PLAYLISTS);
        assertEquals(Status.SIGN_IN,rig.model.view().status());
        assertTrue(rig.calls.isEmpty());
        rig.authenticated=true;
        rig.model.retry();
        assertEquals(List.of(new Call("playlists","qq",1)),rig.calls);
    }

    @Test void shelfPagesPreserveProviderOrderAndReturnToTheLastPage() {
        var rig=new Rig();
        rig.model.open(PLAYLISTS);
        var candidates=java.util.stream.IntStream.range(1,21).mapToObj(i->new OnlinePlaylist("qq",""+i,"List "+i,i)).toList();
        rig.complete(candidates);
        assertEquals(candidates,rig.model.view().playlists());
        assertTrue(rig.model.view().more());
        rig.model.page(1);
        assertEquals(new Call("playlists","qq",2),rig.calls.getLast());
        rig.complete(List.of(new OnlinePlaylist("qq","21","Last",0)));
        assertFalse(rig.model.view().more());
        rig.model.open(DETAIL);
        rig.complete(detail("44",List.of(A)));
        rig.model.open(PLAYLISTS);
        assertEquals(new Call("playlists","qq",2),rig.calls.getLast());
    }

    @Test void playlistPlaybackKeepsDuplicateOccurrencesAndWholeListOrder() {
        var rig=new Rig();
        rig.model.open(DETAIL);
        assertEquals(new Call("playlistDetail","qq","44"),rig.calls.getFirst());
        assertFalse(rig.model.play(rig.model.view(),0));
        rig.complete(detail("44",List.of(A,B,A)));
        assertEquals(Status.READY,rig.model.view().status());
        assertTrue(rig.model.play(rig.model.view(),2));
        assertEquals(List.of(A,B,A),rig.queued);
        assertEquals(2,rig.index);
        assertEquals(OnlineMusicQueue.Mode.SEQUENTIAL,rig.mode);
    }

    @Test void likedQueueUsesOnlyLoadedPageAndRejectsAStaleButtonAfterPaging() {
        var rig=new Rig();
        rig.model.open(LIKED);
        var tracks=java.util.stream.IntStream.range(1,21).mapToObj(i->track("qq",""+i)).toList();
        rig.complete(tracks);
        var before=rig.model.view();
        rig.model.page(1);
        assertFalse(rig.model.play(before,0));
        rig.complete(List.of(B,A));
        assertTrue(rig.model.play(rig.model.view(),1));
        assertEquals(List.of(B,A),rig.queued);
    }

    @Test void unreadLateCompletionsNeverExposeAnotherAccountAndRefreshIsExplicit() {
        var rig=new Rig();
        rig.model.open(DETAIL);
        var request=rig.pending;
        ++rig.epoch;
        request.complete(detail("44",List.of(A)));
        rig.model.poll();
        assertEquals(Status.CHANGED,rig.model.view().status());
        assertTrue(rig.model.view().tracks().isEmpty());
        assertEquals(1,rig.calls.size());
        rig.model.retry();
        rig.complete(detail("44",List.of(B)));
        var shown=rig.model.view();
        ++rig.epoch;
        assertFalse(rig.model.play(shown,0),"account can change after paint and before the next poll");
        assertTrue(rig.queued.isEmpty());
    }

    @Test void switchingRoutesDiscardsUncancellableOldFutureAndWrongIdentity() {
        var rig=new Rig();
        rig.model.open(DETAIL);
        var old=rig.pending;
        rig.model.open(LIKED);
        old.complete(detail("44",List.of(A)));
        rig.model.poll();
        assertEquals(Status.LOADING,rig.model.view().status());
        rig.complete(List.of(B));
        assertEquals(List.of(B),rig.model.view().tracks());
        rig.model.open(DETAIL);
        rig.complete(detail("45",List.of(B)));
        assertEquals(Status.FAILED,rig.model.view().status());
        assertFalse(rig.model.play(rig.model.view(),0));
    }

    @Test void rejectedReadInvalidatesAccountAndShowsAuthorizationInsteadOfEmptyLibrary() {
        var rig=new Rig();
        rig.model.open(DETAIL);
        ++rig.epoch;
        rig.authenticated=false;
        rig.pending.completeExceptionally(new OnlineMusicSource.LoginRequired());
        rig.model.poll();
        assertEquals(Status.AUTHORIZATION,rig.model.view().status());
        assertTrue(rig.model.view().tracks().isEmpty());
    }

    private static OnlineTrack track(String source,String id){return new OnlineTrack(source,id,id,id,"Artist","",1000);}
    private static OnlinePlaylistDetail detail(String id,List<OnlineTrack> tracks){
        return new OnlinePlaylistDetail(new OnlinePlaylist("qq",id,"Collection",tracks.size()),tracks);
    }

    private record Call(String kind,String source,Object identity) {}
    private static final class Uncancellable extends CompletableFuture<Object> {
        @Override public boolean cancel(boolean ignored){return false;}
    }
    private static final class Rig {
        boolean authenticated=true;
        long epoch;
        int index=-1;
        List<?> queued=List.of();
        OnlineMusicQueue.Mode mode;
        final List<Call> calls=new ArrayList<>();
        Uncancellable pending;
        final OnlineMusicQueue queue=(OnlineMusicQueue)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{OnlineMusicQueue.class},
                (proxy,method,args)->switch(method.getName()){
                    case "supportsModeReplacement" -> true;
                    case "replace" -> {queued=(List<?>)args[0];index=(Integer)args[1];mode=(OnlineMusicQueue.Mode)args[2];yield null;}
                    default -> throw new AssertionError(method.getName());
                });
        final OnlineMusicPlayer player=(OnlineMusicPlayer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{OnlineMusicPlayer.class},
                (proxy,method,args)->switch(method.getName()){
                    case "sources" -> List.of("qq","apple","spotify","netease");
                    case "authenticated" -> authenticated;
                    case "supportsPlaylistDetail" -> true;
                    case "playlists","liked","playlistDetail" -> {
                        calls.add(new Call(method.getName(),(String)args[0],args[1]));yield pending=new Uncancellable();
                    }
                    case "queue" -> queue;
                    default -> throw new AssertionError(method.getName());
                });
        final MusicFrontendLibrary model=new MusicFrontendLibrary(player,source->epoch);
        void complete(Object result){pending.complete(result);model.poll();}
    }
}
