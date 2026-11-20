// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.CollapseService;
import net.minecraft.client.gui.screens.Screen;
import java.util.Set;

final class NativeAnimationExample implements UIModule {
    static final UiId PROPERTY=UiId.parse("sh_better_ui:animation_example/progress");
    private final ClientScreens screens;
    ModuleContext context;
    NativeAnimationExample(ClientScreens screens){this.screens=screens;}
    ModuleDiscovery.Candidate candidate(){return new ModuleDiscovery.Candidate("sh_better_ui",getClass().getName(),this,Set.of(),false,true);}
    public ModuleDescriptor descriptor(){return ModuleDescriptor.builder().id(UiId.parse("sh_better_ui:native_animation_example"))
            .version("1.0.0").platforms(Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2, ModuleDescriptor.Platform.NEOFORGE_26_2))
            .initializationPhase(ModuleDescriptor.InitializationPhase.EXTENSION)
            .requiredCapability(UIApis.SCOPED_ANIMATION,VersionRange.ANY)
            .requiredCapability(new CapabilityKey(UIApis.COLLAPSE.id()),VersionRange.ANY)
            .claimResource(new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.ANIMATION_PROPERTY,PROPERTY))
            .claimResource(CollapseService.PROGRESS_CLAIM).build();}
    public void register(ModuleContext context){}
    public void start(ModuleContext context){this.context=context;}
    public void stop(ModuleContext context){this.context=null;}
    boolean available(){return context!=null && context.scope().isOpen();}
    ScopeHandle mount(Screen screen){
        if(!available() || NativePort.screen(net.minecraft.client.Minecraft.getInstance())!=screen)
            throw new IllegalStateException("Animation example must be active with its module running");
        screens.show(screen);return screens.mountTrusted(context,screen,UiId.parse("sh_better_ui:animation_example/tree"));
    }
}
