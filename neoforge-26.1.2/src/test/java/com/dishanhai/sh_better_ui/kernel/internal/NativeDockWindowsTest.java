// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class NativeDockWindowsTest {
    static final class Fixture implements AutoCloseable {
        final NativeTestClient client = NativeTestClient.install();
        final KernelHost host = new KernelHost(ModuleDescriptor.Platform.NEOFORGE_26_1_2,new EnginePublication());
        final ClientScreens screens = new ClientScreens(host,ModuleDescriptor.Platform.NEOFORGE_26_1_2);
        final ClientDockWindows dock = new ClientDockWindows(host,screens);
        final Screen screen = new Screen(Component.empty()) {};
        ModuleContext module; ScopeHandle tree;
        Fixture() throws Exception {
            this(Set.of(UIPermission.REGISTER_RENDER_PASS,UIPermission.CAPTURE_INPUT,UIPermission.BLOCK_VANILLA_INPUT));
        }
        Fixture(Set<UIPermission> grants) throws Exception {
            var plugin = new UIModule() {
                @Override public ModuleDescriptor descriptor() {
                    var builder=ModuleDescriptor.builder().id(UiId.parse("example:windows")).version("1.0.0")
                            .platforms(Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2));
                    grants.forEach(builder::requestPermission); return builder.build();
                }
                @Override public void register(ModuleContext context) {}
                @Override public void start(ModuleContext context) { module=context; }
                @Override public void stop(ModuleContext context) {}
            };
            host.startNative(List.of(new ModuleDiscovery.Candidate("example","fixture",plugin,grants,false)));
            assertNotNull(module, "The fixture module must start on the target platform before mounting a tree");
            client.client().screen=screen; screen.width=800;screen.height=500;
            screens.show(screen); tree=screens.mountTrusted(module,screen,UiId.parse("example:tree"));
        }
        MiniWindowHandle window() { return window((draw,view) -> {}); }
        MiniWindowHandle window(WindowContent content) {
            return dock.createMiniWindow(module,tree,MiniWindowSpec.builder("example:window").autoHide(false)
                    .animation(new CollapseSpec(CollapseSpec.Direction.HORIZONTAL,CollapseSpec.Curve.LINEAR,0,0,true,false)).content(() -> content).build());
        }
        void prepare(){dock.prepare(screen);}
        @Override public void close() throws Exception { try {dock.closeAll();host.close();}finally{client.close();} }
    }
    static NativeInput.Pointer pointer(NativeInput.PointerAction type,double x,double y) {return new NativeInput.Pointer(type,x,y,0,0,0);}

    @Test void missingInputPermissionRejectsBeforeAllocatingWindow() throws Exception {
        try (var f = new Fixture(Set.of(UIPermission.REGISTER_RENDER_PASS, UIPermission.CAPTURE_INPUT))) {
            assertThrows(SecurityException.class, f::window);
            assertTrue(f.dock.windows(f.module).isEmpty());
        }
    }

    @Test void ownedHandlesDockResizeAndDisappearWhenScopeCloses() throws Exception {
        try(var f=new Fixture()) {
            var window=f.window();window.floatAt(60,70);window.resize(400,220);f.prepare();
            var geometry=window.snapshot().orElseThrow().geometry();
            assertEquals(60,geometry.bounds().x());assertEquals(400,geometry.bounds().width());
            window.dock(DockEdge.RIGHT);f.prepare();assertEquals(800,window.snapshot().orElseThrow().geometry().bounds().x()+400);
            assertEquals(1,f.dock.windows(f.module).size()); f.tree.close();assertFalse(window.isOpen());assertTrue(f.dock.windows(f.module).isEmpty());
        }
    }
    @Test void dragCancellationRestoresPlacementAndReleaseCommits() throws Exception {
        try(var f=new Fixture()) {
            var window=f.window();window.floatAt(60,70);f.prepare();
            assertTrue(f.dock.input(f.screen,pointer(NativeInput.PointerAction.PRESS,80,80)));
            assertTrue(f.dock.input(f.screen,pointer(NativeInput.PointerAction.DRAG,180,180)));f.prepare();
            assertEquals(160,window.snapshot().orElseThrow().geometry().bounds().x());
            assertTrue(f.dock.input(f.screen,new NativeInput.Key(NativeInput.KeyAction.PRESS,256,0,0)));f.prepare();
            assertEquals(60,window.snapshot().orElseThrow().geometry().bounds().x());
            f.dock.input(f.screen,pointer(NativeInput.PointerAction.PRESS,80,80));
            f.dock.input(f.screen,pointer(NativeInput.PointerAction.RELEASE,180,180));f.prepare();
            assertEquals(160,window.snapshot().orElseThrow().geometry().bounds().x());
        }
    }
    @Test void unsupportedFeaturesRejectBeforeAllocatingAndCloseIsIdempotent() throws Exception {
        try(var f=new Fixture()) {
            assertThrows(UnsupportedOperationException.class,()->f.dock.createMiniWindow(f.module,f.tree,MiniWindowSpec.builder("example:persistent").persistent(true).build()));
            assertTrue(f.dock.windows(f.module).isEmpty());
            var window=f.window();window.close();window.close();assertFalse(window.isOpen());
            assertThrows(IllegalStateException.class,window::expand);
        }
    }
    @Test void suspendCancelsResizeAndStaleScreenCannotRoute() throws Exception {
        try(var f=new Fixture()) {
            var window=f.window();window.floatAt(60,70);f.prepare();
            f.dock.input(f.screen,pointer(NativeInput.PointerAction.PRESS,318,248));
            f.dock.input(f.screen,pointer(NativeInput.PointerAction.DRAG,418,348));
            f.dock.suspend();assertTrue(window.snapshot().isEmpty());f.prepare();
            assertEquals(260,window.snapshot().orElseThrow().geometry().bounds().width());
            f.client.client().screen=null;
            assertFalse(f.dock.input(f.screen,pointer(NativeInput.PointerAction.PRESS,80,80)));
        }
    }
    @Test void collapseCancelsCaptureAndNeverForwardsHiddenDragOrRelease() throws Exception {
        try(var f=new Fixture()) {
            var events=new ArrayList<NativeInput>(); var focuses=new ArrayList<Boolean>(); var cancelled=new int[1];
            var window=f.window(new WindowContent() {
                @Override public void render(RenderService.Context draw,View view) {}
                @Override public Response input(NativeInput input,View view) { events.add(input); return Response.CAPTURE; }
                @Override public void captureCancelled() { cancelled[0]++; }
                @Override public void focusChanged(boolean focus) { focuses.add(focus); }
            });
            window.floatAt(60,70);f.prepare();
            assertTrue(f.dock.input(f.screen,pointer(NativeInput.PointerAction.PRESS,100,120)));
            assertEquals(List.of(true),focuses);
            window.collapse();f.prepare();
            assertEquals(1,cancelled[0]);assertEquals(List.of(true,false),focuses);
            f.dock.input(f.screen,pointer(NativeInput.PointerAction.DRAG,150,150));
            f.dock.input(f.screen,pointer(NativeInput.PointerAction.RELEASE,150,150));
            assertEquals(1,events.size(),"Collapsed content must never receive the captured gesture");
        }
    }

    @Test void deferredContentFocusIsDeliveredOnceAfterExpansion() throws Exception {
        try(var f=new Fixture()) {
            var focuses=new ArrayList<Boolean>();
            var window=f.window(new WindowContent() {
                @Override public void render(RenderService.Context draw,View view) {}
                @Override public void focusChanged(boolean focus) { focuses.add(focus); }
            });
            window.floatAt(60,70);window.collapse();f.prepare();
            assertTrue(f.dock.input(f.screen,pointer(NativeInput.PointerAction.PRESS,65,75)));
            assertTrue(focuses.isEmpty(),"Compact header focus must not focus hidden content");
            f.prepare();f.prepare();
            assertEquals(List.of(true),focuses,"Content gains focus exactly once when expansion becomes interactive");
            window.toggle();f.prepare();
            assertEquals(List.of(true,false),focuses);
        }
    }

    @Test void collapseDuringHeaderDragRestoresThePreGesturePlacement() throws Exception {
        try(var f=new Fixture()) {
            var window=f.window();window.floatAt(60,70);f.prepare();
            f.dock.input(f.screen,pointer(NativeInput.PointerAction.PRESS,80,80));
            f.dock.input(f.screen,pointer(NativeInput.PointerAction.DRAG,180,180));
            window.collapse();f.prepare();
            assertEquals(60,window.snapshot().orElseThrow().geometry().placement().x());
            assertFalse(window.snapshot().orElseThrow().pointerCaptured());
            f.dock.input(f.screen,pointer(NativeInput.PointerAction.RELEASE,180,180));f.prepare();
            assertEquals(60,window.snapshot().orElseThrow().geometry().placement().x());
        }
    }

}
