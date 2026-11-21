package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

/** Drives the actual view's async handlers without a Minecraft render context or real credentials. */
class OnlineMusicAccountViewTest {
    private static class Uncancellable<T> extends CompletableFuture<T> {
        @Override public boolean cancel(boolean ignored) { return false; }
    }
    private static final OnlineTrack TRACK = new OnlineTrack("qq", "1", "1", "Track", "Artist", "", 1000);
    private static class Fixture {
        final Map<String, CompletableFuture<OnlineMusicAccount>> accounts = new HashMap<>();
        final Map<String,CompletableFuture<List<OnlinePlaylist>>> playlists=new HashMap<>();
        final Map<String,CompletableFuture<List<OnlineTrack>>> liked=new HashMap<>();
        CompletableFuture<List<OnlineTrack>> playlistTracks=new Uncancellable<>();
        String lastLibrarySource;
        OnlineTrack played;
        int remotePlaylistLoads;
        List<String> providers=List.of("netease","qq");
        CompletableFuture<Void> likes = new Uncancellable<>();
        final Map<String, Boolean> saved = new HashMap<>();
        final Set<String> signedIn = new HashSet<>(List.of("netease", "qq"));
        final OnlineMusicPlayer player = (OnlineMusicPlayer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{OnlineMusicPlayer.class}, (proxy, method, args) -> switch (method.getName()) {
            case "authenticated" -> signedIn.contains((String)args[0]);
            case "supportsLikes" -> true;
            case "supportsVolume" -> false;
            case "account" -> accounts.computeIfAbsent((String)args[0], key -> new Uncancellable<>());
            case "like" -> likes;
            case "playlists" -> {lastLibrarySource=(String)args[0];yield playlists.computeIfAbsent(lastLibrarySource,k->new Uncancellable<>());}
            case "liked" -> {lastLibrarySource=(String)args[0];yield liked.computeIfAbsent(lastLibrarySource,k->new Uncancellable<>());}
            case "playlist" -> {remotePlaylistLoads++;yield playlistTracks;}
            case "play" -> {played=(OnlineTrack)args[0];yield null;}
            case "remembersCredentials" -> saved.getOrDefault((String)args[0],false);
            case "rememberCredentials" -> { saved.put((String)args[0],(Boolean)args[1]); yield null; }
            case "snapshot" -> new OnlineMusicPlayer.Snapshot(null, OnlineMusicPlayer.State.IDLE, 0, 0, OnlineMusicPlayer.LyricsState.EMPTY,
                com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline.empty(), OnlineMusicPlayer.Failure.NONE, false);
            case "sources" -> providers;
            default -> null;
        });
        final OnlineMusicViewImpl view = new OnlineMusicViewImpl(player);
    }
    private static Object read(Object view, String name) throws Exception { var f=view.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(view); }
    private static void write(Object view,String name,Object value) throws Exception { var f=view.getClass().getDeclaredField(name);f.setAccessible(true);f.set(view,value); }
    private static void call(Object view, String name, Class<?>[] types, Object... args) throws Exception {var m=view.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(view,args);}
    private static void call(Object view,String name) throws Exception {call(view,name,new Class<?>[0]);}
    private static void open(Fixture f,String source) throws Exception {call(f.view,"openAccount",new Class<?>[]{String.class},source);}
    @Test void lateAccountFailureCannotLeakIntoOverviewAfterBack() throws Exception {
        var f=new Fixture();open(f,"qq");assertEquals("account.loading",read(f.view,"accountState"));
        call(f.view,"leaveAccountDetail");f.accounts.get("qq").completeExceptionally(new RuntimeException("late"));call(f.view,"pollOperations");
        assertEquals("",read(f.view,"accountState"));assertNull(read(f.view,"pendingAccount"));assertEquals(false,read(f.view,"accountDetail"));
    }
    @Test void switchingProviderIgnoresOldIdentityAndKeepsNewRequest() throws Exception {
        var f=new Fixture();open(f,"qq");var old=f.accounts.get("qq");open(f,"netease");
        old.complete(new OnlineMusicAccount("qq","old","Wrong account"));call(f.view,"pollOperations");
        assertEquals("",read(f.view,"accountName"));
        f.accounts.get("netease").complete(new OnlineMusicAccount("netease","new","Correct account"));call(f.view,"pollOperations");
        assertEquals("Correct account",read(f.view,"accountName"));assertEquals("",read(f.view,"accountState"));
    }
    @Test void failedProfileHasExplicitRetryWithoutChangingAuthentication() throws Exception {
        var f=new Fixture();open(f,"qq");f.accounts.get("qq").completeExceptionally(new RuntimeException());call(f.view,"pollOperations");
        assertEquals("account.failed",read(f.view,"accountState"));assertTrue(f.player.authenticated("qq"));
        f.accounts.put("qq",CompletableFuture.completedFuture(new OnlineMusicAccount("qq","ok","Recovered")));
        call(f.view,"loadAccount");call(f.view,"pollOperations");assertEquals("Recovered",read(f.view,"accountName"));assertEquals("",read(f.view,"accountState"));
    }
    @Test void expiredProfileRequiresLoginRatherThanGenericRetry() throws Exception {
        var f=new Fixture();open(f,"qq");f.accounts.get("qq").completeExceptionally(new OnlineMusicSource.LoginRequired());call(f.view,"pollOperations");
        assertEquals("library.login_required",read(f.view,"accountState"));
    }
    @Test void lateLikeCannotMarkDifferentAccountOrOverview() throws Exception {
        var f=new Fixture();open(f,"qq");call(f.view,"changeLike",new Class<?>[]{OnlineTrack.class,boolean.class},TRACK,true);
        var old=f.likes;open(f,"netease");old.complete(null);call(f.view,"pollOperations");
        assertEquals("",read(f.view,"likeState"));assertTrue(((Map<?,?>)read(f.view,"confirmedLikes")).isEmpty());
    }
    @Test void onlySuccessfulWritesDeclareKnownLikeStateAndAccountChangeClearsIt() throws Exception {
        var f=new Fixture();call(f.view,"changeLike",new Class<?>[]{OnlineTrack.class,boolean.class},TRACK,true);f.likes.complete(null);call(f.view,"pollOperations");
        assertEquals(true,((Map<?,?>)read(f.view,"confirmedLikes")).get("qq:1"));
        f.likes=new Uncancellable<>();call(f.view,"changeLike",new Class<?>[]{OnlineTrack.class,boolean.class},TRACK,false);f.likes.completeExceptionally(new RuntimeException());call(f.view,"pollOperations");
        assertNull(((Map<?,?>)read(f.view,"confirmedLikes")).get("qq:1"));
        open(f,"qq");assertTrue(((Map<?,?>)read(f.view,"confirmedLikes")).isEmpty());
    }
    @SuppressWarnings("unchecked")
    @Test void cancelledUncancellableWriteInvalidatesPreviousOppositeState() throws Exception {
        var f=new Fixture();var known=(Map<String,Boolean>)read(f.view,"confirmedLikes");known.put("qq:1",false);
        call(f.view,"changeLike",new Class<?>[]{OnlineTrack.class,boolean.class},TRACK,true);
        var request=f.likes;call(f.view,"cancelLike");assertNull(known.get("qq:1"));
        request.complete(null);call(f.view,"pollOperations");assertNull(known.get("qq:1"));assertEquals("",read(f.view,"likeState"));
    }
    @Test void likedListConfirmsOnlyReturnedTracksNotUnknownTracks() throws Exception {
        var f=new Fixture();var library=f.view.getClass().getDeclaredField("library");library.setAccessible(true);
        Object liked=Arrays.stream(library.getType().getEnumConstants()).filter(v->v.toString().equals("LIKED")).findFirst().orElseThrow();library.set(f.view,liked);
        write(f.view,"pendingLibrary",CompletableFuture.completedFuture(List.of(TRACK)));call(f.view,"pollOperations");
        var states=(Map<?,?>)read(f.view,"confirmedLikes");assertEquals(true,states.get("qq:1"));assertNull(states.get("qq:unknown"));
    }

