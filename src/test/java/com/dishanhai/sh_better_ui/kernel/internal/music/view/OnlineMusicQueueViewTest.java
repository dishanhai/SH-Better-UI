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
        OnlineMusicQueue persistent;
        List<String> providers=List.of("netease","qq");
        int paused,closed,seeks;
        OnlineMusicPlayer.Snapshot snapshotOverride;
        final OnlineMusicPlayer player = (OnlineMusicPlayer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{OnlineMusicPlayer.class}, (proxy, method, args) -> switch(method.getName()) {
            case "queue" -> persistent;
            case "seek" -> {seeks++;yield null;}
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
}
