// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import com.dishanhai.sh_better_ui.client.gui.panel.NativeMusicSettingsScreen;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.live.*;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NativeMusicSettingsScreenTest {
    @Test void buttonsUpdateRealModuleConfigAndPreserveUnrelatedFields() throws Exception {
        try (var client = NativeTestClient.install(); var host = host()) {
            var music = start(host);
            var screen = screen(music, 200, 140);
            client.client().screen = screen;
            assertFalse(music.settings().islandEnabled());
            var original = music.settings().withScale(false, 1.5f);
            music.settings(original);
            button(screen,"island").onPress(new KeyEvent(257,0,0));
            assertEquals(original.withIsland(true),music.settings());
            button(screen,"notices").onPress(new KeyEvent(257,0,0));
            assertEquals(original.withIsland(true).withTaskNotices(true),music.settings());
            music.settings(music.settings().withIsland(false));
            screen.tick();
            assertEquals(original.withTaskNotices(true),music.settings());
            // A retained callback from an old page must not mutate the active config.
            client.client().screen = null;
            button(screen,"island").onPress(new KeyEvent(257,0,0));
            screen.onClose();
            assertFalse(music.settings().islandEnabled());
            assertNull(client.client().screen);
        }
    }
    @Test void narrowScreenHasScrollableControlsAndRetainsScrollOnResize() throws Exception {
        try (var client = NativeTestClient.install(); var host = host()) {
            var screen = screen(start(host),160,100);
            client.client().screen = screen;
            var body = (AbstractContainerWidget)field(screen,"body");
            body.setScrollAmount(30);
            assertTrue(body.scrollAmount() > 0);
            screen.width = 180;
            initialize(screen);
            assertEquals(30,((AbstractContainerWidget)field(screen,"body")).scrollAmount());
            assertTrue(button(screen,"island").getWidth() <= screen.width);
        }
    }
    @Test void hudSliderKeyboardCommitsMergeLatestConfigAndCancelPreservesExactFloats() throws Exception {
        try(var client=NativeTestClient.install();var host=host()) {
            var music=start(host);
            music.settings(music.settings().withPosition(.235f,.625f).withScale(false,1.25f));
            var screen=screen(music,200,180);client.client().screen=screen;
            var slider=(com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider)field(screen,"hudX");
            var before=music.settings();
            slider.onClick(new net.minecraft.client.input.MouseButtonEvent(slider.getX()+slider.getWidth()-5,slider.getY()+5,
                    new net.minecraft.client.input.MouseButtonInfo(0,0)),false);
            assertTrue(slider.isPointerEditing());
            assertEquals(before,music.settings());
            assertTrue(slider.keyPressed(new KeyEvent(256,0,0)));
            assertEquals(.235f,music.settings().x());
            music.settings(music.settings().withIsland(true).withTaskNotices(true));
            slider.keyPressed(new KeyEvent(257,0,0));
            assertTrue(slider.keyPressed(new KeyEvent(262,0,0)));
            assertEquals(.25f,music.settings().x());
            assertEquals(.625f,music.settings().y());
            assertTrue(music.settings().islandEnabled());assertTrue(music.settings().taskNotices());
            var after=music.settings();client.client().screen=null;
            slider.keyPressed(new KeyEvent(262,0,0));assertEquals(after,music.settings());
        }
    }
    @Test void hudResetIsLayoutOnlyAndUndoPreservesOtherSettings() throws Exception {
        try(var client=NativeTestClient.install();var host=host()) {
            var music=start(host);
            music.settings(music.settings().withHud(true).withIsland(true).withPosition(.2f,.4f).withScale(false,1.5f));
            var before=music.settings();var screen=screen(music,200,140);client.client().screen=screen;
            button(screen,"resetHud").onPress(new KeyEvent(257,0,0));
            assertEquals(.5f,music.settings().x());assertEquals(.75f,music.settings().y());assertEquals(1f,music.settings().hudScale());
            assertTrue(music.settings().hudEnabled());assertTrue(music.settings().islandEnabled());
            music.settings(music.settings().withTaskNotices(true));screen.tick();
            button(screen,"undoReset").onPress(new KeyEvent(257,0,0));
            assertEquals(before.withTaskNotices(true),music.settings());
            button(screen,"hud").onPress(new KeyEvent(257,0,0));assertFalse(music.settings().hudEnabled());
        }
    }
    @Test void narrowOuterViewportAndResizeCancelRespectCommittedState() throws Exception {
        try(var client=NativeTestClient.install();var host=host()) {
            var music=start(host);var screen=screen(music,100,120);client.client().screen=screen;
            var body=(AbstractContainerWidget)field(screen,"body");
            assertTrue(body.getX()>=0);assertTrue(body.getX()+body.getWidth()<=100);
            var slider=(com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider)field(screen,"hudScale");
            var before=music.settings();
            slider.onClick(new net.minecraft.client.input.MouseButtonEvent(slider.getX()+5,slider.getY()+5,
                    new net.minecraft.client.input.MouseButtonInfo(0,0)),false);
            screen.width=140;initialize(screen);
            assertFalse(slider.isPointerEditing());assertEquals(before,music.settings());
        }
    }

    @Test void hudUiChangesPersistAcrossRealHosts(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        try(var client=NativeTestClient.install();var host=host()) {
            host.storage(directory);var music=start(host,true);
            var screen=screen(music,240,180);client.client().screen=screen;
            button(screen,"hud").onPress(new KeyEvent(257,0,0));
            var slider=(com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider)field(screen,"hudScale");
            slider.keyPressed(new KeyEvent(257,0,0));slider.keyPressed(new KeyEvent(262,0,0));
            // The settings API commits via the module's already-bound ConfigDiskStore.
        }
        try(var client=NativeTestClient.install();var host=host()) {
            host.storage(directory);var music=start(host,true);
            assertTrue(music.settings().hudEnabled());assertEquals(1.01f,music.settings().hudScale());
        }
    }

    private static KernelHost host() {
        return new KernelHost(ModuleDescriptor.Platform.NEOFORGE_26_1_2,new EnginePublication(),System::nanoTime,Runnable::run,SchedulerBudget.DEFAULT);
    }
    private static NativeMusicActivity start(KernelHost host) { return start(host,false); }
    private static NativeMusicActivity start(KernelHost host,boolean persistent) {
        var live = new LiveSurfaceRuntime(host);
        var music = new NativeMusicActivity(persistent, () -> true, () -> {});
        var platforms = Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2);
        host.start(List.of(BuiltinService.candidate(platforms,ActivityService.KEY,live,ActivityService.CAPABILITY),
                BuiltinService.candidate(platforms,NotificationService.KEY,live,NotificationService.CAPABILITY),music.candidate()));
        return music;
    }
    private static NativeMusicSettingsScreen screen(NativeMusicActivity music,int width,int height) throws Exception {
        var screen = new NativeMusicSettingsScreen(null,music); screen.width=width;screen.height=height;
        initialize(screen);return screen;
    }
    private static void initialize(NativeMusicSettingsScreen screen) throws Exception {
        var init=NativeMusicSettingsScreen.class.getDeclaredMethod("init");init.setAccessible(true);init.invoke(screen);
    }
    private static Button button(Object screen,String name) throws Exception { return (Button)field(screen,name); }
    private static Object field(Object screen,String name) throws Exception {
        var field=screen.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(screen);
    }
}
