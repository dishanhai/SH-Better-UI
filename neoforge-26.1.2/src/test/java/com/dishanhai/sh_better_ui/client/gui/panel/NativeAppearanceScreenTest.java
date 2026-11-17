// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NativeAppearanceScreenTest {
    @TempDir Path directory;
    @Test void invalidRawDraftAndResetUndoNeverChangeSavedSettings() {
        ControlPanelSettings.load(directory);
        var saved=ControlPanelSettings.current();
        var draft=new NativeAppearanceDraft(saved.theme().withMode(ThemeSettings.Mode.CUSTOM),saved.theme());
        draft.color(0,"#12");assertTrue(draft.dirty());
        assertThrows(IllegalArgumentException.class,draft::value);
        draft.reset();assertTrue(draft.canUndoReset());draft.undoReset();
        assertEquals("#12",draft.state().colors().get(0));
        assertEquals(saved,ControlPanelSettings.current());
    }
    @Test void nativeApplyPersistsThemeAndMergesLatestOtherSettings() throws Exception {
        ControlPanelSettings.load(directory);
        try(var client=NativeTestClient.install()){
            var screen=new NativeAppearanceScreen(null);client.client().screen=screen;screen.width=300;screen.height=220;screen.init();
            screen.draft.mode(ThemeSettings.Mode.CUSTOM);screen.draft.color(4,"#45ABCD");screen.draft.background("nativeGradient");screen.init();
            var other=ControlPanelSettings.current().withDisplay(true,true,false).withGlass(false,true,70,90,110);
            assertTrue(ControlPanelSettings.save(other));
            press(screen,"apply");
            assertFalse(screen.draft.dirty());
            assertEquals(other.withTheme(other.theme().withMode(ThemeSettings.Mode.CUSTOM)
                    .withPalette(ThemeSettings.Base.DARK,"","","","","#45ABCD").withBackgroundEffect("nativeGradient")),ControlPanelSettings.current());
            ControlPanelSettings.load(directory);assertEquals("#45ABCD",ControlPanelSettings.current().theme().accent());
            var reopened=new NativeAppearanceScreen(null);assertEquals("nativeGradient",reopened.draft.state().background());
        }
    }
    @Test void rawFieldsAndScrollSurviveResizeWithoutSaving() throws Exception {
        ControlPanelSettings.load(directory);
        try(var client=NativeTestClient.install()){
            var screen=new NativeAppearanceScreen(null);client.client().screen=screen;screen.draft.mode(ThemeSettings.Mode.CUSTOM);
            screen.width=120;screen.height=150;screen.init();
            var input=(EditBox)controls(screen).get("color:0");input.setValue("bad!");
            var body=(AbstractContainerWidget)field(screen,"body");body.setScrollAmount(30);
            assertTrue(body.getX()>=0 && body.getX()+body.getWidth()<=screen.width);
            screen.width=160;screen.init();
            assertEquals("bad!",((EditBox)controls(screen).get("color:0")).getValue());
            assertEquals(30,((AbstractContainerWidget)field(screen,"body")).scrollAmount());
            assertFalse(controls(screen).get("apply").active);
            assertEquals(ControlPanelSettings.DEFAULT,ControlPanelSettings.current());
        }
    }
    @Test void failedSaveRetainsDraftAndParentCallbackIsNotCalled() throws Exception {
        java.nio.file.Files.writeString(directory.resolve("sh_better_ui"),"blocked");ControlPanelSettings.load(directory);
        try(var client=NativeTestClient.install()){
            var calls=new java.util.concurrent.atomic.AtomicInteger();
            var screen=new NativeAppearanceScreen(null,ControlPanelSettings.current().theme(),theme->calls.incrementAndGet());
            client.client().screen=screen;screen.width=300;screen.height=220;screen.draft.background("nativeSolid");screen.init();
            press(screen,"apply");assertTrue(screen.draft.dirty());assertEquals(0,calls.get());
            assertEquals(ControlPanelSettings.DEFAULT,ControlPanelSettings.current());
        }
    }
    @Test void childApplyPreservesUnappliedParentChangesWithoutShowingSaved() throws Exception {
        ControlPanelSettings.load(directory);
        try(var client=NativeTestClient.install()){
            var parent=new ControlPanelScreen(null);
            var draftField=ControlPanelScreen.class.getDeclaredField("draft");draftField.setAccessible(true);
            var dirty=ControlPanelSettings.current().withDisplay(true,true,false);draftField.set(parent,dirty);
            var appearance=ControlPanelSettings.current().theme().withMode(ThemeSettings.Mode.LIGHT);
            parent.appearanceApplied(appearance);
            assertEquals(dirty.withTheme(appearance),draftField.get(parent));
            assertNull(field(parent,"status"));
            assertNotEquals(field(parent,"saved"),field(parent,"draft"));
        }
    }
    private static void press(Object screen,String key)throws Exception{((Button)controls(screen).get(key)).onPress(new KeyEvent(257,0,0));}
    @SuppressWarnings("unchecked") private static Map<String,AbstractWidget> controls(Object screen)throws Exception{return (Map<String,AbstractWidget>)field(screen,"controls");}
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
}
