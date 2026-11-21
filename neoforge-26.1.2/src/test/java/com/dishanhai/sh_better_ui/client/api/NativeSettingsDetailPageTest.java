// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NativeSettingsDetailPageTest {
    static UiSettingsDetailPage page(List<UiButton> controls,UiSettingsDetailPage.Actions actions){
        var fields=new ArrayList<UiSettingsDetailPage.Field>();
        for(int i=0;i<12;i++){
            var label=Component.literal("Field "+i);
            fields.add(new UiSettingsDetailPage.Field(label,(bounds,context)->{
                var button=new UiButton(bounds.x(),bounds.y(),bounds.width(),bounds.height(),label,b->{},context.preferences(),false);
                controls.add(button);return button;
            }));
        }
        return ShBetterUiApi.controls().settingsDetailPage(Component.literal("Details"),List.of(new UiSettingsDetailPage.Section(Component.literal("Group"),fields)),actions);
    }
    @Test void keyboardRevealsClippedFieldsAndResizeRetainsScrollAndFocus()throws Exception{
        try(var client=NativeTestClient.install()){
            var controls=new ArrayList<UiButton>();var page=page(controls,null);var screen=page.builder().build();
            client.screen(screen);screen.width=180;screen.height=180;screen.init();
            client.client().setLastInputType(net.minecraft.client.InputType.KEYBOARD_TAB);
            for(int i=0;i<10;i++)screen.keyPressed(new KeyEvent(258,0,0));
            assertTrue(page.scrollOffset()>0);var focus=screen.getFocused();assertTrue(controls.contains(focus));
            int index=controls.indexOf(focus);double offset=page.scrollOffset();
            screen.width=200;screen.refreshLayout();
            assertSame(controls.get(12+index),screen.getFocused());assertEquals(offset,page.scrollOffset());
            var clip=(Bounds)field(page,"clip");var button=(UiButton)screen.getFocused();
            assertTrue(button.getY()>=clip.y()&&button.getY()+button.getHeight()<=clip.bottom());
            assertTrue(screen.getChildAt(button.getX()+1,clip.y()-1).isEmpty(),"Clipped fields cannot receive outside clicks");
            screen.removed();assertNull(field(page,"owner"));
        }
    }
    @Test void applyFailureKeepsDraftSuccessDoesNotCancelAndRemovalCancelsOnce()throws Exception{
        try(var client=NativeTestClient.install()){
            var cancel=new AtomicInteger();var edits=new AtomicInteger(3);boolean[] fail={true};
            var actions=new UiSettingsDetailPage.Actions(()->Component.literal("Reset"),Component.literal("Cancel"),Component.literal("Apply"),
                    ()->edits.set(0),cancel::incrementAndGet,()->fail[0]?Component.literal("Invalid"):null,()->edits.get()!=0);
            var page=page(new ArrayList<>(),actions);var screen=page.builder().build();client.screen(screen);screen.width=320;screen.height=220;screen.init();
            assertFalse(page.applyDraft());assertEquals(3,edits.get());assertEquals(Component.literal("Invalid"),field(page,"status"));
            fail[0]=false;assertTrue(page.applyDraft());screen.removed();screen.removed();assertEquals(0,cancel.get());
            client.screen(screen);screen.init();screen.removed();screen.removed();assertEquals(1,cancel.get());
        }
    }
    @Test void contextClosesDistinctEqualResourcesExactlyOnce()throws Exception{
        try(var client=NativeTestClient.install()){
            var context=new UiSettingsDetailPage.Context();var calls=new AtomicInteger();
            class Resource implements AutoCloseable{public void close(){calls.incrementAndGet();}public boolean equals(Object other){return other instanceof Resource;}public int hashCode(){return 1;}}
            var first=new Resource();context.manage(first);context.manage(first);context.manage(new Resource());
            context.close();context.close();assertEquals(2,calls.get());assertThrows(IllegalStateException.class,context::preferences);
        }
    }
    @Test void tinyViewportDoesNotConstructInvalidControls()throws Exception{
        try(var client=NativeTestClient.install()){
            var controls=new ArrayList<UiButton>();var page=page(controls,null);var screen=page.builder().build();
            client.screen(screen);screen.width=24;screen.height=40;assertDoesNotThrow(()->screen.init());assertTrue(controls.isEmpty());assertEquals(1,screen.children().size(),"Tiny fallback contains only Back");screen.removed();
        }
    }
    @Test void ownedConfirmationKeepsDraftOnNoAndCancelsExactlyOnceOnYesOrAbandon()throws Exception{
        try(var client=NativeTestClient.install()){
            var cancel=new AtomicInteger();var draft=new AtomicInteger(7);
            var actions=new UiSettingsDetailPage.Actions(()->Component.literal("Reset"),Component.literal("Cancel"),Component.literal("Apply"),
                    ()->draft.set(0),()->{cancel.incrementAndGet();draft.set(0);},()->null,()->draft.get()!=0);
            var page=page(new ArrayList<>(),actions);var parent=new net.minecraft.client.gui.screens.Screen(Component.literal("Parent")){};
            var modal=new net.minecraft.client.gui.screens.Screen(Component.literal("Modal")){};
            var other=new net.minecraft.client.gui.screens.Screen(Component.literal("Other")){};
            var screen=page.builder().parent(parent).build();screen.width=180;screen.height=180;
            java.util.function.Consumer<net.minecraft.client.gui.screens.Screen> show=next->{
                if(client.screen()!=null)client.screen().removed();client.screen(next);
                if(next instanceof UiScreen ui)ui.refreshLayout();
            };
            page.closeScreen=owner->{
                try{UiScreen.closeIfCurrent(owner,parent,()->client.screen(),show,
                        (java.util.function.BooleanSupplier)field(owner,"closeRequested"),()->null);}
                catch(Exception error){throw new AssertionError(error);}
            };
            Runnable[] decision=new Runnable[2];
            page.confirm=(owner,prompt,preferences,focus,accepted,abandoned)->{
                decision[0]=accepted;decision[1]=abandoned;show.accept(modal);return true;
            };
            show.accept(screen);
            var scrolling=(com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll)field(page,"scroll");scrolling.scrollTo(64);
            var context=(UiSettingsDetailPage.Context)field(page,"context");var closes=new AtomicInteger();context.onClose(closes::incrementAndGet);
            page.closeScreen.accept(screen);assertSame(modal,client.screen());assertEquals(7,draft.get());assertEquals(0,cancel.get());assertEquals(0,closes.get());
            // No returns to the same edit session, replacing only per-mount resources.
            show.accept(screen);assertEquals(7,draft.get());assertEquals(0,cancel.get());assertEquals(64,page.scrollOffset());assertEquals(1,closes.get());
            page.closeScreen.accept(screen);show.accept(screen);decision[0].run();
            assertSame(parent,client.screen());assertEquals(1,cancel.get());assertEquals(0,draft.get());
            decision[1].run();screen.removed();assertEquals(1,cancel.get());
            // A new session abandoned from its own modal also releases precisely once.
            draft.set(9);show.accept(screen);page.closeScreen.accept(screen);show.accept(other);decision[1].run();decision[1].run();
            assertSame(other,client.screen());assertEquals(2,cancel.get());assertEquals(0,draft.get());assertNull(field(page,"owner"));
        }
    }
    @Test void eachRebuildReleasesOnlyPreviousMountResources()throws Exception{
        try(var client=NativeTestClient.install()){
            var closes=new AtomicInteger();var cancels=new AtomicInteger();
            var field=new UiSettingsDetailPage.Field(Component.literal("Resource"),(bounds,context)->{
                context.onClose(closes::incrementAndGet);
                return new UiButton(bounds.x(),bounds.y(),bounds.width(),bounds.height(),Component.literal("Value"),b->{},context.preferences(),false);
            });
            var actions=new UiSettingsDetailPage.Actions(()->Component.empty(),Component.empty(),Component.empty(),()->{},cancels::incrementAndGet,()->null,()->true);
            var page=new UiSettingsDetailPage(Component.literal("Lifecycle"),List.of(new UiSettingsDetailPage.Section(Component.literal("Section"),List.of(field))),actions);
            var screen=page.builder().build();client.screen(screen);screen.width=320;screen.height=220;screen.init();
            screen.refreshLayout();assertEquals(1,closes.get());assertEquals(0,cancels.get());
            screen.refreshLayout();assertEquals(2,closes.get());screen.removed();assertEquals(3,closes.get());assertEquals(1,cancels.get());
        }
    }
    @Test void rejectedSecondOwnerDoesNotCancelOrReleaseMountedPage()throws Exception{
        try(var client=NativeTestClient.install()){
            var cancels=new AtomicInteger();var closes=new AtomicInteger();
            var actions=new UiSettingsDetailPage.Actions(()->Component.empty(),Component.empty(),Component.empty(),()->{},cancels::incrementAndGet,()->null,()->true);
            var page=page(new ArrayList<>(),actions);var first=page.builder().build();
            client.screen(first);first.width=320;first.height=220;first.init();
            var context=(UiSettingsDetailPage.Context)field(page,"context");context.onClose(closes::incrementAndGet);
            var second=page.builder().build();second.width=320;second.height=220;
            assertThrows(IllegalStateException.class,()->second.init());
            assertSame(first,field(page,"owner"));assertSame(context,field(page,"context"));
            assertEquals(0,cancels.get());assertEquals(0,closes.get());assertDoesNotThrow(()->context.preferences());
            first.removed();assertEquals(1,cancels.get());assertEquals(1,closes.get());
        }
    }
    static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
}
