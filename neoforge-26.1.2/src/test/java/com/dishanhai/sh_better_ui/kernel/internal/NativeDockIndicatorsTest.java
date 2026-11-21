// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.indicator.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

final class NativeDockIndicatorsTest {
    static final class Fixture implements AutoCloseable {
        final NativeTestClient client=NativeTestClient.install();final AtomicLong clock=new AtomicLong();
        final KernelHost host=new KernelHost(ModuleDescriptor.Platform.NEOFORGE_26_1_2,new EnginePublication(),clock::get,null,SchedulerBudget.DEFAULT);
        final ClientScreens screens=new ClientScreens(host,ModuleDescriptor.Platform.NEOFORGE_26_1_2);
        final ClientDockWindows dock=new ClientDockWindows(host,screens);
        final Screen screen=new Screen(Component.empty()){};
        ModuleContext module;ScopeHandle tree;
        Fixture()throws Exception {
            var grants=Set.of(UIPermission.REGISTER_RENDER_PASS,UIPermission.CAPTURE_INPUT,UIPermission.BLOCK_VANILLA_INPUT);
            var plugin=new UIModule(){
                public ModuleDescriptor descriptor(){var b=ModuleDescriptor.builder().id(UiId.parse("example:dock")).version("1.0.0").platforms(Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2));grants.forEach(b::requestPermission);return b.build();}
                public void register(ModuleContext value){} public void start(ModuleContext value){module=value;}public void stop(ModuleContext value){}
            };
            host.startNative(List.of(new ModuleDiscovery.Candidate("example","test",plugin,grants,false)));
            assertNotNull(module);client.screen(screen);screen.width=800;screen.height=500;screens.show(screen);
            tree=screens.mountTrusted(module,screen,UiId.parse("example:tree"));
        }
        MiniWindowSpec.Builder spec(String name){return MiniWindowSpec.builder("example:"+name).autoHide(true).openDelayMs(100).closeDelayMs(200)
                .animation(new CollapseSpec(CollapseSpec.Direction.HORIZONTAL,CollapseSpec.Curve.LINEAR,0,0,false,false)).content(()->(draw,view)->{});}
        MiniWindowHandle make(String name){return dock.createMiniWindow(module,tree,spec(name).build());}
        void hover(double x,double y,long ms){clock.set(ms*1_000_000L);dock.prepare(screen);dock.updateHover(x,y,clock.get());dock.prepare(screen);}
        void prepare(){dock.prepare(screen);}
        public void close()throws Exception{try{dock.closeAll();host.close();}finally{client.close();}}
    }
    static final class Canvas implements RenderService.Context {
        int clips,fills;int[] clip,fill;
        public RenderService.Frame frame(){return new RenderService.Frame(RenderService.Surface.SCREEN,800,500,1,0);}
        public void fill(int x,int y,int right,int bottom,int argb){fills++;fill=new int[]{x,y,right,bottom};}
        public void clip(int x,int y,int right,int bottom,Runnable action){clips++;clip=new int[]{x,y,right,bottom};action.run();}
        public void transform(double x,double y,float scale,Runnable action){action.run();}
    }
    @Test void indicatorMeasurementClipsWithoutChangingWindowHitBoundsAndRespectsCadence()throws Exception {
        try(var f=new Fixture()){
            int[] calls=new int[3];boolean[] reduced={false};
            var indicator=new CompactIndicator(){
                public IndicatorSize measure(IndicatorConstraints c){calls[0]++;return new IndicatorSize(999,999);}
                public void update(IndicatorContext c){calls[1]++;reduced[0]=c.reducedMotion();}
                public void render(IndicatorRenderContext c){calls[2]++;c.fill(-50,-50,999,999,0xffffffff);}
            };
            var w=f.dock.createMiniWindow(f.module,f.tree,f.spec("indicator").compactIndicator(()->indicator).build());f.prepare();
            var before=w.snapshot().orElseThrow().geometry().bounds();var canvas=new Canvas();var prefs=new UiPreferences(false,true,false);
            assertTrue(f.dock.drawCompactIndicator(w.id(),canvas,0,prefs,true));
            assertEquals((int)before.width(),canvas.clip[2]-canvas.clip[0]);assertArrayEquals(canvas.clip,canvas.fill);assertTrue(reduced[0]);
            f.dock.drawCompactIndicator(w.id(),canvas,100_000_000L,prefs,true);assertEquals(1,calls[1]);assertEquals(1,calls[0]);
            f.dock.drawCompactIndicator(w.id(),canvas,250_000_000L,prefs,true);assertEquals(2,calls[1]);assertEquals(before,w.snapshot().orElseThrow().geometry().bounds());
            assertTrue(f.dock.input(f.screen,NativeDockWindowsTest.pointer(NativeInput.PointerAction.PRESS,before.x()+1,before.y()+1)));
            f.prepare();assertTrue(w.snapshot().orElseThrow().targetExpanded());
            assertFalse(f.dock.drawCompactIndicator(w.id(),canvas,300_000_000L,prefs,false));
        }
    }
    @Test void failingIndicatorIsDisabledOnceWithoutClosingWindow()throws Exception {
        try(var f=new Fixture()){
            int[] calls={0};var indicator=new CompactIndicator(){public IndicatorSize measure(IndicatorConstraints c){return c.fit(10,10);}public void render(IndicatorRenderContext c){calls[0]++;throw new IllegalStateException("fixture callback");}};
            var w=f.dock.createMiniWindow(f.module,f.tree,f.spec("broken").compactIndicator(()->indicator).build());f.prepare();
            var canvas=new Canvas();var prefs=new UiPreferences(false,false,false);
            assertDoesNotThrow(()->f.dock.drawCompactIndicator(w.id(),canvas,0,prefs,false));
            assertFalse(f.dock.drawCompactIndicator(w.id(),canvas,1,prefs,false));assertEquals(1,calls[0]);assertTrue(w.isOpen());
        }
    }
    @Test void explicitCollapseStaysClosedUntilHoverLeavesAndReenters()throws Exception {
        try(var f=new Fixture()){
            var w=f.make("hover");f.prepare();var b=w.snapshot().orElseThrow().geometry().bounds();double x=b.x()+1,y=b.y()+1;
            f.hover(x,y,0);f.hover(x,y,100);assertTrue(w.snapshot().orElseThrow().targetExpanded());
            w.collapse();f.hover(x,y,500);assertFalse(w.snapshot().orElseThrow().targetExpanded());
            f.hover(700,450,600);f.hover(x,y,700);f.hover(x,y,800);assertTrue(w.snapshot().orElseThrow().targetExpanded());
        }
    }
    @Test void corridorRetainsOnlyPriorHoverAndSuspendRestartsDelay()throws Exception {
        try(var f=new Fixture()){
            var w=f.make("corridor");f.prepare();var b=w.snapshot().orElseThrow().geometry().bounds();
            f.hover(b.x()+b.width()+2,b.y()+1,0);f.hover(b.x()+b.width()+2,b.y()+1,200);assertFalse(w.snapshot().orElseThrow().targetExpanded());
            f.hover(b.x()+1,b.y()+1,300);f.hover(b.x()+b.width()+2,b.y()+1,400);assertTrue(w.snapshot().orElseThrow().targetExpanded());
            w.collapse();f.dock.suspend();f.hover(b.x()+1,b.y()+1,1000);assertFalse(w.snapshot().orElseThrow().targetExpanded());
            f.hover(b.x()+1,b.y()+1,1100);assertTrue(w.snapshot().orElseThrow().targetExpanded());
        }
    }
    @Test void topmostHoverIsReportedForPinnedAndFloatingWindows()throws Exception {
        try(var f=new Fixture()){
            var lower=f.make("lower");var upper=f.make("upper");lower.floatAt(100,100);upper.floatAt(100,100);
            lower.pin(true);upper.pin(true);f.prepare();f.hover(110,110,0);
            assertFalse(lower.snapshot().orElseThrow().hovered());assertTrue(upper.snapshot().orElseThrow().hovered());
        }
    }
    @Test void emptyIndicatorFallsBackToTitle()throws Exception {
        try(var f=new Fixture()){
            var empty=new CompactIndicator(){public IndicatorSize measure(IndicatorConstraints c){return new IndicatorSize(0,0);}public void render(IndicatorRenderContext c){fail("empty indicator must not render");}};
            var w=f.dock.createMiniWindow(f.module,f.tree,f.spec("empty").compactIndicator(()->empty).build());f.prepare();
            assertFalse(f.dock.drawCompactIndicator(w.id(),new Canvas(),0,UiPreferences.DEFAULT,false));
        }
    }
    @Test void focusReleaseStartsFreshCloseDelay()throws Exception {
        try(var f=new Fixture()){
            var w=f.dock.createMiniWindow(f.module,f.tree,f.spec("focus").outsideClickCloses(false).build());
            f.prepare();var b=w.snapshot().orElseThrow().geometry().bounds();
            f.dock.input(f.screen,NativeDockWindowsTest.pointer(NativeInput.PointerAction.PRESS,b.x()+1,b.y()+1));
            f.hover(700,450,1000);assertTrue(w.snapshot().orElseThrow().targetExpanded());
            f.dock.input(f.screen,NativeDockWindowsTest.pointer(NativeInput.PointerAction.PRESS,700,450));
            f.hover(700,450,1199);assertTrue(w.snapshot().orElseThrow().targetExpanded());
            f.hover(700,450,1200);assertFalse(w.snapshot().orElseThrow().targetExpanded());
        }
    }
    @Test void reducedMotionSnapsTimedFold(@org.junit.jupiter.api.io.TempDir java.nio.file.Path config)throws Exception {
        try(var f=new Fixture()){
            var type=com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelSettings.class;
            var state=type.getDeclaredField("settings");state.setAccessible(true);var oldState=state.get(null);
            var path=type.getDeclaredField("file");path.setAccessible(true);var oldPath=path.get(null);
            java.nio.file.Files.createDirectories(config.resolve("sh_better_ui"));
            java.nio.file.Files.writeString(config.resolve("sh_better_ui/control-panel.properties"),"reducedMotion=true\n");
            try{
                var load=type.getDeclaredMethod("load",java.nio.file.Path.class);load.setAccessible(true);load.invoke(null,config);
                var w=f.dock.createMiniWindow(f.module,f.tree,f.spec("motion")
                    .animation(new CollapseSpec(CollapseSpec.Direction.HORIZONTAL,CollapseSpec.Curve.EASE,500,500,false,false)).build());
                w.expand();f.prepare();assertEquals(1,w.snapshot().orElseThrow().progress());
                w.collapse();f.prepare();assertEquals(0,w.snapshot().orElseThrow().progress());
            }finally{state.set(null,oldState);path.set(null,oldPath);}
        }
    }

    @Test void springHoverRepeatedFramesKeepOneTransitionAndRespectLeaveDelay()throws Exception {
        try(var preferences=new NativeAnimationPreferences();var f=new Fixture()){
            var window=f.dock.createMiniWindow(f.module,f.tree,f.spec("spring_hover")
                    .animation(new CollapseSpec(CollapseSpec.Direction.HORIZONTAL,CollapseSpec.Curve.SPRING,240,200,false,false)).build());
            f.prepare();var bounds=window.snapshot().orElseThrow().geometry().bounds();double x=bounds.x()+1,y=bounds.y()+1;
            f.hover(x,y,0);f.hover(x,y,100);assertTrue(window.snapshot().orElseThrow().targetExpanded());
            for(int i=1;i<=200;i++)f.hover(x,y,100+i*16);
            assertEquals(CollapseState.EXPANDED,window.snapshot().orElseThrow().state(),"Hover must not restart the spring every frame");
            f.hover(700,450,3400);f.hover(700,450,3599);assertTrue(window.snapshot().orElseThrow().targetExpanded());
            f.hover(700,450,3600);assertFalse(window.snapshot().orElseThrow().targetExpanded());
            for(int i=1;i<=200;i++)f.hover(700,450,3600+i*16);
            assertEquals(CollapseState.COLLAPSED,window.snapshot().orElseThrow().state());
        }
    }

}
