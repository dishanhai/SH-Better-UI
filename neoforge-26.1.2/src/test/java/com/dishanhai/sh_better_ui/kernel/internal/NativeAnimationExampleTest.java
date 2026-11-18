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
            assertEquals(6,screen.children().size());
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