    private static class Host implements com.dishanhai.sh_better_ui.client.api.preview.PreviewContext {
        int width=800,height=600;
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
    @Test void libraryDoesNotInventProvidersForEmbeddedPlayers() throws Exception {
        for(var ids:List.of(List.<String>of(),List.of("qq"))) {
            var f=new Fixture();f.providers=ids;f.view.layout(new Host());call(f.view,"enterLibrary");
            if(ids.isEmpty()){
                assertNull(read(f.view,"librarySources"));
                assertEquals("view.unavailable",read(f.view,"libraryState"));assertTrue(f.playlists.isEmpty());
                assertFalse(((net.minecraft.client.gui.components.AbstractWidget)read(f.view,"libraryLogin")).visible);
            } else {
                assertEquals(List.of("qq","listen1"),read(f.view,"librarySourceIds"));
                assertEquals("qq",f.lastLibrarySource);
            }
        }
    }

    private static void selectListen1(OnlineMusicViewImpl view) throws Exception {
        var tabs=(com.dishanhai.sh_better_ui.kernel.api.music.control.MusicSegmentedControl)read(view,"librarySources");
        assertTrue(tabs.visible && tabs.active);
        assertEquals(3,tabs.choiceCount());
        // Exercise the actual selection callback without a sound device.
        ((java.util.function.IntConsumer)read(tabs,"onChange")).accept(2);
        assertEquals(2,tabs.selectedIndex());
        assertEquals("listen1",read(view,"librarySource"));
    }

