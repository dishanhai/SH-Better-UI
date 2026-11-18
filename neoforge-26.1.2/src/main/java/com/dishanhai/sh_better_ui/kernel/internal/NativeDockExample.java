// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import net.minecraft.client.gui.screens.Screen;
import java.util.Set;

/** Real built-in module for the Screen-only SDK sample; no fake module context or persistent demo data. */
final class NativeDockExample implements UIModule {
    private static final Set<UIPermission> GRANTS=Set.of(UIPermission.REGISTER_RENDER_PASS,
            UIPermission.CAPTURE_INPUT,UIPermission.BLOCK_VANILLA_INPUT);
    private final ClientScreens screens;
    final ClientDockWindows dock;
    ModuleContext context;
    NativeDockExample(ClientScreens screens,ClientDockWindows dock){this.screens=screens;this.dock=dock;}
    ModuleDiscovery.Candidate candidate(){return new ModuleDiscovery.Candidate("sh_better_ui",getClass().getName(),this,GRANTS,false,true);}
    @Override public ModuleDescriptor descriptor(){
        var descriptor=ModuleDescriptor.builder().id(UiId.parse("sh_better_ui:native_dock_example"))
                .version("1.0.0").platforms(Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2))
                .initializationPhase(ModuleDescriptor.InitializationPhase.EXTENSION);
        GRANTS.forEach(descriptor::requestPermission);return descriptor.build();
    }
    @Override public void register(ModuleContext context){}
    @Override public void start(ModuleContext context){this.context=context;}
    @Override public void stop(ModuleContext context){this.context=null;}
    boolean available(){return context!=null && context.scope().isOpen();}
    ScopeHandle mount(Screen owner){
        if(!available())throw new IllegalStateException("Dock example module is not available");
        if(NativePort.screen(net.minecraft.client.Minecraft.getInstance())!=owner)
            throw new IllegalStateException("Dock example must be the active Screen");
        screens.show(owner);
        return screens.mountTrusted(context,owner,UiId.parse("sh_better_ui:dock_example/tree"));
    }
}
