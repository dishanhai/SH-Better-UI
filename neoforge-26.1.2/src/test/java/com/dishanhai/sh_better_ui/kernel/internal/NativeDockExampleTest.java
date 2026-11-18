// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.dock.MiniWindowHandle;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeDockExampleTest {
    @Test void realModuleMountsWindowAndResizeRetainsItButRemovalReleasesIt() throws Exception {
        try(var client=NativeTestClient.install();var host=new KernelHost(ModuleDescriptor.Platform.NEOFORGE_26_1_2,new EnginePublication())) {
            var screens=new ClientScreens(host,ModuleDescriptor.Platform.NEOFORGE_26_1_2);
            var dock=new ClientDockWindows(host,screens);
            var example=new NativeDockExample(screens,dock);
            host.startNative(java.util.List.of(example.candidate()));
            assertTrue(example.available(),"The SDK example must start as an actual native module");
            var screen=new NativeDockExampleScreen(null,example);
            client.client().screen=screen;screen.width=600;screen.height=400;screen.init();
            assertEquals(1,dock.windows(example.context).size());
            var window=dock.windows(example.context).getFirst();
            screen.width=400;screen.init();assertSame(window,dock.windows(example.context).getFirst());
            dock.prepare(screen);assertTrue(window.snapshot().isPresent());
            var compact=window.snapshot().orElseThrow().geometry().bounds();
            assertTrue(screen.helpLeft()>=compact.x()+compact.width()+4,
                    "Initial compact tab must not overlap help text");
            assertTrue(screen.helpLeft()+screen.helpWidth()<=screen.width-20);
            screen.removed();assertFalse(window.isOpen());assertTrue(dock.windows(example.context).isEmpty());
            screen.init();assertEquals(1,dock.windows(example.context).size());
            assertNotSame(window,dock.windows(example.context).getFirst());screen.removed();dock.closeAll();
        }
    }
    @Test void explicitCloseDoesNotRespawnOnTickAndOpenButtonCreatesNewWindow() throws Exception {
        try(var client=NativeTestClient.install();var host=new KernelHost(ModuleDescriptor.Platform.NEOFORGE_26_1_2,new EnginePublication())) {
            var screens=new ClientScreens(host,ModuleDescriptor.Platform.NEOFORGE_26_1_2);
            var dock=new ClientDockWindows(host,screens);var example=new NativeDockExample(screens,dock);
            host.startNative(java.util.List.of(example.candidate()));
            var screen=new NativeDockExampleScreen(null,example);client.client().screen=screen;
            screen.width=600;screen.height=400;screen.init();
            press(screen,"close");assertTrue(dock.windows(example.context).isEmpty());
            screen.tick();screen.width=420;screen.init();assertTrue(dock.windows(example.context).isEmpty());
            press(screen,"expand");assertEquals(1,dock.windows(example.context).size());
            dock.prepare(screen);assertTrue(dock.windows(example.context).getFirst().snapshot().orElseThrow().targetExpanded());
            press(screen,"collapse");dock.prepare(screen);assertFalse(dock.windows(example.context).getFirst().snapshot().orElseThrow().targetExpanded());
            client.client().screen=null;press(screen,"expand");
            assertFalse(dock.windows(example.context).getFirst().snapshot().orElseThrow().targetExpanded());
            screen.removed();dock.closeAll();
        }
    }
    private static void press(NativeDockExampleScreen screen,String name)throws Exception {
        var field=NativeDockExampleScreen.class.getDeclaredField(name);field.setAccessible(true);
        ((Button)field.get(screen)).onPress(new KeyEvent(257,0,0));
    }
}
