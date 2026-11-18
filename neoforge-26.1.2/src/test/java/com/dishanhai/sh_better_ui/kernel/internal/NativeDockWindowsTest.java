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
        final java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong();
        final KernelHost host = new KernelHost(ModuleDescriptor.Platform.NEOFORGE_26_1_2,new EnginePublication(),clock::get,null,SchedulerBudget.DEFAULT);
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
    @Test void glassPolicyHonorsPlayerSettingsAndMaterialQuality() {
        var regular=com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial.preset(com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant.REGULAR);
        var prefs=com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences.DEFAULT;
        assertTrue(ClientDockWindows.optical(regular,prefs,true));
        assertFalse(ClientDockWindows.optical(regular,prefs,false));
        assertFalse(ClientDockWindows.optical(regular,new com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences(true,false,true),true));
        assertFalse(ClientDockWindows.optical(regular,new com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences(false,false,false),true));
        var fallback=new com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial(regular.variant(),regular.optics(),
                com.dishanhai.sh_better_ui.kernel.api.glass.GlassQuality.FALLBACK,
                com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial.Foreground.DARK);
        assertFalse(ClientDockWindows.optical(fallback,prefs,true));
        assertEquals(0xff000000,ClientDockWindows.foreground(fallback));
        assertEquals(0xffffffff,ClientDockWindows.foreground(regular));
        var colors=new ArrayList<Integer>();
        ClientDockWindows.inkText(Component.literal("R").withStyle(style->style.withColor(0xff0000).withBold(true)),0xffffffff)
                .accept((i,style,c)->{colors.add(style.getColor().getValue());assertTrue(style.isBold());return true;});
        assertFalse(colors.isEmpty());assertTrue(colors.stream().allMatch(c->(c&0xffffff)==0xffffff));
        ClientDockWindows.inkText(Component.literal("§cR"),0xff000000)
                .accept((i,style,c)->{assertEquals(0,style.getColor().getValue()&0xffffff);return true;});
    }
    @Test void glassTitlePreservesNativeCodePointsAndNonColorStyles() throws Exception {
        try(var f=new Fixture()) {
            // Font.substrByWidth reads its private splitter rather than getSplitter().
            // Supply deterministic glyph advances but retain the real styled headByWidth algorithm.
            var splitter=new net.minecraft.client.StringSplitter((codePoint,style)->6f);
            var testFont=new net.minecraft.client.gui.Font(null) {
                @Override public net.minecraft.network.chat.FormattedText substrByWidth(
                        net.minecraft.network.chat.FormattedText text,int width) {
                    return splitter.headByWidth(text,width,net.minecraft.network.chat.Style.EMPTY);
                }
            };
            var fontField=net.minecraft.client.Minecraft.class.getDeclaredField("font");
            fontField.setAccessible(true);fontField.set(f.client.client(),testFont);
            var font=new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.parse("sh_better_ui:mp_ui"));
            var title=Component.literal("Bold §z unknown 😀").withStyle(style->style.withBold(true).withFont(font).withColor(0xff0000));
            var baseline=net.minecraft.locale.Language.getInstance().getVisualOrder(f.client.client().font.substrByWidth(title,500));
            var expectedPoints=new ArrayList<Integer>();var expectedStyles=new ArrayList<net.minecraft.network.chat.Style>();
            baseline.accept((i,style,c)->{expectedPoints.add(c);expectedStyles.add(style);return true;});
            var actualPoints=new ArrayList<Integer>();var actualStyles=new ArrayList<net.minecraft.network.chat.Style>();
            var glass=com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial.preset(com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant.REGULAR);
            ClientDockWindows.titleText(title,500,glass).accept((i,style,c)->{actualPoints.add(c);actualStyles.add(style);return true;});
            assertEquals(expectedPoints,actualPoints);assertTrue(actualPoints.contains(0x1f600));
            assertFalse(actualStyles.isEmpty());
            for(int i=0;i<actualStyles.size();i++) {
                assertEquals(expectedStyles.get(i).withColor(0xffffffff),actualStyles.get(i));
                assertTrue(actualStyles.get(i).isBold());assertEquals(font,actualStyles.get(i).getFont());
            }
        }
    }
    @Test void glassCaptureOncePerPresentedFrameAndNeverForHiddenClosedOrDisabledWindows() throws Exception {
        try(var f=new Fixture()) {
            var material=com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial.preset(com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant.REGULAR);
            for(int i=0;i<2;i++) f.dock.createMiniWindow(f.module,f.tree,MiniWindowSpec.builder("example:glass"+i).glass(material).build());
            f.prepare();var calls=new java.util.concurrent.atomic.AtomicInteger();
            var prefs=com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences.DEFAULT;
            assertTrue(f.dock.prepareGlass(prefs,true,()->{calls.incrementAndGet();return true;}));assertEquals(1,calls.get());
            f.prepare();assertFalse(f.dock.prepareGlass(prefs,true,()->{calls.incrementAndGet();return false;}));assertEquals(2,calls.get());
            assertFalse(f.dock.prepareGlass(prefs,false,()->{calls.incrementAndGet();return true;}));assertEquals(2,calls.get());
            f.dock.suspend();assertFalse(f.dock.prepareGlass(prefs,true,()->{calls.incrementAndGet();return true;}));
            f.dock.closeAll();f.prepare();assertFalse(f.dock.prepareGlass(prefs,true,()->{calls.incrementAndGet();return true;}));assertEquals(2,calls.get());
        }
    }
    @Test void closeIsIdempotent() throws Exception {
        try(var f=new Fixture()) {
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=CollapseSpec.Curve.class,names={"LINEAR","SPRING"})
    void shortcutCollapseRestoresParentFocusAndStopsHiddenCharacterInput(CollapseSpec.Curve curve) throws Exception {
        try (var preferences=new NativeAnimationPreferences();var f = new Fixture()) {
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
                    .animation(new CollapseSpec(CollapseSpec.Direction.HORIZONTAL, curve, 240, 240, false, false))
                    .content(() -> content).build());
            var shortcut = new NativeInput.Key(NativeInput.KeyAction.PRESS, 75, 0, 2);
            f.prepare();
            assertTrue(f.dock.input(f.screen, shortcut)); for(int i=0;i<100;i++)advance(f,16);
            assertNull(f.screen.getFocused());
            assertEquals(List.of(true), focuses);
            assertTrue(f.dock.input(f.screen, new NativeInput.Character('a', 0)));
            assertEquals(1, events.size());

            assertTrue(f.dock.input(f.screen, shortcut)); for(int i=0;i<100;i++)advance(f,16);
            assertSame(parentControl, f.screen.getFocused());
            assertEquals(List.of(true, false), focuses, "A collapse shortcut must not refocus hidden content");
            assertFalse(window.snapshot().orElseThrow().focused());
            assertFalse(f.dock.input(f.screen, new NativeInput.Character('b', 0)));
            assertEquals(1, events.size(), "Hidden content must not receive characters");

            assertTrue(f.dock.input(f.screen, shortcut)); for(int i=0;i<100;i++)advance(f,16);
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

    private static MiniWindowHandle springWindow(Fixture f, long duration) {
        // The existing window permissions suffice: deliberately no animation-property claim.
        return f.dock.createMiniWindow(f.module,f.tree,MiniWindowSpec.builder("example:spring")
                .animation(new CollapseSpec(CollapseSpec.Direction.HORIZONTAL,CollapseSpec.Curve.SPRING,duration,duration,false,false)).build());
    }
    private static void advance(Fixture f,long ms){f.clock.addAndGet(ms*1_000_000L);f.prepare();}
    @Test void springReversalIsContinuousAndSettlesWithBoundedGeometry() throws Exception {
        try(var preferences=new NativeAnimationPreferences();var f=new Fixture()){
            var window=springWindow(f,240);f.prepare();window.expand();advance(f,80);
            float before=window.snapshot().orElseThrow().progress();assertTrue(before>0&&before<1);
            window.collapse();f.prepare();assertEquals(before,window.snapshot().orElseThrow().progress(),.00001f);
            advance(f,16);float reversing=window.snapshot().orElseThrow().progress();
            window.expand();f.prepare();assertEquals(reversing,window.snapshot().orElseThrow().progress(),.00001f);
            var springField=window.getClass().getDeclaredField("spring");springField.setAccessible(true);
            var motion=(com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation)springField.get(window);
            for(int i=0;i<400;i++){
                advance(f,16);var snap=window.snapshot().orElseThrow();assertTrue(snap.progress()>=0&&snap.progress()<=1);
                if(motion.running())assertEquals(CollapseState.EXPANDING,snap.state(),"Clamping must not publish a premature terminal state");
            }
            assertEquals(CollapseState.EXPANDED,window.snapshot().orElseThrow().state());
            assertEquals(1,window.snapshot().orElseThrow().progress());
            window.collapse();for(int i=0;i<400;i++)advance(f,16);
            assertEquals(CollapseState.COLLAPSED,window.snapshot().orElseThrow().state());
            assertEquals(0,window.snapshot().orElseThrow().progress());
        }
    }
    @Test void zeroDurationSpringAndMidFlightReducedMotionSnapToEndpoints() throws Exception {
        try(var preferences=new NativeAnimationPreferences();var f=new Fixture()){
            var immediate=springWindow(f,0);f.prepare();immediate.expand();f.prepare();
            assertEquals(CollapseState.EXPANDED,immediate.snapshot().orElseThrow().state());
            immediate.collapse();f.prepare();assertEquals(0,immediate.snapshot().orElseThrow().progress());immediate.close();
            var window=springWindow(f,240);f.prepare();window.expand();advance(f,64);
            assertTrue(window.snapshot().orElseThrow().progress()<1);
            var type=com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelSettings.class;
            var change=type.getDeclaredMethod("withDisplay",boolean.class,boolean.class,boolean.class);change.setAccessible(true);
            var state=type.getDeclaredField("settings");state.setAccessible(true);
            state.set(null,change.invoke(type.getMethod("current").invoke(null),false,true,false));
            f.prepare();assertEquals(CollapseState.EXPANDED,window.snapshot().orElseThrow().state());
            window.collapse();f.prepare();assertEquals(CollapseState.COLLAPSED,window.snapshot().orElseThrow().state());
        }
    }
    @Test void springClosesWithWindowAndTreeAndReopeningOwnsFreshTrack() throws Exception {
        try(var preferences=new NativeAnimationPreferences();var f=new Fixture()){
            var first=springWindow(f,240);f.prepare();first.expand();advance(f,64);
            var field=first.getClass().getDeclaredField("spring");field.setAccessible(true);
            var old=(com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation)field.get(first);
            first.close();assertFalse(old.running());assertThrows(IllegalStateException.class,first::expand);
            var second=springWindow(f,240);f.prepare();assertNotSame(old,field.get(second));
            assertEquals(0,second.snapshot().orElseThrow().progress());second.expand();advance(f,64);
            var next=(com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation)field.get(second);
            f.tree.close();assertFalse(second.isOpen());assertFalse(next.running());assertTrue(f.dock.windows(f.module).isEmpty());
        }
    }

}