    @Test void listen1MinimumViewportImportsAndRemountsWithoutAListen1NetworkSource(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var f=new Fixture();
        var imports=new com.dishanhai.sh_better_ui.kernel.internal.music.Listen1Backup.Session();
        var view=new OnlineMusicViewImpl(f.player,false,imports);
        var compact=new Host();compact.width=208;compact.height=240;
        view.layout(compact);call(view,"enterLibrary");selectListen1(view);
        var path=(com.dishanhai.sh_better_ui.client.gui.scaled.UiTextField)read(view,"listen1Path");
        var action=(com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl)read(view,"listen1ImportButton");
        assertTrue(path.visible && path.active);assertTrue(action.visible);assertFalse(action.active);
        assertTrue(path.getX()+path.getWidth()<action.getX());assertTrue(action.getX()+action.getWidth()<compact.width);
        assertFalse(((net.minecraft.client.gui.components.AbstractWidget)read(view,"libraryLogin")).visible);

        var json=java.nio.file.Files.writeString(directory.resolve("listen1 backup.json"),"""
                {"playerlists":["myplaylist_1"],"myplaylist_1":{"info":{"id":"myplaylist_1","title":"Mix"},"tracks":[
                    {"id":"qqtrack_067abc","source":"qq","title":"QQ track"},
                    {"id":"kwtrack_1","source":"kuwo","title":"Unavailable"}]}}
                """);
        path.setValue("\""+json+"\"");
        // The widget normally writes through onChange; set the value under test before invoking Import.
        write(view,"listen1File",path.getValue());call(view,"updateListen1Editor");
        assertTrue(action.active);
        action.onPress();
        ((CompletableFuture<?>)read(view,"pendingListen1")).get(5,java.util.concurrent.TimeUnit.SECONDS);
        call(view,"pollOperations");
        assertFalse(path.visible);
        assertEquals(1,imports.imported().supportedTracks());assertEquals(1,imports.imported().skippedTracks());
        assertEquals(Set.of("netease"),f.playlists.keySet());
        assertNull(f.played); // Import alone must not replace or start playback.

        view.suspend();
        var reopened=new OnlineMusicViewImpl(f.player,false,imports);
        reopened.layout(compact);call(reopened,"enterLibrary");selectListen1(reopened);
        var row=(com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl)((List<?>)read(reopened,"libraryRows")).getFirst();
        assertTrue(row.visible);row.onPress(); // Open playlist.
        assertEquals(0,f.remotePlaylistLoads);
        row.onPress(); // Play its first track through the regular queue.
        assertNotNull(f.played);
        assertEquals("qq",f.played.source());assertEquals("067abc",f.played.id());
        assertEquals(Set.of("netease"),f.playlists.keySet());
        reopened.suspend();
    }

    @Test void listen1CancellationAndFailedReplacementPreserveThePreviousImport() throws Exception {
        var f=new Fixture();
        var imports=new com.dishanhai.sh_better_ui.kernel.internal.music.Listen1Backup.Session();
        var list=new OnlinePlaylist("listen1","myplaylist_1","Keep this",1);
        var old=new com.dishanhai.sh_better_ui.kernel.internal.music.Listen1Backup.Imported(List.of(list),Map.of(list.id(),List.of(TRACK)),1,0);
        imports.replace(old);
        var view=new OnlineMusicViewImpl(f.player,false,imports);
        view.layout(new Host());call(view,"enterLibrary");selectListen1(view);
        var replace=(com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl)read(view,"libraryRefresh");
        replace.onPress();
        var late=new Uncancellable<com.dishanhai.sh_better_ui.kernel.internal.music.Listen1Backup.Imported>();
        write(view,"pendingListen1",late);
        assertFalse(view.permitsScreenClose()); // Esc dismisses the editor, not the whole screen.
        late.complete(new com.dishanhai.sh_better_ui.kernel.internal.music.Listen1Backup.Imported(List.of(),Map.of(),0,0));
        call(view,"pollOperations");
        assertSame(old,imports.imported());assertTrue(replace.visible);

        replace.onPress();
        write(view,"pendingListen1",CompletableFuture.completedFuture(null)); // File failed validation.
        call(view,"pollOperations");
        assertSame(old,imports.imported());
        assertFalse(view.permitsScreenClose());
        assertEquals(List.of(list),read(view,"playlists"));

        replace.onPress();
        write(view,"pendingListen1",CompletableFuture.completedFuture(
                new com.dishanhai.sh_better_ui.kernel.internal.music.Listen1Backup.Imported(List.of(),Map.of(),0,0)));
        call(view,"pollOperations");
        assertSame(old,imports.imported());
        assertEquals(List.of(list),read(view,"playlists"));
        assertTrue(((net.minecraft.network.chat.Component)read(view,"listen1Status"))
                .getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents message
                && message.getKey().equals("sh_better_ui.online_music.listen1.empty_backup"));
        view.suspend();
    }

