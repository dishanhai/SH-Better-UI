package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.*;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class OnlineMusicScreenTest {
    private com.dishanhai.sh_better_ui.client.api.NativeTestClient clientFixture;
    @org.junit.jupiter.api.BeforeEach void installNativeClient() throws Exception {
        clientFixture = com.dishanhai.sh_better_ui.client.api.NativeTestClient.install();
    }
    @org.junit.jupiter.api.AfterEach void restoreNativeClient() throws Exception {
        if (clientFixture != null) clientFixture.close();
    }

    private static final class View implements OnlineMusicView {
        int layouts, closes;
        boolean fail;
        @Override public OnlineMusicPlayer player() { return null; }
        @Override public void layout(PreviewContext context) { layouts++; if (fail) throw new IllegalStateException("layout failed"); }
        @Override public void render(UiFrame frame) {}
        @Override public void close() { closes++; }
    }
    @Test void userApiReturnsNativeMusicScreenWithoutOpeningServiceOrDeveloperGallery() {
        var screen=ShBetterUiApi.settings().onlineMusicScreen(null);
        assertInstanceOf(UiScreen.class,screen);
        assertEquals("sh_better_ui.online_music.view.brand",
                assertInstanceOf(TranslatableContents.class,screen.getTitle().getContents()).getKey());
        assertFalse(screen.isPauseScreen());
    }
    @Test void constructionIsLazyAndRetainsExplicitParentAndFocusSupplier() throws Exception {
        var calls=new AtomicInteger();Screen parent=new Screen(Component.empty()) {};
        Supplier<GuiEventListener> focus=()->null;
        var screen=OnlineMusicScreen.create(parent,focus,()->{calls.incrementAndGet();return new View();});
        assertEquals(0,calls.get());
        for(var item:new Object[][]{{"parent",parent},{"returnFocus",focus}}) {
            var field=UiScreen.class.getDeclaredField((String)item[0]);field.setAccessible(true);
            assertSame(item[1],field.get(screen));
        }
    }
    @Test void resizeReusesViewAndRepeatedRemovalClosesExactlyOnce() {
        var view=new View();var calls=new AtomicInteger();
        var owner=new OnlineMusicScreen.SessionOwner(()->{calls.incrementAndGet();return view;});
        owner.mount(null);owner.mount(null);assertEquals(1,calls.get());assertEquals(2,view.layouts);
        owner.close();owner.close();assertEquals(1,view.closes);
    }
    @Test void returningAfterRemovalGetsNewSessionInsteadOfClosedPlayer() {
        var first=new View();var second=new View();var calls=new AtomicInteger();
        var owner=new OnlineMusicScreen.SessionOwner(()->calls.getAndIncrement()==0?first:second);
        owner.mount(null);owner.close();owner.mount(null);
        assertEquals(1,first.closes);assertEquals(1,second.layouts);assertEquals(0,second.closes);
        owner.close();assertEquals(1,second.closes);
    }
    @Test void partialLayoutFailureClosesSessionAndDoesNotRetainFailedView() {
        var bad=new View();bad.fail=true;var good=new View();var calls=new AtomicInteger();
        var owner=new OnlineMusicScreen.SessionOwner(()->calls.getAndIncrement()==0?bad:good);
        assertThrows(IllegalStateException.class,()->owner.mount(null));assertEquals(1,bad.closes);
        owner.mount(null);assertEquals(1,good.layouts);owner.close();assertEquals(1,good.closes);
    }
    @Test void unavailableFactoryDoesNotPreventLaterFreshOpen() {
        var calls=new AtomicInteger();var good=new View();
        var owner=new OnlineMusicScreen.SessionOwner(()->{if(calls.getAndIncrement()==0)throw new IllegalStateException("not started");return good;});
        assertThrows(RuntimeException.class,()->owner.mount(null));owner.close();owner.mount(null);
        assertEquals(1,good.layouts);owner.close();assertEquals(1,good.closes);
    }
}
