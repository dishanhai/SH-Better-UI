// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import com.dishanhai.sh_better_ui.kernel.api.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

final class NativeAnimationFixture implements AutoCloseable {
    final NativeTestClient client;
    private final NativeAnimationPreferences preferences;
    final AtomicLong time=new AtomicLong();
    final EnginePublication publication=new EnginePublication();
    final KernelHost host;
    final ClientAnimation animation;
    final ClientCollapse collapse;
    final ClientScreens screens;
    final Screen screen;
    ModuleContext module;
    final ScopeHandle tree;
    NativeAnimationFixture(Set<ModuleDescriptor.ResourceClaim> claims) throws Exception {
        preferences=new NativeAnimationPreferences();
        client=NativeTestClient.install();
        screen=new Screen(Component.empty()){};
        host=new KernelHost(ModuleDescriptor.Platform.NEOFORGE_26_1_2,publication,time::get,null,SchedulerBudget.DEFAULT);
        screens=new ClientScreens(host,ModuleDescriptor.Platform.NEOFORGE_26_1_2);
        animation=new ClientAnimation(host);collapse=new ClientCollapse(host,animation);
        var consumer=new UIModule(){
            public ModuleDescriptor descriptor(){
                var builder=ModuleDescriptor.builder().id(UiId.parse("example:motion"))
                        .version("1.0.0").platforms(Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2))
                        .requiredCapability(UIApis.SCOPED_ANIMATION,VersionRange.ANY)
                        .requiredCapability(new CapabilityKey(UIApis.COLLAPSE.id()),VersionRange.ANY);
                claims.forEach(builder::claimResource);return builder.build();
            }
            public void register(ModuleContext context){}
            public void start(ModuleContext context){module=context;}
            public void stop(ModuleContext context){}
        };
        var platforms=Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2);
        host.startNative(List.of(BuiltinService.candidate(platforms,UIApis.ANIMATION,animation,UIApis.SCOPED_ANIMATION),
                BuiltinService.candidate(platforms,UIApis.COLLAPSE,collapse,new CapabilityKey(UIApis.COLLAPSE.id())),
                new ModuleDiscovery.Candidate("example","fixture",consumer,Set.of(),false)));
        if(module==null)throw new AssertionError("Native consumer failed discovery/capability resolution");
        client.screen(screen);screen.width=400;screen.height=240;
        screens.show(screen);tree=screens.mountTrusted(module,screen,UiId.parse("example:motion/tree"));
    }
    void frame(long millis){
        time.set(millis*1_000_000L);
        host.beginNativeFrame(()->{},phase->{if(phase==UIPhase.ANIMATION){animation.update();collapse.update();}});
        host.finishNativeFrame();
    }
    @Override public void close() throws Exception {try{host.close();}finally{try{client.close();}finally{preferences.close();}}}
}