    @Test void libraryAuthRoundTripPreservesCompletedListAndGuestControlsStayClearOfFooter() throws Exception {
        var f=new Fixture();f.view.layout(new Host());call(f.view,"enterLibrary");
        var playlists = new ArrayList<OnlinePlaylist>();
        for (int index = 0; index < 40; index++) {
            playlists.add(new OnlinePlaylist("netease", "saved_" + index, "Saved " + index, 1));
        }
        f.playlists.get("netease").complete(playlists);call(f.view,"pollOperations");write(f.view,"libraryPage",3);
        var scroll = (OnlineMusicScrollRegion) read(f.view, "libraryScroll");
        scroll.setFocused(true);
        assertTrue(scroll.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, 0, 0));
        double savedOffset = scroll.viewportOffset();
        assertTrue(savedOffset > 0);
        var pageField=f.view.getClass().getDeclaredField("page");pageField.setAccessible(true);
        Object accounts=Arrays.stream(pageField.getType().getEnumConstants()).filter(v->v.toString().equals("ACCOUNTS")).findFirst().orElseThrow();
        call(f.view,"show",new Class<?>[]{pageField.getType()},accounts);open(f,"qq");call(f.view,"enterLibrary");
        assertEquals(3,read(f.view,"libraryPage"));assertEquals(playlists,read(f.view,"playlists"));
        assertEquals(savedOffset, scroll.viewportOffset());
        for(int[] size:new int[][]{{208,240},{392,269},{560,264}}){
            var guest=new Fixture();guest.signedIn.clear();var host=new Host();host.width=size[0];host.height=size[1];guest.view.layout(host);call(guest.view,"enterLibrary");
            var login=(com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl)read(guest.view,"libraryLogin");
            var layout=(com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLayout)read(guest.view,"layout");
            assertTrue(login.visible);assertTrue(login.getY()+login.getHeight()<=layout.pager().y());
            var guestScroll = (OnlineMusicScrollRegion) read(guest.view, "libraryScroll");
            assertFalse(guestScroll.active);
            assertFalse(((net.minecraft.client.gui.components.AbstractWidget)read(guest.view,"libraryContinuationRetry")).visible);
            for(Object item:(List<?>)read(guest.view,"navigation")){var w=(net.minecraft.client.gui.components.AbstractWidget)item;assertTrue(w.getY()+w.getHeight()<=layout.navigation().bottom());}
        }
    }

    @Test void leavingAuthorizationThroughLibraryTabConsumesItsOneShotReturn() throws Exception {
        var f=new Fixture();f.signedIn.clear();f.view.layout(new Host());call(f.view,"enterLibrary");
        ((com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl)read(f.view,"libraryLogin")).onPress();
        assertEquals(true,read(f.view,"libraryLoginReturn"));
        call(f.view,"enterLibrary");assertEquals(false,read(f.view,"libraryLoginReturn"));
        var pageField=f.view.getClass().getDeclaredField("page");pageField.setAccessible(true);
        Object accounts=Arrays.stream(pageField.getType().getEnumConstants()).filter(v->v.toString().equals("ACCOUNTS")).findFirst().orElseThrow();
        call(f.view,"show",new Class<?>[]{pageField.getType()},accounts);open(f,"netease");call(f.view,"updateVisibility");
        ((com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl)read(f.view,"back")).onPress();
        assertEquals("ACCOUNTS",read(f.view,"page").toString());assertEquals(false,read(f.view,"accountDetail"));
        assertEquals(false,read(f.view,"libraryLoginReturn"));
    }

