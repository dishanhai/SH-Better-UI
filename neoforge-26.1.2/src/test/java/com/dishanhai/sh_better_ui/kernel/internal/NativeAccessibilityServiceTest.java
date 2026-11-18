// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;
import net.minecraft.client.gui.narration.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class NativeAccessibilityServiceTest {
    static final class Fixture implements AutoCloseable {
        final EnginePublication publication=new EnginePublication();
        final KernelHost host=new KernelHost(ModuleDescriptor.Platform.NEOFORGE_26_1_2,publication);
        final ModuleContext[] modules=new ModuleContext[2];
        final AccessibilityService service;
        Fixture(){
            var platforms=Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2);
            var candidates=new ArrayList<ModuleDiscovery.Candidate>();
            candidates.add(BuiltinService.candidate(platforms,UIApis.ACCESSIBILITY,new ClientAccessibility(host),UIApis.MINECRAFT_NARRATION));
            for(int i=0;i<2;i++){
                int index=i;
                var plugin=new UIModule(){
                    public ModuleDescriptor descriptor(){return ModuleDescriptor.builder().id(UiId.parse("example:accessibility"+index)).version("1.0.0")
                            .platforms(platforms).requiredCapability(UIApis.MINECRAFT_NARRATION,VersionRange.ANY).build();}
                    public void register(ModuleContext context){}
                    public void start(ModuleContext context){modules[index]=context;}
                    public void stop(ModuleContext context){}
                };
                candidates.add(new ModuleDiscovery.Candidate("example","consumer"+i,plugin,Set.of(),false));
            }
            host.startNative(candidates);assertNotNull(modules[0]);assertNotNull(modules[1]);service=publication.current().require(UIApis.ACCESSIBILITY);
        }
        ScopeHandle tree(int module){return modules[module].scope().openChild(ScopeKind.SCREEN,UiId.parse("example:screen"+module))
                .openChild(ScopeKind.UI_TREE,UiId.parse("example:tree"+module));}
        public void close(){host.close();}
    }
    static AccessibilityService.Semantics semantics(String name,String hint){return new AccessibilityService.Semantics(AccessibilityService.Role.BUTTON,Component.literal(name),Component.literal(hint));}
    static NarrationElementOutput capture(NarrationElementOutput output,Set<NarratedElementType> types){
        return new NarrationElementOutput(){
            public void add(NarratedElementType type,NarrationThunk<?> contents){types.add(type);output.add(type,contents);}
            public NarrationElementOutput nest(){return capture(output.nest(),types);}
        };
    }
    @Test void registeredServiceCollectsDynamicNativeTitleAndHintWithoutClaimingSystemReader(){
        try(var f=new Fixture()){
            assertTrue(f.publication.current().capabilities().supports(UIApis.MINECRAFT_NARRATION));
            assertEquals(CapabilityStatus.UNAVAILABLE,f.publication.current().capabilities().status(UIApis.SYSTEM_SCREEN_READER));
            var node=f.tree(0);var description=new AtomicReference<>(semantics("Open","Ready"));
            f.service.describe(f.modules[0],node,description::get);
            var types=EnumSet.noneOf(NarratedElementType.class);
            var collector=new ScreenNarrationCollector();collector.update(output->assertTrue(f.service.narrate(f.modules[0],node,capture(output,types))));
            assertEquals(EnumSet.of(NarratedElementType.TITLE,NarratedElementType.HINT),types);
            var first=collector.collectNarrationText(true);assertTrue(first.contains("Open"));assertTrue(first.contains("Ready"));
            description.set(semantics("Changed",""));
            types.clear();collector.update(output->assertTrue(f.service.narrate(f.modules[0],node,capture(output,types))));
            assertEquals(EnumSet.of(NarratedElementType.TITLE),types);
            var second=collector.collectNarrationText(true);assertTrue(second.contains("Changed"));assertFalse(second.contains("Ready"));
            assertEquals(AccessibilityService.Role.BUTTON,f.service.semantics(f.modules[0],node).orElseThrow().role());
        }
    }
    @Test void bindingsAreOwnedReplaceableAfterUnsubscribeAndCloseWithTheirNode(){
        try(var f=new Fixture()){
            var node=f.tree(0);var binding=f.service.describe(f.modules[0],node,()->semantics("One","Hint"));
            assertThrows(IllegalStateException.class,()->f.service.describe(f.modules[0],node,()->semantics("Two","")));
            assertThrows(SecurityException.class,()->f.service.semantics(f.modules[1],node));
            assertThrows(SecurityException.class,()->f.service.describe(f.modules[1],node,()->semantics("Foreign","")));
            assertThrows(IllegalArgumentException.class,()->f.service.describe(f.modules[0],f.modules[0].scope(),()->semantics("Module","")));
            binding.close();binding.close();assertFalse(binding.isActive());assertTrue(f.service.semantics(f.modules[0],node).isEmpty());
            var rebound=f.service.describe(f.modules[0],node,()->semantics("Rebound",""));node.close();assertFalse(rebound.isActive());
            assertThrows(IllegalStateException.class,()->f.service.semantics(f.modules[0],node));
            var child=f.tree(1).openChild(ScopeKind.COMPONENT,UiId.parse("example:component"));
            var component=f.service.describe(f.modules[1],child,()->semantics("Child",""));f.host.stopModule(f.modules[1].descriptor().id());assertFalse(component.isActive());
        }
    }
    @Test void offThreadAndSupplierClosingNodeCannotEmitStaleNarration()throws Exception{
        try(var f=new Fixture()){
            var node=f.tree(0);var error=new AtomicReference<Throwable>();
            var thread=new Thread(()->{try{f.service.semantics(f.modules[0],node);}catch(Throwable failure){error.set(failure);}});thread.start();thread.join();
            assertInstanceOf(IllegalStateException.class,error.get());
            var subscription=f.service.describe(f.modules[0],node,()->{node.close();return semantics("Stale","Must not be spoken");});
            var collector=new ScreenNarrationCollector();collector.update(output->assertFalse(f.service.narrate(f.modules[0],node,output)));
            assertEquals("",collector.collectNarrationText(true));assertFalse(subscription.isActive());
        }
    }
    @Test void themePreferencesComposeWithCurrentNativePreferencesWithoutMutatingThem()throws Exception{
        try(var client=NativeTestClient.install();var themes=new ThemeManager();var f=new Fixture()){
            var original=UiPreferences.configured();var scope=themes.global().child("accessibility");
            themes.preferences(new ThemePreferences(ThemePreferences.Mode.FOLLOW_ENVIRONMENT,null,true,true,true,1,true));
            var prefs=f.service.preferences(scope);assertTrue(prefs.highContrast());assertTrue(prefs.reducedMotion());assertFalse(prefs.backgroundBlur());assertEquals(original.feedback(),prefs.feedback());
            assertEquals(original,UiPreferences.configured());scope.close();
            assertEquals(original,f.service.preferences(scope));assertEquals(original,f.service.preferences(null));
        }
    }
}
