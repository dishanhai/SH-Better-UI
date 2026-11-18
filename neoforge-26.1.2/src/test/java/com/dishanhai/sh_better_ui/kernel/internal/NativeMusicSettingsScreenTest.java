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
    private static KernelHost host() {
        return new KernelHost(ModuleDescriptor.Platform.NEOFORGE_26_1_2,new EnginePublication(),System::nanoTime,Runnable::run,SchedulerBudget.DEFAULT);
    }
    private static NativeMusicActivity start(KernelHost host) {
        var live = new LiveSurfaceRuntime(host);
        var music = new NativeMusicActivity(false, () -> true, () -> {});
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