    @Test void libraryGuestGoesToMatchingAuthorizationThenReturnsToItsGroup() throws Exception {
        var f=new Fixture();f.signedIn.clear();f.view.layout(new Host());call(f.view,"enterLibrary");
        assertEquals("LIBRARY",read(f.view,"page").toString());assertEquals(LibraryAccessFeedback.SIGN_IN,read(f.view,"libraryState"));assertTrue(f.playlists.isEmpty());
        var groups=(com.dishanhai.sh_better_ui.kernel.api.music.control.MusicSegmentedControl)read(f.view,"libraryGroups");
        ((java.util.function.IntConsumer)read(groups,"onChange")).accept(1);
        ((com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl)read(f.view,"libraryLogin")).onPress();
        assertEquals("ACCOUNTS",read(f.view,"page").toString());assertEquals("netease",read(f.view,"accountSource"));
        f.signedIn.add("netease");
        ((com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl)read(f.view,"back")).onPress();
        assertEquals("LIBRARY",read(f.view,"page").toString());assertEquals("LIKED",read(f.view,"library").toString());assertEquals("netease",f.lastLibrarySource);assertFalse(f.liked.isEmpty());
    }
    @Test void switchingLibraryProviderDiscardsLateResultsAndDoesNotChangeAuthorizationSource() throws Exception {
        var f=new Fixture();f.view.layout(new Host());call(f.view,"enterLibrary");var old=f.playlists.get("netease");
        var sources=(com.dishanhai.sh_better_ui.kernel.api.music.control.MusicSegmentedControl)read(f.view,"librarySources");
        ((java.util.function.IntConsumer)read(sources,"onChange")).accept(1);
        old.complete(List.of(new OnlinePlaylist("netease","old","Old",1)));call(f.view,"pollOperations");
        assertEquals("qq",read(f.view,"librarySource"));assertEquals("netease",read(f.view,"accountSource"));assertTrue(((List<?>)read(f.view,"playlists")).isEmpty());
        assertTrue(((com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl)read(f.view,"libraryRefresh")).active);
        f.playlists.get("qq").completeExceptionally(new OnlineMusicSource.LoginRequired());call(f.view,"pollOperations");
        assertEquals("library.login_required",read(f.view,"libraryState"));
        assertTrue(((com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl)read(f.view,"libraryLogin")).visible);
    }
    @Test void playlistDetailBackRestoresLibraryPageRatherThanAccount() throws Exception {
        var f=new Fixture();f.view.layout(new Host());call(f.view,"enterLibrary");
        var list=new OnlinePlaylist("netease","1","Real playlist",1);f.playlists.get("netease").complete(List.of(list));call(f.view,"pollOperations");
        write(f.view,"libraryPage",3);call(f.view,"loadPlaylist",new Class<?>[]{OnlinePlaylist.class,int.class},list,1);
        assertEquals("TRACKS",read(f.view,"library").toString());
        ((com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl)read(f.view,"back")).onPress();
        assertEquals("LIBRARY",read(f.view,"page").toString());assertEquals("PLAYLISTS",read(f.view,"library").toString());assertEquals(3,read(f.view,"libraryPage"));assertEquals(List.of(list),read(f.view,"playlists"));
        f.playlistTracks.complete(List.of(TRACK));call(f.view,"pollOperations");assertTrue(((List<?>)read(f.view,"libraryTracks")).isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test void realLayoutAccountButtonsToggleSavingAndBackDiscardsLateProfile() throws Exception {
        var f=new Fixture();f.signedIn.remove("netease");f.view.layout(new Host());
        var pageField=f.view.getClass().getDeclaredField("page");pageField.setAccessible(true);
        var page=Arrays.stream(pageField.getType().getEnumConstants()).filter(v->v.toString().equals("ACCOUNTS")).findFirst().orElseThrow();
        call(f.view,"show",new Class<?>[]{pageField.getType()},page);
        var cards=(Map<String,com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl>)read(f.view,"accounts");
        cards.get("qq").onPress();
        var remember=(com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl)read(f.view,"remember");
        assertTrue(remember.visible);remember.onPress();assertEquals(true,f.saved.get("qq"));
        assertEquals("ACCOUNTS",read(f.view,"page").toString());
        ((com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl)read(f.view,"back")).onPress();
        f.accounts.get("qq").completeExceptionally(new RuntimeException("late"));call(f.view,"pollOperations");
        assertEquals(false,read(f.view,"accountDetail"));assertEquals("",read(f.view,"accountState"));
    }
}
