// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.config.ControlFeedbackSettings;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class NativeFeedbackScreenTest {
    @TempDir Path directory;
    Object original,file;
    @BeforeEach void isolate()throws Exception{original=staticField("settings");file=staticField("file");ControlPanelSettings.load(directory);}
    @AfterEach void restore()throws Exception{setStatic("settings",original);setStatic("file",file);}
    @Test void editsPreviewOnlyAndApplyMergesUnrelatedCurrentSettings()throws Exception{
        try(var client=NativeTestClient.install()){
            var screen=screen(client);press(screen,"hover");
            assertTrue(ControlPanelSettings.current().feedback().hoverAnimation());
            var prefs=(UiPreferences)field(field(screen,"preview"),"preferences");assertFalse(prefs.feedback().hoverAnimation());
            var other=ControlPanelSettings.current().withDisplay(true,true,false).withGlass(false,true,70,90,110);
            assertTrue(ControlPanelSettings.save(other));screen.apply();
            assertFalse(screen.dirty());assertEquals(other.withFeedback(new ControlFeedbackSettings(false,true,true,0)),ControlPanelSettings.current());
            ControlPanelSettings.load(directory);assertFalse(ControlPanelSettings.current().feedback().hoverAnimation());
        }
    }
    @Test void narrowScrollAndSliderCancelPreserveExactDraftAndNeverSave()throws Exception{
        try(var client=NativeTestClient.install()){
            var initial=new ControlFeedbackSettings(true,true,true,347);
            var screen=new NativeFeedbackScreen(null,initial,value->{});client.client().screen=screen;screen.width=160;screen.height=180;screen.init();
            var body=(AbstractContainerWidget)field(screen,"body");assertTrue(body.getX()>=0&&body.getX()+body.getWidth()<=160);
            var slider=(UiSlider)field(screen,"delay");
            slider.onClick(new MouseButtonEvent(slider.getX()+slider.getWidth()-5,slider.getY()+8,new MouseButtonInfo(0,0)),false);
            assertNotEquals(347,((ControlFeedbackSettings)field(screen,"draft")).tooltipDelayMs());
            screen.keyPressed(new KeyEvent(256,0,0));assertEquals(347,((ControlFeedbackSettings)field(screen,"draft")).tooltipDelayMs());
            assertEquals(ControlFeedbackSettings.DEFAULT,ControlPanelSettings.current().feedback());
            assertTrue(screen.keyPressed(new KeyEvent(269,0,0)));assertTrue(body.scrollAmount()>0);
            screen.width=200;screen.init();assertEquals(347,((UiSlider)field(screen,"delay")).value());
            assertTrue(((AbstractContainerWidget)field(screen,"body")).scrollAmount()>0);
        }
    }
    @Test void resetUndoIsDraftOnlyAndFailedSaveRetainsChanges()throws Exception{
        try(var client=NativeTestClient.install()){
            var screen=screen(client);press(screen,"press");var draft=field(screen,"draft");
            var beforeReset=NativeFeedbackScreen.class.getDeclaredField("beforeReset");beforeReset.setAccessible(true);
            // Invoke the real reset and undo controls through the scroll container.
            var body=(AbstractContainerWidget)field(screen,"body");
            var reset=body.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(b->b.getMessage().equals(NativeFeedbackScreen.label("reset"))).findFirst().orElseThrow();
            reset.onPress(new KeyEvent(257,0,0));assertEquals(ControlFeedbackSettings.DEFAULT,field(screen,"draft"));
            press(screen,"undo");assertEquals(draft,field(screen,"draft"));
            assertEquals(ControlFeedbackSettings.DEFAULT,ControlPanelSettings.current().feedback());
            Path blocked=directory.resolve("blocked");java.nio.file.Files.writeString(blocked,"not a directory");
            ControlPanelSettings.load(blocked);screen.apply();assertTrue(screen.dirty());assertEquals(draft,field(screen,"draft"));
        }
    }
    @Test void childApplyDoesNotHideParentUnsavedDisplayChanges()throws Exception{
        try(var client=NativeTestClient.install()){
            var parent=new ControlPanelScreen(null);var f=ControlPanelScreen.class.getDeclaredField("draft");f.setAccessible(true);
            var dirty=ControlPanelSettings.current().withDisplay(true,true,false);f.set(parent,dirty);
            var feedback=new ControlFeedbackSettings(false,false,false,300);parent.feedbackApplied(feedback);
            assertEquals(dirty.withFeedback(feedback),f.get(parent));assertNull(field(parent,"status"));
            assertNotEquals(field(parent,"saved"),field(parent,"draft"));
        }
    }
    @Test void cancelDiscardsWithoutWritingAndDirtyEscapeRequiresDecision()throws Exception{
        try(var client=NativeTestClient.install()){
            var parent=new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("Parent")){};
            java.util.function.Consumer<net.minecraft.client.gui.screens.Screen> navigate=next->{
                if(client.client().screen!=null)client.client().screen.removed();
                client.client().screen=next;
                if(next instanceof NativeFeedbackScreen feedback){feedback.width=320;feedback.height=240;feedback.init();}
                else if(next instanceof net.minecraft.client.gui.screens.ConfirmScreen){
                    try{var init=next.getClass().getDeclaredMethod("init");init.setAccessible(true);next.width=320;next.height=240;init.invoke(next);}
                    catch(ReflectiveOperationException e){throw new AssertionError(e);}
                }
            };
            var screen=new NativeFeedbackScreen(parent,ControlFeedbackSettings.DEFAULT,value->{},navigate);
            navigate.accept(screen);press(screen,"hover");
            var cancel=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(b->b.getMessage().equals(NativeFeedbackScreen.label("cancel"))).findFirst().orElseThrow();
            cancel.onPress(new KeyEvent(257,0,0));assertSame(parent,client.client().screen);
            assertEquals(ControlFeedbackSettings.DEFAULT,ControlPanelSettings.current().feedback());
            navigate.accept(screen);assertTrue(screen.keyPressed(new KeyEvent(256,0,0)));
            var confirm=assertInstanceOf(net.minecraft.client.gui.screens.ConfirmScreen.class,client.client().screen);
            ((Button)field(confirm,"noButton")).onPress(new KeyEvent(257,0,0));
            assertSame(screen,client.client().screen);assertTrue(screen.dirty());assertFalse(((ControlFeedbackSettings)field(screen,"draft")).hoverAnimation());
            screen.keyPressed(new KeyEvent(256,0,0));
            var accepted=assertInstanceOf(net.minecraft.client.gui.screens.ConfirmScreen.class,client.client().screen);
            ((Button)field(accepted,"yesButton")).onPress(new KeyEvent(257,0,0));assertSame(parent,client.client().screen);
            // A late callback from the previous dialog must not navigate away from the parent.
            ((Button)field(confirm,"noButton")).onPress(new KeyEvent(257,0,0));assertSame(parent,client.client().screen);
            ControlPanelSettings.load(directory);assertEquals(ControlFeedbackSettings.DEFAULT,ControlPanelSettings.current().feedback());
        }
    }
    @Test void tabReachesSliderAndArrowKeysEditDraftOnly()throws Exception{
        try(var client=NativeTestClient.install()){
            var screen=screen(client);var slider=(UiSlider)field(screen,"delay");
            client.client().setLastInputType(net.minecraft.client.InputType.KEYBOARD_TAB);
            for(int i=0;i<16&&!slider.isFocused();i++)screen.keyPressed(new KeyEvent(258,0,0));
            assertTrue(slider.isFocused(),"Tab must reach the nested slider without direct focus injection");
            assertTrue(screen.keyPressed(new KeyEvent(262,0,0)));
            assertEquals(1,((ControlFeedbackSettings)field(screen,"draft")).tooltipDelayMs());
            assertTrue(screen.keyPressed(new KeyEvent(263,0,0)));
            assertEquals(0,((ControlFeedbackSettings)field(screen,"draft")).tooltipDelayMs());
            assertEquals(ControlFeedbackSettings.DEFAULT,ControlPanelSettings.current().feedback());
        }
    }
    @Test void initialStatusIsEmptyAndStatusClipStaysInsideNarrowScreens()throws Exception{
        try(var client=NativeTestClient.install()){
            var screen=screen(client);assertEquals("",((net.minecraft.network.chat.Component)field(screen,"status")).getString());
            press(screen,"hover");assertEquals(NativeFeedbackScreen.label("unsaved"),field(screen,"status"));
            for(int width:new int[]{320,160,24,1}){
                screen.width=width;screen.height=180;var bounds=screen.statusBounds();
                assertTrue(bounds.x()>=0);assertTrue(bounds.width()>=0);assertTrue(bounds.x()+bounds.width()<=width);
                assertTrue(bounds.y()+bounds.height()<=screen.height-26,"Status cannot cover the footer buttons");
            }
        }
    }
    private static NativeFeedbackScreen screen(NativeTestClient client){var screen=new NativeFeedbackScreen(null);client.client().screen=screen;screen.width=320;screen.height=240;screen.init();return screen;}
    private static void press(Object screen,String name)throws Exception{((Button)field(screen,name)).onPress(new KeyEvent(257,0,0));}
    private static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static Object staticField(String name)throws Exception{var f=ControlPanelSettings.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static void setStatic(String name,Object value)throws Exception{var f=ControlPanelSettings.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
}
