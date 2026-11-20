// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTransportControl;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OnlineMusicQueueViewTest {
    private static final OnlineTrack A = new OnlineTrack("qq", "1", "1", "Song", "Artist", "", 1000);
    private static final OnlineTrack B = new OnlineTrack("qq", "2", "2", "Second", "Artist", "", 1000);
    private static class Fixture {
        OnlineTrack playing;
        final List<java.util.concurrent.CompletableFuture<OnlineArtistTracks>> artistRequests=new ArrayList<>();
        OnlineMusicQueue persistent;
        List<String> providers=List.of("netease","qq");
        int paused,closed,seeks;
        long clipOffset, lastSeek;
        OnlineMusicPlayer.Snapshot snapshotOverride;
        final OnlineMusicPlayer player = (OnlineMusicPlayer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{OnlineMusicPlayer.class}, (proxy, method, args) -> switch(method.getName()) {
            case "supportsArtistTracks" -> true;
            case "artistTracks" -> {var request=new java.util.concurrent.CompletableFuture<OnlineArtistTracks>();artistRequests.add(request);yield request;}
            case "queue" -> persistent;
            case "seek" -> {seeks++;lastSeek=(Long)args[0];yield null;}
            case "lyricOffsetMs" -> clipOffset;
            case "pause" -> { paused++;yield null; }
            case "close" -> { closed++;yield null; }
            case "sources" -> providers;
            case "authenticated", "supportsLikes", "remembersCredentials", "supportsVolume" -> false;
            case "play" -> { playing=(OnlineTrack)args[0]; yield null; }
            case "snapshot" -> snapshotOverride!=null?snapshotOverride:new OnlineMusicPlayer.Snapshot(playing, playing == null ? OnlineMusicPlayer.State.IDLE : OnlineMusicPlayer.State.PLAYING, 0, 1000, OnlineMusicPlayer.LyricsState.EMPTY, LyricTimeline.empty(), OnlineMusicPlayer.Failure.NONE, false);
            default -> null;
        });
        final OnlineMusicViewImpl view;
        Fixture() { this(null); }
        Fixture(OnlineMusicQueue queue) { persistent = queue; view = new OnlineMusicViewImpl(player); }
    }
    private static Object field(Object owner,String name) throws Exception { var f=owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
    private static void call(Object owner,String name,Class<?>[] types,Object... args) throws Exception { var m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(owner,args); }
    private static void queuePage(OnlineMusicViewImpl view) throws Exception {
        var f=view.getClass().getDeclaredField("page"); f.setAccessible(true);
        Object page=Arrays.stream(f.getType().getEnumConstants()).filter(v->v.toString().equals("QUEUE")).findFirst().orElseThrow();
        call(view,"show",new Class<?>[]{f.getType()},page);
        call(view,"setQueueEditing",new Class<?>[]{boolean.class},true);
    }
    private static class Host implements com.dishanhai.sh_better_ui.client.api.preview.PreviewContext {
        int width = 800, height = 600;
        final net.minecraft.client.gui.screens.Screen screen = new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.empty()) {};
        final List<net.minecraft.client.gui.components.AbstractWidget> widgets = new ArrayList<>();
        final net.minecraft.client.StringSplitter splitter = new net.minecraft.client.StringSplitter((codePoint, style) -> 5f);
        final net.minecraft.client.gui.Font font = new net.minecraft.client.gui.Font(ignored -> null, false) {
            @Override public net.minecraft.client.StringSplitter getSplitter() { return Host.this.splitter; }
            @Override public int width(String text) { return (int)Math.ceil(Host.this.splitter.stringWidth(text)); }
            @Override public int width(net.minecraft.util.FormattedCharSequence text) { return (int)Math.ceil(Host.this.splitter.stringWidth(text)); }
            @Override public int width(net.minecraft.network.chat.FormattedText text) { return (int)Math.ceil(Host.this.splitter.stringWidth(text)); }
            @Override public List<net.minecraft.util.FormattedCharSequence> split(net.minecraft.network.chat.FormattedText text,int width) {
                return Host.this.splitter.splitLines(text,width,net.minecraft.network.chat.Style.EMPTY).stream().map(net.minecraft.locale.Language.getInstance()::getVisualOrder).toList();
            }
        };
        @Override public net.minecraft.client.gui.screens.Screen screen() { return screen; }
        @Override public com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds viewport() { return new com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds(0,0,width,height); }
        @Override public com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds toolbar() { return viewport(); }
        @Override public net.minecraft.client.gui.Font font() { return font; }
        @Override public com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences preferences() { return com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences.DEFAULT; }
        @Override public com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost overlays() { return null; }
        @Override public com.dishanhai.sh_better_ui.client.api.UiApiContext context() { return null; }
        @Override public boolean interactive() { return true; }
        @Override public <T extends net.minecraft.client.gui.components.AbstractWidget> T add(T widget) { widgets.add(widget);return widget; }
        @Override public <T extends net.minecraft.client.gui.components.AbstractWidget> T addOverlay(T widget) { return add(widget); }
    }

    private static class EditableQueue implements OnlineMusicQueue {
        List<OnlineTrack> tracks = List.of(A, B);
        List<OnlineTrack> previous;
        int index, moves, appends;
        public boolean supportsEditing() { return true; }
        public Snapshot snapshot() { return new Snapshot(tracks, index, previous != null); }
        public void append(List<OnlineTrack> additions) { previous = tracks; var copy = new ArrayList<>(tracks); copy.addAll(additions); tracks = List.copyOf(copy); appends++; }
        public void move(int from, int to) { previous = tracks; var copy = new ArrayList<>(tracks); copy.add(to, copy.remove(from)); tracks = List.copyOf(copy); index = to; moves++; }
        public void undo() { tracks = previous; previous = null; }
        public void replace(List<OnlineTrack> tracks, int selected) { this.tracks = tracks; index = selected; }
        public void select(int selected) { index = selected; }
        public void next() {} public void previous() {} public void remove(int selected) {} public void clear() {}
    }
    @Test void editableQueueControlsDelegateWithoutInterruptingPlaybackAndUndo() throws Exception {
        var queue = new EditableQueue(); var f = new Fixture(queue); f.playing = A;
        var host = new Host(); f.view.layout(host); queuePage(f.view);
        var up = (MusicTextControl)((List<?>)field(f.view,"queueUps")).get(0);
        var down = (MusicTextControl)((List<?>)field(f.view,"queueDowns")).get(0);
        assertFalse(up.active); assertTrue(down.active); down.onPress();
        assertEquals(List.of(B,A),queue.tracks); assertEquals(1,queue.moves);
        assertSame(((List<?>)field(f.view,"queueRows")).get(1),host.screen.getFocused());
        ((MusicTextControl)field(f.view,"queueUndo")).onPress(); assertEquals(List.of(A,B),queue.tracks);
        var append = (MusicTextControl)field(f.view,"queueAppend"); assertTrue(append.active); append.onPress();
        assertEquals(List.of(A,B,A),queue.tracks); assertEquals(1,queue.appends);
        assertEquals(A,f.playing); assertEquals(0,f.paused); assertEquals(0,f.closed);
        call(f.view,"enterImmersive",new Class<?>[0]); call(f.view,"updateQueueRows",new Class<?>[0]);
        assertFalse(append.visible); assertFalse(down.visible);
    }
    @Test void editControlsAreHiddenForLegacyQueuesAndBoundedAtSmallSizes() throws Exception {
        var legacy = new Fixture(); legacy.view.layout(new Host()); queuePage(legacy.view);
        assertFalse(((MusicTextControl)field(legacy.view,"queueAppend")).visible);
        assertTrue(((List<?>)field(legacy.view,"queueUps")).stream().noneMatch(w->((MusicTextControl)w).visible));
        for(int[] size:new int[][]{{208,240},{360,340},{800,600}}) {
            var f = new Fixture(new EditableQueue()); var host = new Host(); host.width=size[0];host.height=size[1];f.view.layout(host);queuePage(f.view);
            int right=-1;
            for(String name:List.of("queueRows","queueUps","queueDowns","queueRemoves")) {
                var w=(MusicTextControl)((List<?>)field(f.view,name)).get(0);
                assertTrue(w.visible);assertTrue(w.getWidth()>0);assertTrue(w.getX()>=right);right=w.getX()+w.getWidth();assertTrue(right<=size[0]);
            }
            assertFalse(((MusicTextControl)field(f.view,"queueAppend")).active);
        }
    }
    @Test void movingAcrossViewportKeepsTheMovedEntryVisibleAndFocused() throws Exception {
        var queue=new EditableQueue(); queue.tracks=java.util.stream.IntStream.range(0,30)
                .mapToObj(i->new OnlineTrack("qq",""+i,""+i,"Song "+i,"Artist","",1000)).toList();
        var f=new Fixture(queue);var host=new Host();host.width=360;host.height=340;f.view.layout(host);queuePage(f.view);
        var downs=(List<?>)field(f.view,"queueDowns");int last=downs.size()-1;var moved=queue.tracks.get(last);
        ((MusicTextControl)downs.get(last)).onPress();
        assertEquals(1,field(f.view,"queueFirst"));assertEquals(moved,queue.tracks.get(last+1));
        assertSame(downs.get(last),host.screen.getFocused());
        ((MusicTextControl)downs.get(last)).onPress();assertEquals(moved,queue.tracks.get(last+2));
        assertEquals(2,field(f.view,"queueFirst"));assertEquals(2,queue.moves);
    }
    @Test void suspendingCancelsArmedQueueEditsBeforeKeyboardOrPointerRelease() throws Exception {
        for(String name:List.of("queueAppend","queueUps","queueDowns")) for(boolean pointer:List.of(false,true)) {
            var queue=new EditableQueue();var f=new Fixture(queue);f.playing=A;f.view.layout(new Host());queuePage(f.view);
            var value=field(f.view,name);var control=value instanceof List<?> list?(MusicTextControl)list.get(name.equals("queueUps")?1:0):(MusicTextControl)value;
            control.setFocused(true);assertTrue(control.active);assertTrue(control.visible);
            if(pointer) assertTrue(control.mouseClicked(control.getX()+2,control.getY()+2,0));
            else assertTrue(control.keyPressed(32,0,0));
            assertTrue(control.isArmed());f.view.suspend();assertFalse(control.isArmed());
            if(pointer) assertFalse(control.mouseReleased(control.getX()+2,control.getY()+2,0));
            else assertFalse(control.keyReleased(32,0,0));
            assertEquals(0,queue.moves);assertEquals(0,queue.appends);assertEquals(List.of(A,B),queue.tracks);
        }
    }
    @Test void queueHeaderEditsReserveThePlaybackModeRegionAtCompactSizes() throws Exception {
        for(int[] size:new int[][]{{208,240},{360,340},{800,600}}) {
            var f=new Fixture(new EditableQueue());var host=new Host();host.width=size[0];host.height=size[1];f.view.layout(host);queuePage(f.view);
            var source=((OnlineMusicLayout)field(f.view,"layout")).sourceBar();
            var append=(MusicTextControl)field(f.view,"queueAppend");var locate=(MusicTextControl)field(f.view,"queueLocate");
            int modeRight=source.x()+Math.max(12,source.width()-96);
            assertTrue(append.getX()>=modeRight);assertTrue(append.getX()+append.getWidth()<=locate.getX());
            assertTrue(locate.getX()+locate.getWidth()<=source.right());assertEquals(24,locate.getWidth());
        }
    }
    @Test void changingVisibleQueueCancelsArmedEditControls() throws Exception {
        var queue=new EditableQueue(); var f=new Fixture(queue);f.view.layout(new Host());queuePage(f.view);
        var down=(MusicTextControl)((List<?>)field(f.view,"queueDowns")).get(0);
        down.setFocused(true);assertTrue(down.keyPressed(32,0,0));
        queue.tracks=List.of(B,A);call(f.view,"updateQueueRows",new Class<?>[0]);
        assertFalse(down.isArmed());assertFalse(down.keyReleased(32,0,0));assertEquals(0,queue.moves);
    }

    @Test void primaryLyricsEntrancesUseStageAndRestoreTheirOrigin() throws Exception {
        for (String entrance : List.of("navigation", "nowPlaying", "lyricsToggle")) {
            for (String origin : List.of("SEARCH", "ACCOUNTS", "QUEUE")) {
                var f = new Fixture(); f.view.layout(new Host());
                var pageField = f.view.getClass().getDeclaredField("page"); pageField.setAccessible(true);
                Object start = Arrays.stream(pageField.getType().getEnumConstants()).filter(v -> v.toString().equals(origin)).findFirst().orElseThrow();
                call(f.view, "show", new Class<?>[]{pageField.getType()}, start);
                if (entrance.equals("navigation")) {
                    Object lyrics = Arrays.stream(pageField.getType().getEnumConstants()).filter(v -> v.toString().equals("LYRICS")).findFirst().orElseThrow();
                    call(f.view, "navigate", new Class<?>[]{pageField.getType()}, lyrics);
                } else if(entrance.equals("lyricsToggle")) ((Runnable)field(field(f.view,entrance),"action")).run();
                else ((MusicTextControl)field(f.view, entrance)).onPress();
                assertEquals(true, field(f.view, "immersive"), entrance);
                assertEquals("LYRICS", field(f.view, "stagePage").toString());
                assertFalse(f.view.permitsScreenClose());
                assertEquals(origin, pageField.get(f.view).toString());
                assertEquals(0, f.paused); assertEquals(0, f.closed);
            }
        }
    }

    @Test void hidingAndReturningCancelsNavigationAndTitlePresses() throws Exception {
        var f=new Fixture();f.view.layout(new Host());
        var nav=(MusicTextControl)((List<?>)field(f.view,"navigation")).get(0);
        var title=(MusicTextControl)field(f.view,"nowPlaying");
        nav.setFocused(true);assertTrue(nav.keyPressed(32,0,0));
        title.setFocused(true);assertTrue(title.keyPressed(32,0,0));
        call(f.view,"enterImmersive",new Class<?>[0]);
        assertFalse(nav.isArmed());assertFalse(title.isArmed());
        assertFalse(f.view.permitsScreenClose());
        assertFalse(nav.keyReleased(32,0,0));assertFalse(title.keyReleased(32,0,0));
    }

    @Test void providerSegmentsFollowRealProvidersAndCancelOldSearchWithoutChangingAccounts() throws Exception {
        var f=new Fixture();f.providers=List.of("netease","qq","custom");var host=new Host();host.width=392;host.height=269;f.view.layout(host);
        var selector=(com.dishanhai.sh_better_ui.kernel.api.music.control.MusicSegmentedControl)field(f.view,"sourceSegments");
        assertEquals(3,selector.choiceCount());
        var pending=new java.util.concurrent.CompletableFuture<List<OnlineTrack>>();
        var pendingField=f.view.getClass().getDeclaredField("pending");pendingField.setAccessible(true);pendingField.set(f.view,pending);
        ((java.util.function.IntConsumer)field(selector,"onChange")).accept(1);
        assertTrue(pending.isCancelled());assertEquals("qq",field(f.view,"source"));assertEquals("netease",field(f.view,"accountSource"));assertEquals(1,selector.selectedIndex());assertSame(selector,host.screen.getFocused());
        selector.setFocused(true);assertTrue(selector.keyPressed(262,0,0));assertTrue(selector.isArmed());
        call(f.view,"enterImmersive",new Class<?>[0]);assertFalse(selector.visible);assertFalse(selector.isArmed());
    }
    @Test void sameTitleReplacementCancelsAnArmedSearchRow() throws Exception {
        var f=new Fixture();f.view.layout(new Host());var results=f.view.getClass().getDeclaredField("results");results.setAccessible(true);
        results.set(f.view,List.of(A));call(f.view,"updateRows",new Class<?>[0]);
        var row=(MusicTextControl)((List<?>)field(f.view,"resultRows")).get(0);row.setFocused(true);assertTrue(row.keyPressed(32,0,0));
        var like=(MusicTextControl)((List<?>)field(f.view,"searchLikes")).get(0);like.active=true;like.setFocused(true);assertTrue(like.keyPressed(32,0,0));
        var unlike=(MusicTextControl)((List<?>)field(f.view,"searchUnlikes")).get(0);unlike.active=true;unlike.setFocused(true);assertTrue(unlike.keyPressed(32,0,0));
        results.set(f.view,List.of(new OnlineTrack("qq","other","other",A.title(),"Different Artist","",1000)));
        call(f.view,"updateRows",new Class<?>[0]);assertFalse(row.isArmed());assertFalse(row.keyReleased(32,0,0));assertFalse(like.isArmed());assertFalse(unlike.isArmed());assertFalse(like.keyReleased(32,0,0));assertFalse(unlike.keyReleased(32,0,0));assertNull(f.playing);
    }

    @Test void initialSearchStatusDoesNotRepeatTheSameInstructionAsDetail() throws Exception {
        var f=new Fixture();f.view.layout(new Host());
        var status=(com.dishanhai.sh_better_ui.kernel.api.music.status.MusicStatusView)field(f.view,"searchStatus");
        assertEquals(net.minecraft.network.chat.Component.empty(),field(status,"detail"));
        var narration=status.narration().getString();
        assertEquals(narration.indexOf("search_hint"),narration.lastIndexOf("search_hint"));
    }

    @Test void successfulResultsClearBusyTooltipAndQueueEmptyHasFocusableNarration() throws Exception {
        var f=new Fixture();f.view.layout(new Host());
        var state=f.view.getClass().getDeclaredField("searchState");state.setAccessible(true);state.set(f.view,"searching");
        var pending=f.view.getClass().getDeclaredField("pending");pending.setAccessible(true);pending.set(f.view,new java.util.concurrent.CompletableFuture<>());
        call(f.view,"updateRows",new Class<?>[0]);
        var search=(MusicTextControl)field(f.view,"searchButton");
        assertTrue(((net.minecraft.network.chat.Component)field(search.getTooltip(),"message")).getString().contains("searching"));
        pending.set(f.view,null);var results=f.view.getClass().getDeclaredField("results");results.setAccessible(true);results.set(f.view,List.of(A));
        call(f.view,"updateRows",new Class<?>[0]);
        assertEquals("sh_better_ui.online_music.search",((net.minecraft.network.chat.Component)field(search.getTooltip(),"message")).getString());
        queuePage(f.view);var nav=(MusicTextControl)((List<?>)field(f.view,"navigation")).get(3);
        assertTrue(nav.visible);assertTrue(nav.active);
        assertTrue(((net.minecraft.network.chat.Component)field(nav,"secondary")).getString().contains("queue.empty_hint"));
        call(f.view,"playCollection",new Class<?>[]{List.class,int.class},List.of(A),0);
        assertFalse(((net.minecraft.network.chat.Component)field(nav,"secondary")).getString().contains("queue.empty_hint"));
    }

    @Test void realSnapshotSynchronizationPreservesBrowseAndRejectsLateSeekAfterFailure() throws Exception {
        var f=new Fixture();f.view.layout(new Host());
        var timeline=new LyricTimeline(List.of(new com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricLine("first","",0,1000,List.of(),""),new com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricLine("second","",1000,2000,List.of(),"")));
        var lyrics=(MusicLyricsView)field(f.view,"stageLyrics");
        for(var state:List.of(OnlineMusicPlayer.State.PLAYING,OnlineMusicPlayer.State.PAUSED)) {
            f.snapshotOverride=new OnlineMusicPlayer.Snapshot(A,state,500,2000,OnlineMusicPlayer.LyricsState.READY,timeline,OnlineMusicPlayer.Failure.NONE,false);
            call(f.view,"syncStageLyrics",new Class<?>[]{OnlineMusicPlayer.Snapshot.class,boolean.class},f.snapshotOverride,true);
            assertEquals(0,lyrics.model().activeIndex());assertEquals(500,lyrics.model().positionMillis());lyrics.model().browse();
        }
        assertTrue(lyrics.browsing());
        f.snapshotOverride=new OnlineMusicPlayer.Snapshot(A,OnlineMusicPlayer.State.PLAYING,1500,2000,OnlineMusicPlayer.LyricsState.READY,timeline,OnlineMusicPlayer.Failure.NONE,false);
        call(f.view,"syncStageLyrics",new Class<?>[]{OnlineMusicPlayer.Snapshot.class,boolean.class},f.snapshotOverride,true);
        assertEquals(1,lyrics.model().activeIndex());assertTrue(lyrics.browsing());lyrics.follow();assertFalse(lyrics.browsing());
        f.snapshotOverride=new OnlineMusicPlayer.Snapshot(A,OnlineMusicPlayer.State.FAILED,1500,2000,OnlineMusicPlayer.LyricsState.READY,timeline,OnlineMusicPlayer.Failure.AUDIO,false);
        ((java.util.function.LongConsumer)field(lyrics,"seek")).accept(1000);assertEquals(0,f.seeks);
        call(f.view,"syncStageLyrics",new Class<?>[]{OnlineMusicPlayer.Snapshot.class,boolean.class},f.snapshotOverride,true);assertEquals(-1,lyrics.model().activeIndex());
        lyrics.model().browse();f.snapshotOverride=new OnlineMusicPlayer.Snapshot(B,OnlineMusicPlayer.State.PLAYING,500,2000,OnlineMusicPlayer.LyricsState.EMPTY,timeline,OnlineMusicPlayer.Failure.NONE,false);
        call(f.view,"syncStageLyrics",new Class<?>[]{OnlineMusicPlayer.Snapshot.class,boolean.class},f.snapshotOverride,true);
        assertTrue(lyrics.model().lines().isEmpty());assertFalse(lyrics.browsing());assertEquals(-1,lyrics.model().activeIndex());
    }

    @Test void draggingTimelinePreviewsLyricsWithoutSeekingUntilRelease() throws Exception {
        var f=new Fixture();f.view.layout(new Host());
        var timeline=new LyricTimeline(List.of(
                new com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricLine("first","",0,1000,List.of(),""),
                new com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricLine("second","",1000,2000,List.of(),"")));
        f.snapshotOverride=new OnlineMusicPlayer.Snapshot(A,OnlineMusicPlayer.State.PAUSED,500,2000,
                OnlineMusicPlayer.LyricsState.READY,timeline,OnlineMusicPlayer.Failure.NONE,false);
        var lyrics=(MusicLyricsView)field(f.view,"stageLyrics");
        var seek=(com.dishanhai.sh_better_ui.kernel.api.music.control.MusicSeekControl)field(f.view,"stageSeek");
        seek.visible=true;seek.sync("qq:1",500,2000,true);
        var types=new Class<?>[]{OnlineMusicPlayer.Snapshot.class,boolean.class};
        call(f.view,"syncStageLyrics",types,f.snapshotOverride,true);
        lyrics.model().browse();
        double from=seek.getX()+4+(seek.getWidth()-8)*.25;
        double to=seek.getX()+4+(seek.getWidth()-8)*.75;
        double y=seek.getY()+2;
        assertTrue(seek.mouseClicked(from,y,0));
        assertTrue(seek.mouseDragged(to,y,0,0,0));
        call(f.view,"syncStageLyrics",types,f.snapshotOverride,true);
        assertEquals(1500,lyrics.model().positionMillis());assertEquals(1,lyrics.model().activeIndex());
        assertFalse(lyrics.browsing());assertTrue(lyrics.model().seekTarget(1).isEmpty());assertEquals(0,f.seeks);
        seek.cancelInteraction();
        call(f.view,"syncStageLyrics",types,f.snapshotOverride,true);
        assertEquals(500,lyrics.model().positionMillis());assertEquals(0,lyrics.model().activeIndex());
        assertTrue(lyrics.model().seekTarget(1).isPresent());assertFalse(seek.mouseReleased(to,y,0));assertEquals(0,f.seeks);
        assertTrue(seek.mouseClicked(to,y,0));assertTrue(seek.mouseReleased(to,y,0));assertEquals(1,f.seeks);
        assertTrue(seek.mouseClicked(to,y,0));
        seek.sync("qq:2",100,2000,true);
        f.snapshotOverride=new OnlineMusicPlayer.Snapshot(B,OnlineMusicPlayer.State.PAUSED,100,2000,
                OnlineMusicPlayer.LyricsState.READY,timeline,OnlineMusicPlayer.Failure.NONE,false);
        call(f.view,"syncStageLyrics",types,f.snapshotOverride,true);
        assertEquals(100,lyrics.model().positionMillis());assertFalse(seek.mouseReleased(to,y,0));assertEquals(1,f.seeks);
    }

    @Test void authorizedPreviewScrubsInSongTimeAndLyricClickSeeksInClipTime() throws Exception {
        var f=new Fixture();f.view.layout(new Host());f.clipOffset=30000;
        var timeline=new LyricTimeline(List.of(
                new com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricLine("clip start","",30000,35000,List.of(),""),
                new com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricLine("second","",35000,45000,List.of(),"")));
        f.snapshotOverride=new OnlineMusicPlayer.Snapshot(A,OnlineMusicPlayer.State.PAUSED,2000,30000,
                OnlineMusicPlayer.LyricsState.READY,timeline,OnlineMusicPlayer.Failure.NONE,true);
        var lyrics=(MusicLyricsView)field(f.view,"stageLyrics");
        var seek=(com.dishanhai.sh_better_ui.kernel.api.music.control.MusicSeekControl)field(f.view,"stageSeek");
        seek.visible=true;seek.sync("qq:1",2000,30000,true);
        var types=new Class<?>[]{OnlineMusicPlayer.Snapshot.class,boolean.class};
        call(f.view,"syncStageLyrics",types,f.snapshotOverride,true);
        assertEquals(32000,lyrics.model().positionMillis());
        double target=seek.getX()+4+(seek.getWidth()-8)/6d;
        assertTrue(seek.mouseClicked(target,seek.getY()+2,0));
        call(f.view,"syncStageLyrics",types,f.snapshotOverride,true);
        assertEquals(35000,lyrics.model().positionMillis());assertEquals(1,lyrics.model().activeIndex());
        seek.cancelInteraction();call(f.view,"syncStageLyrics",types,f.snapshotOverride,true);
        ((java.util.function.LongConsumer)field(lyrics,"seek")).accept(35000);
        assertEquals(1,f.seeks);assertEquals(5000,f.lastSeek);
        ((java.util.function.LongConsumer)field(lyrics,"seek")).accept(65000);
        assertEquals(1,f.seeks);
    }

    @Test void compactResultsQueueAndMiniExposeRealArtistMetadata() throws Exception {
        var f=new Fixture();var host=new Host();host.width=393;host.height=270;f.view.layout(host);
        var results=f.view.getClass().getDeclaredField("results");results.setAccessible(true);results.set(f.view,List.of(A,B));
        call(f.view,"updateRows",new Class<?>[0]);
        var row=(MusicTextControl)((List<?>)field(f.view,"resultRows")).get(0);
        assertTrue(row.secondaryVisible());assertTrue(((net.minecraft.network.chat.Component)field(row,"secondary")).getString().contains(A.artist()));
        call(f.view,"playCollection",new Class<?>[]{List.class,int.class},List.of(A,B),0);
        call(f.view,"updateMiniMetadata",new Class<?>[]{OnlineMusicPlayer.Snapshot.class},f.player.snapshot());
        var mini=(MusicTextControl)field(f.view,"nowPlaying");assertTrue(mini.secondaryVisible());
        assertEquals(A.artist(),((net.minecraft.network.chat.Component)field(mini,"secondary")).getString());
        queuePage(f.view);var queued=(MusicTextControl)((List<?>)field(f.view,"queueRows")).get(0);
        assertTrue(queued.secondaryVisible());assertTrue(((net.minecraft.network.chat.Component)field(queued,"secondary")).getString().contains(A.artist()));
    }

    @Test void compactDockKeepsRealControlsWithinBoundsAndUsesPublicUukWidgets() throws Exception {
        for(int[] size:new int[][]{{208,240},{393,270},{590,406},{960,540}}) {
            var f=new Fixture();var host=new Host();host.width=size[0];host.height=size[1];f.view.layout(host);
            assertInstanceOf(com.dishanhai.sh_better_ui.kernel.api.music.control.MusicSeekControl.class,field(f.view,"seek"));
            for(String name:List.of("playback","stop","lyricsToggle","miniQueue")) {
                var w=assertInstanceOf(com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTransportControl.class,field(f.view,name));
                assertTrue(w.visible);assertTrue(w.getX()>=0);assertTrue(w.getX()+w.getWidth()<=size[0]);
                assertTrue(w.getY()>=0);assertTrue(w.getY()+w.getHeight()<=size[1]);
            }
            for(Object w:(List<?>)field(f.view,"navigation"))assertInstanceOf(MusicTextControl.class,w);
            assertInstanceOf(MusicTextControl.class,field(f.view,"searchButton"));
            ((Runnable)field(field(f.view,"miniQueue"),"action")).run();assertEquals("QUEUE",field(f.view,"page").toString());
            assertEquals(0,f.paused);assertEquals(0,f.closed);
        }
    }

    @Test void leavingEditorCancelsPendingMoveAndResizeKeepsEditor() throws Exception {
        var queue=new EditableQueue(); var f=new Fixture(queue); var host=new Host(); f.view.layout(host); queuePage(f.view);
        var down=(MusicTextControl)((List<?>)field(f.view,"queueDowns")).get(0);
        down.setFocused(true); assertTrue(down.keyPressed(32,0,0));
        assertFalse(f.view.permitsScreenClose()); assertFalse(down.isArmed());
        assertFalse(down.keyReleased(32,0,0)); assertEquals(0,queue.moves);
        ((MusicTextControl)field(f.view,"queueEdit")).onPress();
        host.width=208;host.height=240; f.view.layout(host);
        assertEquals(true,field(f.view,"queueEditing"));
        assertTrue(((MusicTextControl)field(f.view,"queueClear")).visible);
    }
    @Test void compactFeedbackUsesVisibleTitleAndPlaybackFailureTakesPriority() throws Exception {
        var f=new Fixture();var host=new Host();host.width=208;host.height=240;f.view.layout(host);
        var feedback=(OnlineMusicFeedback)field(f.view,"interactionFeedback");
        feedback.show(net.minecraft.network.chat.Component.literal("Action received"),System.nanoTime()/1_000_000L);
        call(f.view,"updateMiniMetadata",new Class<?>[]{OnlineMusicPlayer.Snapshot.class},f.player.snapshot());
        var title=(MusicTextControl)field(f.view,"nowPlaying");assertEquals("Action received",title.getMessage().getString());
        var failed=new OnlineMusicPlayer.Snapshot(A,OnlineMusicPlayer.State.FAILED,0,1000,OnlineMusicPlayer.LyricsState.EMPTY,LyricTimeline.empty(),OnlineMusicPlayer.Failure.AUDIO,false);
        call(f.view,"updateMiniMetadata",new Class<?>[]{OnlineMusicPlayer.Snapshot.class},failed);
        assertTrue(title.getMessage().getString().contains("failure.audio"));
        feedback.clear();f.playing=A;call(f.view,"updateMiniMetadata",new Class<?>[]{OnlineMusicPlayer.Snapshot.class},f.player.snapshot());
        assertEquals(A.title(),title.getMessage().getString());
    }
    @Test void actionFailureUsesSanitizedFeedbackAndSuspendClearsIt() throws Exception {
        var f=new Fixture(); f.view.layout(new Host());
        call(f.view,"perform",new Class<?>[]{net.minecraft.network.chat.Component.class,Runnable.class},
                net.minecraft.network.chat.Component.literal("Import credentials"),(Runnable)()->{throw new IllegalArgumentException("secret-cookie");});
        var feedback=(OnlineMusicFeedback)field(f.view,"interactionFeedback");
        var message=feedback.current(System.nanoTime()/1_000_000L).getString();
        assertTrue(message.contains("feedback.failed"));assertFalse(message.contains("secret-cookie"));
        f.view.suspend();assertEquals("",feedback.current(System.nanoTime()/1_000_000L).getString());
    }
    @Test void queueButtonAcknowledgesCurrentLabelWithoutClaimingRemoteSuccess() throws Exception {
        var f=new Fixture();f.view.layout(new Host());queuePage(f.view);
        var edit=(MusicTextControl)field(f.view,"queueEdit");
        var actionName=edit.getMessage().copy();
        assertEquals(true,field(f.view,"queueEditing"));
        edit.onPress();
        assertEquals(false,field(f.view,"queueEditing"));
        var feedback=(OnlineMusicFeedback)field(f.view,"interactionFeedback");
        var message=feedback.current(System.nanoTime()/1_000_000L);
        var acknowledgement=assertInstanceOf(net.minecraft.network.chat.contents.TranslatableContents.class,message.getContents());
        assertEquals("sh_better_ui.online_music.feedback.requested",acknowledgement.getKey());
        assertArrayEquals(new Object[]{actionName},acknowledgement.getArgs());
        var action=assertInstanceOf(net.minecraft.network.chat.contents.TranslatableContents.class,actionName.getContents());
        assertEquals("sh_better_ui.online_music.queue.done",action.getKey());
        assertFalse(feedback.isError(System.nanoTime()/1_000_000L));
    }
    @Test void queueEditorReturnsWithoutLosingPlaybackOrScroll() throws Exception {
        var f = new Fixture(new EditableQueue()); f.playing=A; var host=new Host(); f.view.layout(host); queuePage(f.view);
        var edit=(MusicTextControl)field(f.view,"queueEdit");
        var remove=(MusicTextControl)((List<?>)field(f.view,"queueRemoves")).get(0);
        assertTrue(remove.visible); edit.onPress(); assertFalse(remove.visible);
        assertFalse(((MusicTextControl)field(f.view,"queueClear")).visible);
        int width=((MusicTextControl)((List<?>)field(f.view,"queueRows")).get(0)).getWidth();
        edit.onPress(); assertTrue(remove.visible);
        assertTrue(width>((MusicTextControl)((List<?>)field(f.view,"queueRows")).get(0)).getWidth());
        assertFalse(f.view.permitsScreenClose()); assertFalse(remove.visible);
        assertSame(edit,host.screen.getFocused()); assertEquals("QUEUE",field(f.view,"page").toString());
        assertEquals(A,f.playing); assertEquals(0,f.paused); assertEquals(0,f.closed);
    }
    @Test void realQueueControlsPlayClearUndoAndReturnWithoutStopping() throws Exception {
        var f=new Fixture(); f.view.layout(new Host());
        call(f.view,"playCollection",new Class<?>[]{List.class,int.class},List.of(A,B),0);
        assertEquals(A,f.playing); queuePage(f.view);
        var next=(MusicTransportControl)field(f.view,"miniNext"); assertTrue(next.visible);((Runnable)field(next,"action")).run();assertEquals(B,f.playing);
        var clear=(MusicTextControl)field(f.view,"queueClear");clear.onPress();assertEquals(B,f.playing);
        var undo=(MusicTextControl)field(f.view,"queueUndo");assertTrue(undo.active);undo.onPress();
        assertEquals(2,((PlayerQueue)field(f.view,"queue")).tracks().size());assertEquals(B,f.playing);
        ((MusicTextControl)field(f.view,"back")).onPress();assertFalse(clear.visible);assertTrue(next.visible);assertEquals(B,f.playing);
    }
    @Test void queueActionControlsStayDisjointAtSupportedWindowSizes() throws Exception {
        for (int[] size: new int[][]{{208,240},{360,340},{520,264},{800,600}}) {
            var f=new Fixture(); var host=new Host();host.width=size[0];host.height=size[1];f.view.layout(host);queuePage(f.view);
            var names=List.of("queueEdit","queueUndo","queueClear");
            int right=-1;
            for (String name:names) { var b=(MusicTextControl)field(f.view,name);assertTrue(b.getX()>=right,name);assertTrue(b.getWidth()>0);assertTrue(b.getX()+b.getWidth()<=size[0]);right=b.getX()+b.getWidth(); }
        }
    }
    @Test void suspendingTheActualViewDisarmsAutoplayWithoutDroppingItsQueue() throws Exception {
        var f=new Fixture();f.view.layout(new Host());
        call(f.view,"playCollection",new Class<?>[]{List.class,int.class},List.of(A,B),0);
        var queue=(PlayerQueue)field(f.view,"queue");queue.observe(f.player.snapshot());
        f.view.suspend();
        var end=new OnlineMusicPlayer.Snapshot(A,OnlineMusicPlayer.State.ENDED,1000,1000,OnlineMusicPlayer.LyricsState.EMPTY,LyricTimeline.empty(),OnlineMusicPlayer.Failure.NONE,false);
        queue.observe(end);assertEquals(A,f.playing);assertEquals(2,queue.tracks().size());assertEquals(0,queue.index());
    }
    @Test void immersiveEntryHidesNavigationAndReturnRestoresQueueWithoutPausing() throws Exception {
        var f=new Fixture();f.view.layout(new Host());
        call(f.view,"playCollection",new Class<?>[]{List.class,int.class},java.util.Collections.nCopies(20,A),0);queuePage(f.view);
        var offset=f.view.getClass().getDeclaredField("queueFirst");offset.setAccessible(true);offset.set(f.view,3);
        call(f.view,"enterImmersive",new Class<?>[0]);
        assertEquals(true,field(f.view,"immersive"));
        assertTrue(((List<?>)field(f.view,"navigation")).stream().allMatch(w->!((net.minecraft.client.gui.components.AbstractWidget)w).visible));
        assertFalse(((MusicTextControl)field(f.view,"nowPlaying")).visible);
        assertTrue(((net.minecraft.client.gui.components.AbstractWidget)field(f.view,"stageBack")).visible);
        assertEquals(0,f.paused);assertEquals(0,f.closed);
        assertFalse(f.view.permitsScreenClose());assertEquals(false,field(f.view,"immersive"));
        assertEquals("QUEUE",field(f.view,"page").toString());assertEquals(3,field(f.view,"queueFirst"));
        assertFalse(f.view.permitsScreenClose()); // leave the retained queue editor first
        assertTrue(f.view.permitsScreenClose());assertEquals(A,f.playing);
    }
    @Test void immersiveResizeKeepsModeAndReturnsToTheSamePage() throws Exception {
        var f=new Fixture();f.view.layout(new Host());queuePage(f.view);call(f.view,"enterImmersive",new Class<?>[0]);
        var compact=new Host();compact.width=208;compact.height=240;f.view.layout(compact);
        assertEquals(true,field(f.view,"immersive"));
        assertTrue(((net.minecraft.client.gui.components.AbstractWidget)field(f.view,"stageBack")).visible);
        assertFalse(f.view.permitsScreenClose());assertEquals("QUEUE",field(f.view,"page").toString());
        assertEquals(0,f.paused);assertEquals(0,f.closed);
    }
    @Test void immersiveLyricWheelReturnsFocusToVisibleStageControl() throws Exception {
        var f=new Fixture();var host=new Host();f.view.layout(host);call(f.view,"enterImmersive",new Class<?>[0]);
        var rows=(java.util.List<?>)field(f.view,"lyricRows");
        var scroll=(java.util.function.IntConsumer)field(rows.get(0),"scroll");scroll.accept(1);
        var target=(net.minecraft.client.gui.components.AbstractWidget)field(f.view,"stageBack");
        assertTrue(target.visible);assertSame(target,host.screen.getFocused());
        assertFalse(((MusicTextControl)field(f.view,"back")).visible);
    }
    @Test void windowLossCancelsMusicGesturesWithoutPausingTheSession() throws Exception {
        var f=new Fixture();f.view.layout(new Host());call(f.view,"enterImmersive",new Class<?>[0]);
        var seek=(com.dishanhai.sh_better_ui.kernel.api.music.control.MusicSeekControl)field(f.view,"stageSeek");
        seek.sync("qq:1",0,1000,true);assertTrue(seek.mouseClicked(seek.getX()+10,seek.getY()+4,0));assertTrue(seek.isEditing());
        var play=(com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTransportControl)field(f.view,"stagePlay");
        play.setFocused(true);assertTrue(play.keyPressed(32,0,0));
        call(f.view,"cancelStageInteractions",new Class<?>[0]);
        assertFalse(seek.isEditing());assertFalse(play.keyReleased(32,0,0));assertEquals(0,f.paused);assertEquals(0,f.closed);
    }
    @Test void artworkAndLyricModesShareControlsAndDoNotReplacePlayback() throws Exception {
        var f=new Fixture();f.view.layout(new Host());call(f.view,"playCollection",new Class<?>[]{List.class,int.class},List.of(A,B),0);
        call(f.view,"enterImmersive",new Class<?>[0]);
        Object seek=field(f.view,"stageSeek");Object play=field(f.view,"stagePlay");
        var mode=f.view.getClass().getDeclaredField("stagePage");mode.setAccessible(true);
        Object artwork=Arrays.stream(mode.getType().getEnumConstants()).filter(v->v.toString().equals("ARTWORK")).findFirst().orElseThrow();
        call(f.view,"setStagePage",new Class<?>[]{mode.getType()},artwork);
        assertSame(seek,field(f.view,"stageSeek"));assertSame(play,field(f.view,"stagePlay"));assertEquals(A,f.playing);assertEquals(0,f.paused);assertEquals(0,f.closed);
    }
    @Test void tinyResizeDoesNotBuildInvalidStageOrDestroyPlayer() throws Exception {
        for(int width:new int[]{0,1,23}) {
            var f=new Fixture();f.view.layout(new Host());
            call(f.view,"playCollection",new Class<?>[]{List.class,int.class},List.of(A,B),0);
            call(f.view,"enterImmersive",new Class<?>[0]);
            var tiny=new Host();tiny.width=width;tiny.height=1;
            assertDoesNotThrow(()->f.view.layout(tiny));assertNull(field(f.view,"stage"));
            assertEquals(A,f.playing);assertEquals(0,f.paused);assertEquals(0,f.closed);
            f.view.layout(new Host());assertEquals(true,field(f.view,"immersive"));assertNotNull(field(f.view,"stage"));
        }
    }
    @Test void resizePreservesQueueSelectionWithoutReplaying() throws Exception {
        var f=new Fixture();f.view.layout(new Host());call(f.view,"playCollection",new Class<?>[]{List.class,int.class},List.of(A,B),1);queuePage(f.view);
        var host=new Host();host.width=360;host.height=340;f.view.layout(host);
        var queue=(PlayerQueue)field(f.view,"queue");assertEquals(1,queue.index());assertEquals(2,queue.tracks().size());assertEquals(B,f.playing);
        assertTrue(((MusicTextControl)field(f.view,"queueEdit")).visible);
    }
    private static final class ModeQueue implements OnlineMusicQueue {
        Mode mode=Mode.SEQUENTIAL;int changes;
        public boolean supportsModes(){return true;}
        public Mode mode(){return mode;}
        public void mode(Mode value){mode=value;changes++;}
        public Snapshot snapshot(){return new Snapshot(List.of(A,B),0,false,mode,false,true);}
        public void replace(List<OnlineTrack> songs,int selected){}
        public void select(int selected){}
        public void next(){} public void previous(){} public void remove(int selected){}
        public void clear(){} public void undo(){}
    }
    @Test void queueModeButtonCyclesAllModesWithoutPlaybackAndPersistsAcrossRelayout() throws Exception {
        var backend=new ModeQueue();var f=new Fixture(backend);var host=new Host();f.view.layout(host);queuePage(f.view);
        var control=(MusicTextControl)field(f.view,"queuePlaybackMode");
        assertTrue(control.visible);assertTrue(control.active);
        for(var mode:List.of(OnlineMusicQueue.Mode.REPEAT_ALL,OnlineMusicQueue.Mode.REPEAT_ONE,OnlineMusicQueue.Mode.SHUFFLE,OnlineMusicQueue.Mode.SEQUENTIAL)){
            control.onPress();assertEquals(mode,backend.mode);
        }
        control.onPress();f.view.layout(host);queuePage(f.view);
        assertEquals(OnlineMusicQueue.Mode.REPEAT_ALL,backend.mode);
        assertEquals(5,backend.changes);assertNull(f.playing);assertEquals(0,f.paused);
    }
    @Test void legacyQueueModeControlIsDisabledAndHiddenStageCannotActivateIt() throws Exception {
        var f=new Fixture();f.view.layout(new Host());queuePage(f.view);
        var control=(MusicTextControl)field(f.view,"queuePlaybackMode");assertFalse(control.active);control.onPress();
        var stage=(MusicTextControl)field(f.view,"stagePlaybackMode");assertFalse(stage.visible);stage.onPress();
        assertNull(f.playing);assertEquals(0,f.paused);
    }
    @Test void immersiveQueueModeUsesSameBackendAndHidesOnReturnToLyrics() throws Exception {
        var backend=new ModeQueue();var f=new Fixture(backend);f.view.layout(new Host());
        call(f.view,"enterImmersive",new Class<?>[]{});
        var pageField=f.view.getClass().getDeclaredField("stagePage");pageField.setAccessible(true);
        var queuePage=Arrays.stream(pageField.getType().getEnumConstants()).filter(v->v.toString().equals("QUEUE")).findFirst().orElseThrow();
        call(f.view,"setStagePage",new Class<?>[]{pageField.getType()},queuePage);
        var control=(MusicTextControl)field(f.view,"stagePlaybackMode");assertTrue(control.visible);control.onPress();
        assertEquals(OnlineMusicQueue.Mode.REPEAT_ALL,backend.mode);
        var lyrics=Arrays.stream(pageField.getType().getEnumConstants()).filter(v->v.toString().equals("LYRICS")).findFirst().orElseThrow();
        call(f.view,"setStagePage",new Class<?>[]{pageField.getType()},lyrics);
        assertFalse(control.visible);control.onPress();assertEquals(1,backend.changes);
    }
    @Test void modeHeaderFitsCompactQueueWithoutOverlappingLocate() throws Exception {
        for(int[] size:new int[][]{{208,240},{393,270},{590,406},{960,540}}){
            var f=new Fixture(new ModeQueue());var host=new Host();host.width=size[0];host.height=size[1];f.view.layout(host);queuePage(f.view);
            var mode=(MusicTextControl)field(f.view,"queuePlaybackMode");var locate=(MusicTextControl)field(f.view,"queueLocate");
            assertTrue(mode.getX()>=0);assertTrue(mode.getWidth()>=12);
            assertTrue(mode.getX()+mode.getWidth()<=locate.getX());assertTrue(locate.getX()+locate.getWidth()<=size[0]);
        }
    }
    @Test void suspendingViewCancelsArmedModeKeyWithoutChangingOrder() throws Exception {
        var backend=new ModeQueue();var f=new Fixture(backend);f.view.layout(new Host());queuePage(f.view);
        var mode=(MusicTextControl)field(f.view,"queuePlaybackMode");mode.setFocused(true);
        assertTrue(mode.keyPressed(32,0,0));f.view.suspend();assertFalse(mode.keyReleased(32,0,0));
        assertEquals(0,backend.changes);
    }

    private static OnlineMusicPlayer session(java.util.concurrent.CountDownLatch release) throws Exception {
        return session(release,null,new java.util.concurrent.atomic.AtomicInteger());
    }
    private static OnlineMusicPlayer session(java.util.concurrent.CountDownLatch release, Exception likeError,java.util.concurrent.atomic.AtomicInteger writes) throws Exception {
        var providers=new ArrayList<OnlineMusicSource>();
        for(String source:List.of("qq","netease")) providers.add((OnlineMusicSource)Proxy.newProxyInstance(OnlineMusicQueueViewTest.class.getClassLoader(),new Class[]{OnlineMusicSource.class},(proxy,method,args)->switch(method.getName()) {
            case "id"->source;
            case "requestSnapshot"->proxy;
            case "search"->{release.await();yield List.of(A);}
            case "like"->{writes.incrementAndGet();release.await();if(likeError!=null)throw likeError;yield null;}
            case "qrLogin"->new OnlineMusicSource.QrChallenge() {
                public OnlineMusicLogin.Image image() {return new OnlineMusicLogin.Image(1,1,new BitSet());}
                public OnlineMusicSource.Poll poll() throws Exception {release.await();return new OnlineMusicSource.Poll(OnlineMusicLogin.State.WAITING,"");}
            };
            case "playlist","liked"->List.of(A);
            case "playlists"->List.of(new OnlinePlaylist("qq","new","New account list",1));
            case "supportsLikes"->false;
            case "credentials"->null;
            default->null;
        }));
        var constructor=Class.forName("com.dishanhai.sh_better_ui.kernel.internal.music.OnlinePlayerSession").getDeclaredConstructor(List.class);
        constructor.setAccessible(true);return (OnlineMusicPlayer)constructor.newInstance(providers);
    }
    @Test void detachedReadSurvivesViewSuspendAndCanBeRestoredFromHistory() throws Exception {
        var release=new java.util.concurrent.CountDownLatch(1);
        try(var player=session(release)) {
            var view=new OnlineMusicViewImpl(player,false);view.layout(new Host());
            var source=view.getClass().getDeclaredField("source");source.setAccessible(true);source.set(view,"qq");
            var query=view.getClass().getDeclaredField("query");query.setAccessible(true);query.set(view,"private query");
            call(view,"search",new Class<?>[]{int.class,boolean.class},1,true);
            var registry=com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.forPlayer(player).orElseThrow();
            var handle=registry.latest(com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.Kind.SEARCH,"qq").orElseThrow();
            view.suspend();assertFalse(handle.observe().isCancelled());assertTrue(handle.snapshot().background());
            release.countDown();handle.observe().get(3,java.util.concurrent.TimeUnit.SECONDS);
            var reopened=new OnlineMusicViewImpl(player,false);reopened.layout(new Host());
            call(reopened,"restoreOperation",new Class<?>[]{long.class},handle.id());
            assertEquals("private query",field(reopened,"submittedQuery"));
            assertEquals(List.of(A),((java.util.concurrent.CompletableFuture<?>)field(reopened,"pending")).join());
            assertFalse(handle.snapshot().background());
        } finally {release.countDown();}
    }
    @Test void suspendingBorrowedViewDetachesProviderWriteWithoutCancelling() throws Exception {
        var release=new java.util.concurrent.CountDownLatch(1);
        try(var player=session(release)) {
            player.credentials(A.source(),"fixture-account");
            var view=new OnlineMusicViewImpl(player,false); view.layout(new Host());
            call(view,"changeLike",new Class<?>[]{OnlineTrack.class,boolean.class},A,true);
            var registry=com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.forPlayer(player).orElseThrow();
            var handle=registry.latest(com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.Kind.LIKE,A.source()).orElseThrow();
            view.suspend(); // close delegates here before render-thread texture cleanup
            assertTrue(handle.snapshot().background());
            assertFalse(handle.observe().isCancelled());
            release.countDown();
            handle.observe().get(3,java.util.concurrent.TimeUnit.SECONDS);
        } finally { release.countDown(); }
    }
    @Test void explicitCancelAndCredentialInvalidationCannotRestoreOldReads() throws Exception {
        var release=new java.util.concurrent.CountDownLatch(1);
        try(var player=session(release)) {
            var view=new OnlineMusicViewImpl(player,false);view.layout(new Host());
            var source=view.getClass().getDeclaredField("source");source.setAccessible(true);source.set(view,"qq");
            var query=view.getClass().getDeclaredField("query");query.setAccessible(true);query.set(view,"private query");
            call(view,"search",new Class<?>[]{int.class,boolean.class},1,true);
            var registry=com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.forPlayer(player).orElseThrow();
            var handle=registry.latest(com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.Kind.SEARCH,"qq").orElseThrow();
            call(view,"cancelSearch",new Class<?>[]{});
            assertEquals(com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.State.CANCELLED,handle.snapshot().state());
            player.credentials("qq","new-account");
            call(view,"restoreOperation",new Class<?>[]{long.class},handle.id());
            assertNull(field(view,"pending"));assertTrue(registry.snapshots().isEmpty());
        } finally {release.countDown();}
    }

    private static void page(OnlineMusicViewImpl view,String name) throws Exception {
        var f=view.getClass().getDeclaredField("page");f.setAccessible(true);
        var target=Arrays.stream(f.getType().getEnumConstants()).filter(v->v.toString().equals(name)).findFirst().orElseThrow();
        call(view,"show",new Class<?>[]{f.getType()},target);
    }
    @Test void navigationAwayAndBackDoesNotLeavePermanentSearchingStatus() throws Exception {
        var release=new java.util.concurrent.CountDownLatch(1);
        try(var player=session(release)) {
            var view=new OnlineMusicViewImpl(player,false);view.layout(new Host());
            var query=view.getClass().getDeclaredField("query");query.setAccessible(true);query.set(view,"private query");
            call(view,"search",new Class<?>[]{int.class,boolean.class},1,true);
            var registry=com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.forPlayer(player).orElseThrow();
            var handle=registry.latest(com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.Kind.SEARCH,"netease").orElseThrow();
            page(view,"ACCOUNTS");release.countDown();handle.observe().get(3,java.util.concurrent.TimeUnit.SECONDS);
            page(view,"SEARCH");
            assertNull(field(view,"pending"));assertEquals("operations.background",field(view,"searchState"));
            assertTrue(((List<?>)field(view,"results")).isEmpty());
        } finally {release.countDown();}
    }
    @Test void historyRebindingAndSuspendDisarmHeldRows() throws Exception {
        var release=new java.util.concurrent.CountDownLatch(1);
        try(var player=session(release)) {
            var registry=com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.forPlayer(player).orElseThrow();
            var first=registry.startSearch("qq","private first",1);
            var view=new OnlineMusicViewImpl(player,false);view.layout(new Host());page(view,"HISTORY");
            var row=(MusicTextControl)((List<?>)field(view,"historyRows")).get(0);
            var held=MusicTextControl.class.getDeclaredField("pressedKey");held.setAccessible(true);held.setInt(row,32);assertTrue(row.isArmed());
            var second=registry.startSearch("qq","private second",1);call(view,"updateHistory",new Class<?>[]{});
            assertFalse(row.isArmed());assertEquals(second.id(),((Map<?,?>)field(view,"historyBindings")).get(row));
            held.setInt(row,32);page(view,"ACCOUNTS");assertFalse(row.isArmed());
            page(view,"HISTORY");held.setInt(row,32);view.suspend();assertFalse(row.isArmed());
            assertFalse(row.getMessage().getString().contains("private"));
        } finally {release.countDown();}
    }

    @Test void historyEntryLivesBesideSearchAndFitsSmallestLayout() throws Exception {
        var release=new java.util.concurrent.CountDownLatch(0);
        try(var player=session(release)) {
            var view=new OnlineMusicViewImpl(player,false);var host=new Host();host.width=208;host.height=240;view.layout(host);
            var entry=(MusicTextControl)field(view,"searchHistory");
            var query=(net.minecraft.client.gui.components.AbstractWidget)field(view,"queryField");
            var search=(MusicTextControl)field(view,"searchButton");
            assertTrue(entry.visible);assertTrue(entry.active);
            assertTrue(query.getX()+query.getWidth()<=search.getX());
            assertTrue(search.getX()+search.getWidth()<=entry.getX());
            assertTrue(entry.getX()+entry.getWidth()<=host.width);
            assertFalse(((List<?>)field(view,"accountControls")).contains(entry));
            entry.onPress();assertEquals("HISTORY",field(view,"page").toString());
            assertFalse(view.permitsScreenClose());assertEquals("SEARCH",field(view,"page").toString());
            page(view,"ACCOUNTS");assertFalse(entry.visible);
        }
    }

    @Test void displayedResultsOutliveHistoryRetentionButNotCredentialChange() throws Exception {
        for(boolean expire:List.of(false,true))try(var player=session(new java.util.concurrent.CountDownLatch(0))) {
            var registry=com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.forPlayer(player).orElseThrow();
            var handle=registry.startSearch("qq","private query",1);handle.observe().get(3,java.util.concurrent.TimeUnit.SECONDS);
            var view=new OnlineMusicViewImpl(player,false);view.layout(new Host());call(view,"restoreOperation",new Class<?>[]{long.class},handle.id());
            var results=view.getClass().getDeclaredField("results");results.setAccessible(true);results.set(view,List.of(A));
            var pending=view.getClass().getDeclaredField("pending");pending.setAccessible(true);pending.set(view,null);
            if(expire) {
                var completed=handle.getClass().getDeclaredField("completed");completed.setAccessible(true);completed.setLong(handle,System.nanoTime()-601_000_000_000L);
                registry.snapshots();
            } else for(int i=0;i<33;i++)registry.startSearch("qq","query "+i,1).observe().get(3,java.util.concurrent.TimeUnit.SECONDS);
            assertFalse(handle.valid());call(view,"pollOperations",new Class<?>[]{});
            assertEquals(List.of(A),field(view,"results"));
            player.credentials("qq","new-account");call(view,"pollOperations",new Class<?>[]{});
            assertTrue(((List<?>)field(view,"results")).isEmpty());
        }
    }
    @Test void credentialRolloverClearsPlaylistIdentityAndLikeCacheAndRetryOpensNewLibrary() throws Exception {
        var release=new java.util.concurrent.CountDownLatch(0);
        try(var player=session(release)) {
            player.credentials("qq","first-account");
            var view=new OnlineMusicViewImpl(player,false);view.layout(new Host());
            var playlist=new OnlinePlaylist("qq","old","Old list",1);
            var registry=com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.forPlayer(player).orElseThrow();
            var handle=registry.startPlaylist(playlist,1);handle.observe().get(3,java.util.concurrent.TimeUnit.SECONDS);
            call(view,"restoreOperation",new Class<?>[]{long.class},handle.id());call(view,"pollOperations",new Class<?>[]{});
            ((Map<String,Boolean>)field(view,"confirmedLikes")).put("qq:old",true);
            player.credentials("qq","second-account");call(view,"pollOperations",new Class<?>[]{});
            assertNull(field(view,"selectedPlaylist"));assertEquals("NONE",field(view,"library").toString());
            assertTrue(((Map<?,?>)field(view,"confirmedLikes")).isEmpty());
            call(view,"loadLibraryPage",new Class<?>[]{int.class},1);
            assertEquals("PLAYLISTS",field(view,"library").toString());
            assertNotNull(field(view,"pendingPlaylists"));
        }
    }

    @Test void historyLikeAndLoginNavigateWithoutReissuingTheirAction() throws Exception {
        var neverRelease=new java.util.concurrent.CountDownLatch(1);
        try(var player=session(neverRelease)) {
            player.credentials("qq","fixture");
            var registry=com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.forPlayer(player).orElseThrow();
            var like=registry.startLike(A,true);
            var view=new OnlineMusicViewImpl(player,false);var host=new Host();view.layout(host);
            page(view,"HISTORY");
            var row=(MusicTextControl)((List<?>)field(view,"historyRows")).get(0);
            assertTrue(row.active);row.onPress();
            assertEquals("LIBRARY",field(view,"page").toString());
            assertEquals("qq",field(view,"librarySource"));
            assertEquals("LIKED",field(view,"library").toString());
            assertNotNull(field(view,"pendingLibrary"));
            assertEquals(1,registry.snapshots().stream().filter(task->task.kind().mutation()).count());
            assertFalse(view.permitsScreenClose());assertEquals("HISTORY",field(view,"page").toString());
            assertEquals(like.id(),((Map<?,?>)field(view,"historyBindings")).get(host.screen().getFocused()));
            var attempt=registry.startLogin("netease");
            var login=registry.snapshots().stream().filter(task->task.kind().name().equals("LOGIN")).findFirst().orElseThrow();
            call(view,"updateHistory",new Class<?>[]{});
            call(view,"restoreOperation",new Class<?>[]{long.class},login.id());
            assertEquals("ACCOUNTS",field(view,"page").toString());
            assertEquals("netease",field(view,"accountSource"));
            assertTrue((Boolean)field(view,"accountDetail"));
            assertSame(attempt,registry.currentLogin("netease").orElseThrow());
            assertEquals(1,registry.snapshots().stream().filter(task->task.kind().name().equals("LOGIN")).count());
            assertFalse(view.permitsScreenClose());assertEquals("HISTORY",field(view,"page").toString());
            assertFalse(like.observe().isCancelled());
            assertFalse(view.permitsScreenClose());assertEquals("SEARCH",field(view,"page").toString());
            assertSame(field(view,"searchHistory"),host.screen().getFocused());
        } finally {neverRelease.countDown();}
    }

    @Test void historyPagerAndCancelledReadCannotActivate() throws Exception {
        var release=new java.util.concurrent.CountDownLatch(1);
        try(var player=session(release)) {
            var registry=com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.forPlayer(player).orElseThrow();
            var handle=registry.startSearch("qq","cancelled query",1);handle.cancel();
            var view=new OnlineMusicViewImpl(player,false);view.layout(new Host());page(view,"HISTORY");
            assertFalse(((MusicTextControl)field(view,"historyPrevious")).active);
            assertFalse(((MusicTextControl)field(view,"historyNext")).active);
            var row=(MusicTextControl)((List<?>)field(view,"historyRows")).get(0);
            assertFalse(row.active);row.onPress();
            call(view,"restoreOperation",new Class<?>[]{long.class},handle.id());
            assertEquals("HISTORY",field(view,"page").toString());
            assertNull(field(view,"pending"));
        } finally {release.countDown();}
    }

    @Test void unknownWriteChecksLikesOnceAndSignedOutRouteHonorsAuthGate() throws Exception {
        var release=new java.util.concurrent.CountDownLatch(0);
        var writes=new java.util.concurrent.atomic.AtomicInteger();
        try(var player=session(release,new java.io.IOException("unknown transport"),writes)) {
            var registry=com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.forPlayer(player).orElseThrow();
            player.credentials("qq","fixture");
            var unknown=registry.startLike(A,false);
            assertThrows(java.util.concurrent.ExecutionException.class,()->unknown.observe().get(3,java.util.concurrent.TimeUnit.SECONDS));
            for(int i=0;i<100&&unknown.snapshot().state().name().equals("RUNNING");i++)Thread.sleep(5);
            assertEquals("UNKNOWN",unknown.snapshot().error().name());
            assertEquals(1,writes.get());
            var view=new OnlineMusicViewImpl(player,false);view.layout(new Host());page(view,"HISTORY");
            call(view,"restoreOperation",new Class<?>[]{long.class},unknown.id());
            assertEquals("qq",field(view,"librarySource"));assertEquals("LIKED",field(view,"library").toString());
            assertNotNull(field(view,"pendingLibrary"));assertEquals(1,writes.get());
            assertFalse(view.permitsScreenClose());
            player.credentials("qq","");
            var signedOut=registry.startLike(A,true);
            assertThrows(java.util.concurrent.ExecutionException.class,()->signedOut.observe().get(3,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("LOGIN_REQUIRED",signedOut.snapshot().error().name());
            call(view,"restoreOperation",new Class<?>[]{long.class},signedOut.id());
            assertEquals("qq",field(view,"librarySource"));assertEquals("LIKED",field(view,"library").toString());
            assertNull(field(view,"pendingLibrary"));assertEquals(1,writes.get());
            assertTrue(((MusicTextControl)field(view,"libraryLogin")).visible);
            ((MusicTextControl)field(view,"libraryLogin")).onPress();
            assertEquals("ACCOUNTS",field(view,"page").toString());
            assertEquals("qq",field(view,"accountSource"));
            assertFalse(view.permitsScreenClose());
            assertEquals("LIBRARY",field(view,"page").toString());
            assertEquals("qq",field(view,"librarySource"));
            assertEquals("LIKED",field(view,"library").toString());
            assertNull(field(view,"pendingLibrary"));
            ((MusicTextControl)field(view,"back")).onPress();
            assertEquals("HISTORY",field(view,"page").toString());
            assertTrue(registry.currentLogin("qq").isEmpty());
        }
    }
    @Test void wideHistoryLabelFitsChineseTranslationsAndCompactEntryKeepsSearchTarget() throws Exception {
        try(var player=session(new java.util.concurrent.CountDownLatch(0))) {
            var view=new OnlineMusicViewImpl(player,false);var host=new Host();view.layout(host);
            var entry=(MusicTextControl)field(view,"searchHistory");
            assertTrue(entry.getWidth()>=64);
            for(String locale:List.of("zh_cn","zh_tw")) {
                try(var stream=getClass().getResourceAsStream("/assets/sh_better_ui/lang/"+locale+".json")) {
                    assertNotNull(stream);
                    var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    String label=json.get("sh_better_ui.online_music.operations.short_title").getAsString();
                    assertTrue(host.font().width(label)+18<=entry.getWidth());
                }
            }
            var fullSearch=(MusicTextControl)field(view,"searchButton");
            assertTrue(fullSearch.getX()+fullSearch.getWidth()<=entry.getX());
            host.width=208;host.height=240;view.layout(host);
            var compact=(MusicTextControl)field(view,"searchHistory");
            assertEquals(24,compact.getWidth());
            assertEquals(24,((MusicTextControl)field(view,"searchButton")).getWidth());
            assertTrue(((net.minecraft.client.gui.components.AbstractWidget)field(view,"queryField")).getWidth()>0);
        }
    }

    @Test void ordinaryLibraryDrillDownKeepsItsOwnBackDestination() throws Exception {
        try(var player=session(new java.util.concurrent.CountDownLatch(0))) {
            player.credentials("qq","fixture");
            var registry=com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.forPlayer(player).orElseThrow();
            var liked=registry.startLiked("qq",1);
            var view=new OnlineMusicViewImpl(player,false);view.layout(new Host());page(view,"HISTORY");
            call(view,"restoreOperation",new Class<?>[]{long.class},liked.id());
            var switchGroup=(java.util.function.IntConsumer)field(field(view,"libraryGroups"),"onChange");
            switchGroup.accept(0);
            ((java.util.concurrent.CompletableFuture<?>)field(view,"pendingPlaylists")).get(3,java.util.concurrent.TimeUnit.SECONDS);
            call(view,"pollOperations",new Class<?>[0]);
            var row=(MusicTextControl)((List<?>)field(view,"libraryRows")).get(0);row.onPress();
            assertEquals("TRACKS",field(view,"library").toString());
            assertFalse(view.permitsScreenClose());
            assertEquals("LIBRARY",field(view,"page").toString());
            assertEquals("PLAYLISTS",field(view,"library").toString());
        }
    }

    @Test void historyFocusNeverSilentlyChangesTaskAndAccountEscapeReturnsToCard() throws Exception {
        try(var player=session(new java.util.concurrent.CountDownLatch(0))) {
            var registry=com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations.forPlayer(player).orElseThrow();
            var first=registry.startSearch("qq","first",1);
            var view=new OnlineMusicViewImpl(player,false); var host=new Host();view.layout(host);page(view,"HISTORY");
            var row=(MusicTextControl)((List<?>)field(view,"historyRows")).get(0);
            var bindings=(Map<?,?>)field(view,"historyBindings");
            assertEquals(first.id(),bindings.get(row));
            host.screen().setFocused(row);
            registry.startSearch("qq","second",1);call(view,"updateHistory",new Class<?>[0]);
            assertSame(field(view,"back"),host.screen().getFocused());
            page(view,"ACCOUNTS");
            var card=(MusicTextControl)((Map<?,?>)field(view,"accounts")).get("qq");
            card.onPress();assertTrue((boolean)field(view,"accountDetail"));
            assertFalse(view.permitsScreenClose());assertFalse((boolean)field(view,"accountDetail"));
            assertSame(card,host.screen().getFocused());
        }
    }


    @Test void spotifyHandoffFitsMinimumSearchAndDoesNotMasqueradeAsProvider() throws Exception {
        try (var player = session(new java.util.concurrent.CountDownLatch(0))) {
            var host = new Host(); host.width=208; host.height=240;
            var view = new OnlineMusicViewImpl(player, false); view.layout(host);
            var spotify = view.spotifySearchControl();
            var nativeSearch = (MusicTextControl) field(view,"searchButton");
            var history = (MusicTextControl) field(view,"searchHistory");
            assertNotNull(spotify);
            assertTrue(spotify.active);
            assertTrue(nativeSearch.getX()+nativeSearch.getWidth() <= spotify.getX());
            assertTrue(spotify.getX()+spotify.getWidth() <= history.getX());
            assertFalse(player.sources().contains("spotify"));
            queuePage(view);
            assertNull(view.spotifySearchControl());
        }
    }

    @Test void spotifyHandoffAndQueryRemainAvailableWithoutNativeProviders() throws Exception {
        var f = new Fixture(); f.providers = List.of();
        f.view.layout(new Host());
        assertTrue(f.view.spotifySearchControl().active);
        assertFalse(((MusicTextControl) field(f.view,"searchButton")).active);
        var query = (com.dishanhai.sh_better_ui.client.gui.scaled.UiTextField) field(f.view,"queryField");
        assertTrue(query.active);
        query.setValue("browser-only search");
        assertEquals("browser-only search", query.getValue());
        call(f.view, "queryChanged", new Class<?>[]{String.class}, query.getValue());
        assertEquals(0L, field(f.view,"suggestAfter"));
    }

    @Test void remountRestoresSpotifySearchDraftAndMatchingCompletedResults() throws Exception {
        var f = new Fixture();
        var bookmark = new OnlineMusicViewImpl.ExternalSearchBookmark(f.player, "qq", "edited draft",
                "completed query", 2, 0, List.of(A), "results", 0, null);
        var same = new OnlineMusicViewImpl(f.player, false);
        same.layout(new Host());
        assertSame(same.spotifySearchControl(), same.restoreExternalSearch(bookmark));
        assertEquals("edited draft", field(same,"query"));
        assertEquals("qq", field(same,"source"));
        assertEquals("completed query", field(same,"submittedQuery"));
        assertEquals(2, field(same,"remotePage"));
        assertEquals(List.of(A), field(same,"results"));

        var other = new Fixture().view;
        other.layout(new Host());
        other.restoreExternalSearch(bookmark);
        assertEquals("edited draft", field(other,"query"));
        assertEquals(List.of(), field(other,"results"));
    }
    @Test void artistEscCancelsAndReturnsToOriginalArtworkStage() throws Exception {
        var f=new Fixture();var host=new Host();f.view.layout(host);
        var stageField=f.view.getClass().getDeclaredField("stagePage");stageField.setAccessible(true);
        Object artwork=Arrays.stream(stageField.getType().getEnumConstants()).filter(v->v.toString().equals("ARTWORK")).findFirst().orElseThrow();
        call(f.view,"enterImmersive",new Class<?>[]{stageField.getType()},artwork);
        call(f.view,"openArtist",new Class<?>[]{OnlineArtist.class},new OnlineArtist("qq","1","Artist"));
        assertEquals("ARTIST",field(f.view,"page").toString());assertEquals(1,f.artistRequests.size());
        assertFalse(f.view.permitsScreenClose());assertTrue(f.artistRequests.get(0).isCancelled());
        assertEquals(true,field(f.view,"immersive"));assertEquals("ARTWORK",field(f.view,"stagePage").toString());
        assertFalse(f.view.permitsScreenClose());
        assertEquals(false,field(f.view,"immersive"));assertEquals("SEARCH",field(f.view,"page").toString());
    }
    @Test void artistLeavingForLyricsCancelsAndSmallPanelRetainsSongRow() throws Exception {
        var f=new Fixture();var host=new Host();host.width=208;host.height=240;f.view.layout(host);
        call(f.view,"openArtist",new Class<?>[]{OnlineArtist.class},new OnlineArtist("qq","1","Artist"));
        Object panel=field(f.view,"artistDetail");assertFalse(((List<?>)field(panel,"rows")).isEmpty());
        call(f.view,"enterImmersive",new Class<?>[]{});
        assertTrue(f.artistRequests.get(0).isCancelled());
        assertFalse((Boolean)field(panel,"visible"));
        call(f.view,"openArtist",new Class<?>[]{OnlineArtist.class},new OnlineArtist("qq","2","Other"));
        assertFalse(f.view.permitsScreenClose());
        assertFalse(f.view.permitsScreenClose());
        assertEquals("SEARCH",field(f.view,"page").toString());
    }

}
