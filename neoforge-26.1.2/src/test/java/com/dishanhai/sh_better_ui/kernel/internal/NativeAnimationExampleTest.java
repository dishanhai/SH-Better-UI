// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import com.dishanhai.sh_better_ui.kernel.api.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NativeAnimationExampleTest {
    @Test void exampleStartsWithDeclaredClaimsAndMountsRealServicesUntilRemoval()throws Exception {
        var time=new java.util.concurrent.atomic.AtomicLong();
        try(var preferences=new NativeAnimationPreferences();var client=NativeTestClient.install();var host=new KernelHost(ModuleDescriptor.Platform.NEOFORGE_26_1_2,new EnginePublication(),time::get,null,SchedulerBudget.DEFAULT)){
            var screens=new ClientScreens(host,ModuleDescriptor.Platform.NEOFORGE_26_1_2);
            var animation=new ClientAnimation(host);var collapse=new ClientCollapse(host,animation);var example=new NativeAnimationExample(screens);
            var platforms=Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2);
            host.startNative(java.util.List.of(BuiltinService.candidate(platforms,UIApis.ANIMATION,animation,UIApis.SCOPED_ANIMATION),
                    BuiltinService.candidate(platforms,UIApis.COLLAPSE,collapse,new CapabilityKey(UIApis.COLLAPSE.id())),example.candidate()));
            assertTrue(example.available());
            var screen=new NativeAnimationExampleScreen(null,example);client.client().screen=screen;screen.width=400;screen.height=300;screen.init();
            var track=(AnimationService.Track)field(screen,"track");assertTrue(track.isActive());
            frame(host,animation,collapse);time.set(1_500_000_000L);frame(host,animation,collapse);
            assertEquals(.5f,track.value(),.0001f,"The initial demo track lasts 3 seconds instead of immediately settling");
            track.pause();time.set(2_000_000_000L);frame(host,animation,collapse);assertEquals(.5f,track.value(),.0001f);
            track.resume();time.set(3_500_000_000L);frame(host,animation,collapse);assertEquals(1f,track.value(),.0001f);
            screen.width=320;screen.init();assertSame(track,field(screen,"track"));
            assertEquals(6,screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance).count());
            screen.height=180;screen.init();
            var body=(net.minecraft.client.gui.components.AbstractContainerWidget)field(screen,"body");
            var preview=(net.minecraft.client.gui.components.AbstractWidget)field(screen,"preview");
            assertNotNull(body);assertNotNull(preview);assertSame(track,field(screen,"track"));
            assertTrue(body.getX()>=0 && body.getX()+body.getWidth()<=320);
            int footerTop=screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast).mapToInt(net.minecraft.client.gui.components.Button::getY).min().orElseThrow();
            assertTrue(body.getY()+body.getHeight()<footerTop,"Scrollable body must stay above the fixed footer");
            assertTrue(preview.visible);assertEquals(82,preview.getHeight(),"Both demo bars remain in the scroll content");
            assertTrue(screen.keyPressed(new net.minecraft.client.input.KeyEvent(269,0,0)));
            assertTrue(body.scrollAmount()>0,"Short-height content can reach its second bar");
            screen.width=300;screen.init();
            body=(net.minecraft.client.gui.components.AbstractContainerWidget)field(screen,"body");
            assertTrue(body.scrollAmount()>0);assertSame(track,field(screen,"track"));
            assertTrue(screen.keyPressed(new net.minecraft.client.input.KeyEvent(268,0,0)));assertEquals(0,body.scrollAmount());
            assertTrue(screen.mouseScrolled(body.getX()+10,body.getY()+10,0,-1));
            assertTrue(body.scrollAmount()>0,"Pointer wheel scrolls the visible body");
            screen.removed();assertFalse(track.isActive());
            screen.init();assertNotSame(track,field(screen,"track"));screen.removed();
        }
    }
    private static void frame(KernelHost host,ClientAnimation animation,ClientCollapse collapse){
        host.beginNativeFrame(()->{},phase->{if(phase==UIPhase.ANIMATION){animation.update();collapse.update();}});
        host.finishNativeFrame();
    }
    private static Object field(Object object,String name)throws Exception{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
}
