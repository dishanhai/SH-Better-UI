// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.*;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeGlassDetailPageTest {
    @Test void detailAppliesOnlyOpticsToLatestParentDraftAndNeverSaves()throws Exception{
        try(var client=NativeTestClient.install()){
            var saved=ControlPanelSettings.current();var parent=new ControlPanelScreen(null);
            var initial=saved.withGlass(saved.backgroundBlur(),true,50,60,70);set(parent,"draft",initial);
            var page=parent.glassParametersPage();var screen=page.builder().parent(parent).build();
            client.screen(screen);screen.width=320;screen.height=220;var init=UiScreen.class.getDeclaredMethod("init");init.setAccessible(true);init.invoke(screen);
            client.client().setLastInputType(net.minecraft.client.InputType.KEYBOARD_TAB);
            var slider=screen.children().stream().filter(UiSlider.class::isInstance).map(UiSlider.class::cast).findFirst().orElseThrow();
            screen.setFocused(slider);assertTrue(screen.keyPressed(new KeyEvent(262,0,0)));
            assertEquals(initial,field(parent,"draft"));assertEquals(saved,ControlPanelSettings.current());
            var latest=initial.withDisplay(!initial.highContrast(),!initial.reducedMotion(),!initial.widgetBlur());set(parent,"draft",latest);
            var actions=(UiSettingsDetailPage.Actions)field(page,"actions");assertNull(actions.apply().get());
            assertEquals(latest.withGlass(latest.backgroundBlur(),latest.glassEnabled(),51,60,70),field(parent,"draft"));
            assertEquals(saved,ControlPanelSettings.current());screen.removed();
        }
    }
    @Test void resetUndoIsLocalAndReturnFocusResolvesRebuiltEntry()throws Exception{
        try(var client=NativeTestClient.install()){
            var parent=new ControlPanelScreen(null);var initial=ControlPanelSettings.current().withGlass(true,true,11,12,13);set(parent,"draft",initial);
            var page=parent.glassParametersPage();var actions=(UiSettingsDetailPage.Actions)field(page,"actions");
            assertFalse(actions.dirty().getAsBoolean());
            assertEquals("sh_better_ui.panel.reset",((net.minecraft.network.chat.contents.TranslatableContents)actions.resetLabel().get().getContents()).getKey());
            actions.reset().run();assertTrue(actions.dirty().getAsBoolean());
            assertEquals("sh_better_ui.panel.reset.undo",((net.minecraft.network.chat.contents.TranslatableContents)actions.resetLabel().get().getContents()).getKey());
            actions.reset().run();assertFalse(actions.dirty().getAsBoolean());assertEquals(initial,field(parent,"draft"));
            var screen=parent.glassParametersScreen();var supplier=(java.util.function.Supplier<?>)field(screen,"returnFocus");
            var button=new UiButton(0,0,100,20,Component.literal("entry"),b->{},UiPreferences.configured(),false);
            set(parent,"glassParameters",button);assertSame(button,supplier.get());
            var rebuilt=new UiButton(0,0,100,20,Component.literal("entry2"),b->{},UiPreferences.configured(),false);
            set(parent,"glassParameters",rebuilt);assertSame(rebuilt,supplier.get());
        }
    }
    static Object field(Object value,String name)throws Exception{var field=value.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(value);}
    static void set(Object value,String name,Object data)throws Exception{var field=value.getClass().getDeclaredField(name);field.setAccessible(true);field.set(value,data);}
}
