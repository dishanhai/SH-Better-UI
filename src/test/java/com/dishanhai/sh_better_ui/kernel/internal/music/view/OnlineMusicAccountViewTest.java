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
        CompletableFuture<Void> likes = new Uncancellable<>();
        final Map<String, Boolean> saved = new HashMap<>();
        final Set<String> signedIn = new HashSet<>(List.of("netease", "qq"));
        final OnlineMusicPlayer player = (OnlineMusicPlayer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{OnlineMusicPlayer.class}, (proxy, method, args) -> switch (method.getName()) {
            case "authenticated" -> signedIn.contains((String)args[0]);
            case "supportsLikes" -> true;
            case "account" -> accounts.computeIfAbsent((String)args[0], key -> new Uncancellable<>());
            case "like" -> likes;
            case "remembersCredentials" -> saved.getOrDefault((String)args[0],false);
            case "rememberCredentials" -> { saved.put((String)args[0],(Boolean)args[1]); yield null; }
            case "snapshot" -> new OnlineMusicPlayer.Snapshot(null, OnlineMusicPlayer.State.IDLE, 0, 0, OnlineMusicPlayer.LyricsState.EMPTY,
                com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline.empty(), OnlineMusicPlayer.Failure.NONE, false);
            case "sources" -> List.of("netease", "qq");
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
        @Override public com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds viewport() { return new com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds(0,0,800,600); }
        @Override public com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds toolbar() { return viewport(); }
        @Override public net.minecraft.client.gui.Font font() { return font; }
        @Override public com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences preferences() { return com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences.DEFAULT; }
        @Override public com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost overlays() { return null; }
        @Override public com.dishanhai.sh_better_ui.client.api.UiApiContext context() { return null; }
        @Override public boolean interactive() { return true; }
        @Override public <T extends net.minecraft.client.gui.components.AbstractWidget> T add(T widget) { widgets.add(widget);return widget; }
        @Override public <T extends net.minecraft.client.gui.components.AbstractWidget> T addOverlay(T widget) { return add(widget); }
    }
    @SuppressWarnings("unchecked")
    @Test void realLayoutAccountButtonsToggleSavingAndBackDiscardsLateProfile() throws Exception {
        var f=new Fixture();f.signedIn.remove("netease");f.view.layout(new Host());
        var pageField=f.view.getClass().getDeclaredField("page");pageField.setAccessible(true);
        var page=Arrays.stream(pageField.getType().getEnumConstants()).filter(v->v.toString().equals("ACCOUNTS")).findFirst().orElseThrow();
        call(f.view,"show",new Class<?>[]{pageField.getType()},page);
        var quick=(Map<String,List<com.dishanhai.sh_better_ui.client.gui.scaled.UiButton>>)read(f.view,"accountLibraries");
        assertTrue(quick.get("qq").get(0).visible);assertTrue(quick.get("qq").get(0).active);
        assertFalse(quick.get("netease").get(0).active);
        var cards=(Map<String,com.dishanhai.sh_better_ui.client.gui.scaled.UiButton>)read(f.view,"accounts");
        cards.get("qq").onPress();
        var remember=(com.dishanhai.sh_better_ui.client.gui.scaled.UiButton)read(f.view,"remember");
        assertTrue(remember.visible);remember.onPress();assertEquals(true,f.saved.get("qq"));
        assertTrue(((com.dishanhai.sh_better_ui.client.gui.scaled.UiButton)read(f.view,"openLiked")).visible);
        ((com.dishanhai.sh_better_ui.client.gui.scaled.UiButton)read(f.view,"back")).onPress();
        f.accounts.get("qq").completeExceptionally(new RuntimeException("late"));call(f.view,"pollOperations");
        assertEquals(false,read(f.view,"accountDetail"));assertEquals("",read(f.view,"accountState"));
    }
}
