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
    static final class TestScreen extends Screen {
        TestScreen() { super(Component.empty()); }
        void addParentControl(net.minecraft.client.gui.components.Button button) { addWidget(button); }
    }
    static final class Fixture implements AutoCloseable {
        final NativeTestClient client = NativeTestClient.install();
        final KernelHost host = new KernelHost(ModuleDescriptor.Platform.NEOFORGE_26_1_2,new EnginePublication());
        final ClientScreens screens = new ClientScreens(host,ModuleDescriptor.Platform.NEOFORGE_26_1_2);
        final ClientDockWindows dock = new ClientDockWindows(host,screens);
        final TestScreen screen = new TestScreen();
        ModuleContext module; ScopeHandle tree;
        Fixture() throws Exception {
            this(Set.of(UIPermission.REGISTER_RENDER_PASS,UIPermission.CAPTURE_INPUT,UIPermission.BLOCK_VANILLA_INPUT));
        }
        Fixture(Set<UIPermission> grants) throws Exception { this(grants,null,"example:windows"); }
        Fixture(Set<UIPermission> grants, java.nio.file.Path storage, String moduleId) throws Exception {
            if(storage!=null) host.storage(storage);
            var plugin = new UIModule() {
                @Override public ModuleDescriptor descriptor() {
                    var builder=ModuleDescriptor.builder().id(UiId.parse(moduleId)).version("1.0.0")
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

    private static Set<UIPermission> persistentGrants() {
        return Set.of(UIPermission.REGISTER_RENDER_PASS,UIPermission.CAPTURE_INPUT,UIPermission.BLOCK_VANILLA_INPUT,UIPermission.MODIFY_GLOBAL_CONFIG);
    }
    private static MiniWindowHandle persistent(Fixture fixture) {
        return fixture.dock.createMiniWindow(fixture.module,fixture.tree,MiniWindowSpec.builder("example:persistent")
                .persistent(true).autoHide(false)
                .animation(new CollapseSpec(CollapseSpec.Direction.HORIZONTAL,CollapseSpec.Curve.LINEAR,0,0,true,false))
                .content(() -> (draw,view) -> {}).build());
    }
    @Test void persistentRequiresGrantAndRealStorageBeforeWindowAllocation() throws Exception {
        try(var fixture=new Fixture()) {
            assertThrows(SecurityException.class,()->persistent(fixture));
            assertTrue(fixture.dock.windows(fixture.module).isEmpty());
        }
        try(var fixture=new Fixture(persistentGrants())) {
            assertThrows(IllegalStateException.class,()->persistent(fixture));
            assertTrue(fixture.dock.windows(fixture.module).isEmpty());
        }
    }
    @Test void placementSurvivesHostRestartButNotWindowLifetimeAndIsModuleIsolated(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        try(var fixture=new Fixture(persistentGrants(),directory,"example:windows")) {
            var window=persistent(fixture);window.floatAt(100,110);window.resize(400,220);
            fixture.module.engine().configs().flush().toCompletableFuture().join();
            fixture.tree.close();assertFalse(window.isOpen());
            assertTrue(fixture.dock.windows(fixture.module).isEmpty());
        }
        try(var files=java.nio.file.Files.walk(directory)) { assertTrue(files.anyMatch(java.nio.file.Files::isRegularFile)); }
        try(var fixture=new Fixture(persistentGrants(),directory,"example:windows")) {
            var window=persistent(fixture);fixture.prepare();
            var bounds=window.snapshot().orElseThrow().geometry().bounds();
            assertEquals(100,bounds.x());assertEquals(110,bounds.y());assertEquals(400,bounds.width());
            window.close(); window=persistent(fixture);fixture.prepare();
            assertEquals(100,window.snapshot().orElseThrow().geometry().bounds().x());
            fixture.screen.width=140;fixture.screen.height=90;fixture.prepare();
            bounds=window.snapshot().orElseThrow().geometry().bounds();
            assertTrue(bounds.x()>=0 && bounds.y()>=0 && bounds.x()+bounds.width()<=140 && bounds.y()+bounds.height()<=90);
        }
        try(var fixture=new Fixture(persistentGrants(),directory,"example:other")) {
            var window=persistent(fixture);fixture.prepare();
            assertEquals(260,window.snapshot().orElseThrow().geometry().bounds().width());
        }
    }
    @Test void restoredSizeRespectsNewSpecAndResetPersists(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        try(var fixture=new Fixture(persistentGrants(),directory,"example:windows")) {
            var window=persistent(fixture);window.resize(700,600);window.floatAt(20,30);
            fixture.module.engine().configs().flush().toCompletableFuture().join();
        }
        try(var fixture=new Fixture(persistentGrants(),directory,"example:windows")) {
            var spec=MiniWindowSpec.builder("example:persistent").persistent(true).autoHide(false)
                    .size(260,180).limits(90,80,300,200)
                    .animation(new CollapseSpec(CollapseSpec.Direction.HORIZONTAL,CollapseSpec.Curve.LINEAR,0,0,true,false))
                    .content(() -> (draw,view) -> {}).build();
            var window=fixture.dock.createMiniWindow(fixture.module,fixture.tree,spec);fixture.prepare();
            assertEquals(300,window.snapshot().orElseThrow().geometry().bounds().width());
            assertEquals(200,window.snapshot().orElseThrow().geometry().bounds().height());
            window.resetPlacement();fixture.module.engine().configs().flush().toCompletableFuture().join();
        }
        try(var fixture=new Fixture(persistentGrants(),directory,"example:windows")) {
            var window=persistent(fixture);fixture.prepare();
            assertEquals(260,window.snapshot().orElseThrow().geometry().bounds().width());
        }
    }

    @Test void cancelledDragDoesNotWriteButReleasedDragRestoresOnRestart(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        try(var fixture=new Fixture(persistentGrants(),directory,"example:windows")) {
            var window=persistent(fixture);window.floatAt(60,70);fixture.prepare();
            fixture.module.engine().configs().flush().toCompletableFuture().join();
            long writes=fixture.module.engine().configs().persistence().submitted();
            fixture.dock.input(fixture.screen,pointer(NativeInput.PointerAction.PRESS,80,80));
            fixture.dock.input(fixture.screen,pointer(NativeInput.PointerAction.DRAG,180,180));
            fixture.dock.suspend();fixture.prepare();
            assertEquals(writes,fixture.module.engine().configs().persistence().submitted());
            assertEquals(60,window.snapshot().orElseThrow().geometry().bounds().x());
            fixture.dock.input(fixture.screen,pointer(NativeInput.PointerAction.PRESS,318,248));
            fixture.dock.input(fixture.screen,pointer(NativeInput.PointerAction.DRAG,418,348));
            fixture.dock.suspend();fixture.prepare();
            assertEquals(writes,fixture.module.engine().configs().persistence().submitted());
            assertEquals(260,window.snapshot().orElseThrow().geometry().bounds().width());
            fixture.dock.input(fixture.screen,pointer(NativeInput.PointerAction.PRESS,80,80));
            fixture.dock.input(fixture.screen,pointer(NativeInput.PointerAction.RELEASE,180,180));
            fixture.module.engine().configs().flush().toCompletableFuture().join();
        }
        try(var fixture=new Fixture(persistentGrants(),directory,"example:windows")) {
            var window=persistent(fixture);fixture.prepare();assertEquals(160,window.snapshot().orElseThrow().geometry().bounds().x());
        }
    }

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
            assertThrows(UnsupportedOperationException.class,()->f.dock.createMiniWindow(f.module,f.tree,MiniWindowSpec.builder("example:spring").animation(new CollapseSpec(CollapseSpec.Direction.HORIZONTAL,CollapseSpec.Curve.SPRING,100,100,true,false)).build()));
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

    @Test void shortcutCollapseRestoresParentFocusAndStopsHiddenCharacterInput() throws Exception {
        try (var f = new Fixture()) {
            var events = new ArrayList<NativeInput>();
            var focuses = new ArrayList<Boolean>();
            var parentControl = net.minecraft.client.gui.components.Button.builder(Component.literal("Parent"), button -> {}).build();
            f.screen.addParentControl(parentControl);
            f.screen.setFocused(parentControl);
            var content = new WindowContent() {
                @Override public void render(RenderService.Context draw, View view) {}
                @Override public Response input(NativeInput input, View view) { events.add(input); return Response.FOCUS; }
                @Override public void focusChanged(boolean focus) { focuses.add(focus); }
            };
            var window = f.dock.createMiniWindow(f.module, f.tree, MiniWindowSpec.builder("example:shortcut")
                    .autoHide(false).shortcut(75, 2)
                    .animation(new CollapseSpec(CollapseSpec.Direction.HORIZONTAL, CollapseSpec.Curve.LINEAR, 0, 0, false, false))
                    .content(() -> content).build());
            var shortcut = new NativeInput.Key(NativeInput.KeyAction.PRESS, 75, 0, 2);
            f.prepare();
            assertTrue(f.dock.input(f.screen, shortcut)); f.prepare();
            assertNull(f.screen.getFocused());
            assertEquals(List.of(true), focuses);
            assertTrue(f.dock.input(f.screen, new NativeInput.Character('a', 0)));
            assertEquals(1, events.size());

            assertTrue(f.dock.input(f.screen, shortcut)); f.prepare();
            assertSame(parentControl, f.screen.getFocused());
            assertEquals(List.of(true, false), focuses, "A collapse shortcut must not refocus hidden content");
            assertFalse(window.snapshot().orElseThrow().focused());
            assertFalse(f.dock.input(f.screen, new NativeInput.Character('b', 0)));
            assertEquals(1, events.size(), "Hidden content must not receive characters");

            assertTrue(f.dock.input(f.screen, shortcut)); f.prepare();
            assertEquals(List.of(true, false, true), focuses);
            assertTrue(f.dock.input(f.screen, new NativeInput.Character('c', 0)));
            assertEquals(2, events.size(), "Reopening restores normal content input");
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
