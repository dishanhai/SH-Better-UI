// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.indicator.*;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Rect;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Space;
import com.dishanhai.sh_better_ui.kernel.internal.dock.DockLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Screen-scoped native windows. Persistent layout uses module-owned config storage; HUD and composited materials are not advertised. */
final class ClientDockWindows implements WindowService, DockService {
    private final KernelHost host;
    private final ClientScreens screens;
    private final NativeDockPlacements placements = new NativeDockPlacements();
    private final LinkedHashMap<UiId, Window> windows = new LinkedHashMap<>();
    private Screen surface;
    private Window focused, captured;
    private net.minecraft.client.gui.components.events.GuiEventListener previousFocus;
    private Gesture gesture;
    private int capturedButton = -1;
    private long version, solves, layoutNanos, paintNanos, inputNanos;
    private int z;
    private DockLayoutSnapshot presented = DockLayout.solve(0, new Rect(0,0,0,0,Space.GUI), List.of(), 4);

    ClientDockWindows(KernelHost host, ClientScreens screens) { this.host = host; this.screens = screens; }

    @Override public MiniWindowHandle createMiniWindow(ModuleContext module, ScopeHandle scope, MiniWindowSpec spec) {
        var owned = host.ownedScope(module, scope);
        var owner = screens.ownerOf(owned);
        if (owner == null || (scope.kind() != ScopeKind.UI_TREE && scope.kind() != ScopeKind.COMPONENT))
            throw new IllegalArgumentException("Native 26.x mini-windows require an active owned Screen tree");
        var access = host.moduleAccess(module);
        access.require(UIPermission.REGISTER_RENDER_PASS, owned);
        access.require(UIPermission.CAPTURE_INPUT, owned);
        access.require(UIPermission.BLOCK_VANILLA_INPUT, owned);
        if (!spec.id().namespace().equals(module.descriptor().id().namespace())) throw new SecurityException("Window ID must belong to module");
        if (spec.glass() != null || spec.animation().curve() == CollapseSpec.Curve.SPRING)
            throw new UnsupportedOperationException("26.x first-stage windows support plain surfaces and timed collapse; glass and spring collapse are not migrated");
        if (windows.containsKey(spec.id())) throw new IllegalArgumentException("Duplicate window ID");
        if (windows.size() >= 128) throw new IllegalStateException("Window limit reached");
        if (spec.persistent()) access.require(UIPermission.MODIFY_GLOBAL_CONFIG, owned);
        var key = spec.persistent() ? placements.bind(module,spec.id(),initialPlacement(spec)) : null;
        var window = new Window(module, owner, spec, key);
        windows.put(spec.id(), window);
        window.lifetime = host.onScopeClose(owned, window::close);
        return window;
    }
    @Override public Optional<MiniWindowHandle> find(ModuleContext owner, UiId id) {
        host.moduleAccess(owner); var window = windows.get(id);
        return window != null && window.module == owner ? Optional.of(window) : Optional.empty();
    }
    @Override public List<MiniWindowHandle> windows(ModuleContext owner) {
        host.moduleAccess(owner); return windows.values().stream().filter(w -> w.module == owner).map(w -> (MiniWindowHandle)w).toList();
    }
    @Override public DockResult dock(MiniWindowHandle handle, DockRequest request) {
        host.assertOwner();
        if (!(handle instanceof Window window) || windows.get(window.id()) != window) throw new IllegalArgumentException("Unknown window");
        window.placement = window.placement.withRequest(request);
        window.save();
        return new DockResult(true, window.placement);
    }
    @Override public DockLayoutSnapshot layout() { host.assertOwner(); return presented; }
    @Override public DockDiagnostics diagnostics() { host.assertOwner(); return new DockDiagnostics(layoutNanos, paintNanos, inputNanos, solves, presented.entries().size()); }

    /** Call before/after screen switches and when the client loses focus; this never commits a cancelled gesture. */
    void suspend() {
        cancelGesture(); focus(null); surface = null;
        for(var window:windows.values()){window.hover.suspend(host.nowNanos());window.hovered=false;}
        presented = DockLayout.solve(++version, new Rect(0,0,0,0,Space.GUI), List.of(), 4);
    }
    void sync(Screen next, boolean focused) {
        host.assertOwner();
        if (!focused || next != surface) suspend();
    }
    void closeAll() { suspend(); for (var window : List.copyOf(windows.values())) window.close(); }

    void prepare(Screen screen) {
        host.assertOwner();
        if (NativePort.screen(Minecraft.getInstance()) != screen) { suspend(); return; }
        if (surface != screen) { suspend(); surface = screen; }
        if ((gesture != null || captured != null) && (presented.safeArea().width() != screen.width || presented.safeArea().height() != screen.height)) cancelGesture();
        long start = System.nanoTime(), now = host.nowNanos();
        var items = new ArrayList<DockLayout.Item>();
        for (var window : windows.values()) if (window.owner == screen) {
            window.sample(now);
            items.add(new DockLayout.Item(window.id(), window.placement, window.spec.compactWidth(), window.spec.compactHeight(),
                    window.progress, window.spec.priority(), window.z, window.spec.expansionLayout()));
        }
        presented = DockLayout.solve(++version, new Rect(0,0,screen.width,screen.height,Space.GUI), items, 4);
        layoutNanos = System.nanoTime() - start; solves++;
        for (var entry : presented.entries().entrySet()) {
            var window = windows.get(entry.getKey()); if (window != null) window.geometry=entry.getValue();
        }
    }

    void draw(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        prepare(screen);
        if (surface != screen) return;
        long now = host.nowNanos();
        updateHover(mouseX,mouseY,now);
        var context = new Canvas(graphics, screen.width, screen.height, Minecraft.getInstance().getWindow().getGuiScale(), partialTick);
        long paintStart = System.nanoTime();
        try {
            for (var entry : presented.entries().entrySet()) {
                var window = windows.get(entry.getKey()); var geometry = entry.getValue();
                if (window == null || !geometry.visible()) continue;
                window.geometry = geometry;
                var b = geometry.bounds();
                context.fill((int)b.x(),(int)b.y(),(int)(b.x()+b.width()),(int)(b.y()+b.height()),0xf0222530);
                context.fill((int)b.x(),(int)b.y(),(int)(b.x()+b.width()),(int)Math.min(b.y()+20,b.y()+b.height()),window == focused ? 0xff536c97 : 0xff333d50);
                if(!drawCompactIndicator(window.id(),context,now,UiPreferences.configured(),!Minecraft.getInstance().isWindowActive())) {
                    context.clip((int)b.x(),(int)b.y(),(int)(b.x()+b.width()),(int)(b.y()+b.height()),()->
                        graphics.text(Minecraft.getInstance().font, net.minecraft.locale.Language.getInstance().getVisualOrder(Minecraft.getInstance().font.substrByWidth(window.spec.title(), Math.max(0,(int)b.width()-42))),(int)b.x()+5,(int)b.y()+6,0xffffffff,false));
                }
                if (window.progress > .98f) {
                    context.text(Component.literal("×"),(int)(b.x()+b.width())-14,(int)b.y()+6,0xffffffff);
                    var body = window.body();
                    context.clip((int)body.x(),(int)body.y(),(int)(body.x()+body.width()),(int)(body.y()+body.height()), () -> window.content().render(context,window.view()));
                    // Visible diagonal grip establishes the resize affordance.
                    context.fill((int)(b.x()+b.width())-9,(int)(b.y()+b.height())-3,(int)(b.x()+b.width())-2,(int)(b.y()+b.height())-2,0xffaab6cc);
                    context.fill((int)(b.x()+b.width())-5,(int)(b.y()+b.height())-6,(int)(b.x()+b.width())-2,(int)(b.y()+b.height())-5,0xffaab6cc);
                }
            }
        } finally { context.open = false; paintNanos = System.nanoTime()-paintStart; }
    }

    /** Input coordinates and painted geometry are identical. The caller cancels native propagation when true. */
    boolean input(Screen screen, NativeInput event) {
        host.assertOwner();
        if (screen != surface || screen != NativePort.screen(Minecraft.getInstance())) return false;
        long start = System.nanoTime();
        try {
            if (event instanceof NativeInput.Key key) {
                if (key.action() == NativeInput.KeyAction.PRESS) {
                    for (var window : windows.values()) if (window.owner == screen && window.spec.shortcut() == key.key()
                            && window.spec.shortcutModifiers() == (key.modifiers() & 15)
                            && (key.modifiers() != 0 || screen.getFocused() == null)) {
                        window.toggle(); focus(window); return true;
                    }
                    if (key.key() == GLFW.GLFW_KEY_TAB && focused != null) {
                        var choices = windows.values().stream().filter(w -> w.owner == screen && w.isOpen()).toList();
                        int index = choices.indexOf(focused), delta = (key.modifiers() & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1;
                        focus(choices.get(Math.floorMod(index + delta, choices.size()))); focused.expand(); return true;
                    }
                    if (focused != null && (key.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
                        if (key.key() == GLFW.GLFW_KEY_W) { focused.close(); return true; }
                        int dx = key.key() == GLFW.GLFW_KEY_LEFT ? -8 : key.key() == GLFW.GLFW_KEY_RIGHT ? 8 : 0;
                        int dy = key.key() == GLFW.GLFW_KEY_UP ? -8 : key.key() == GLFW.GLFW_KEY_DOWN ? 8 : 0;
                        if (dx != 0 || dy != 0) {
                            if ((key.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) focused.resize(focused.placement.width()+dx,focused.placement.height()+dy);
                            else if (focused.spec.draggable()) { var b=focused.geometry.bounds(); focused.floatAt(b.x()+dx,b.y()+dy); }
                            return true;
                        }
                    }
                }
                if (key.action() == NativeInput.KeyAction.PRESS && key.key() == GLFW.GLFW_KEY_ESCAPE) {
                    if (gesture != null || captured != null) { cancelGesture(); return true; }
                    if (focused != null) { focused.collapse(); focus(null); return true; }
                }
                return focused != null && focused.content != null && focused.content.input(event, focused.view()) != WindowContent.Response.PASS;
            }
            if (!(event instanceof NativeInput.Pointer pointer)) return focused != null && focused.content != null && focused.content.input(event,focused.view()) != WindowContent.Response.PASS;
            // Capture the pointer boundary before an explicit command suppresses hover changes.
            // This samples intent only: input still uses the last presented geometry.
            if(pointer.action()==NativeInput.PointerAction.PRESS)updateHover(pointer.x(),pointer.y(),host.nowNanos());
            if (gesture != null) {
                if (pointer.action() == NativeInput.PointerAction.DRAG) { gesture.update(pointer.x(), pointer.y()); return true; }
                if (pointer.action() == NativeInput.PointerAction.RELEASE && pointer.button() == 0) { gesture.update(pointer.x(),pointer.y());
                    if (!gesture.resize) {
                        var placement = gesture.window.placement;
                        var rect = new Rect(placement.x(),placement.y(),placement.width(),placement.height(),Space.GUI);
                        DockLayout.snap(rect,presented.safeArea(),12).ifPresent(gesture.window::dock);
                    }
                    var completed = gesture.window; gesture = null; completed.holdHoverClock(); completed.save(); return true; }
            }
            if (captured != null) {
                var target = captured;
                try { target.content().input(event,target.view()); }
                finally { if (pointer.action() == NativeInput.PointerAction.RELEASE && pointer.button() == capturedButton) { captured = null; capturedButton = -1; target.holdHoverClock(); } }
                return true;
            }
            var target = pick(pointer.x(),pointer.y());
            if (pointer.action() == NativeInput.PointerAction.PRESS && pointer.button() == 0) {
                for (var window : List.copyOf(windows.values())) if (window.owner == screen && window != target && window.spec.outsideClickCloses()) window.collapse();
                focus(target);
                if (target == null) return false;
                target.z = ++z;
                var b = target.geometry.bounds();
                if (target.progress < .98f) { target.expand(); return true; }
                if (pointer.y() < b.y()+20) {
                    if (pointer.x() >= b.x()+b.width()-20) { target.close(); return true; }
                    if (target.spec.draggable()) { gesture = new Gesture(target,pointer.x(),pointer.y(),false); return true; }
                }
                if (pointer.x() >= b.x()+b.width()-12 && pointer.y() >= b.y()+b.height()-12) { gesture = new Gesture(target,pointer.x(),pointer.y(),true); return true; }
            }
            if (target == null || target.progress < .98f || !contains(target.body(),pointer.x(),pointer.y())) return false;
            var response = target.content().input(event,target.view());
            if (!target.isOpen()) return response != WindowContent.Response.PASS;
            if (response == WindowContent.Response.CAPTURE && pointer.action() == NativeInput.PointerAction.PRESS) { captured = target; capturedButton = pointer.button(); focus(target); }
            else if (response == WindowContent.Response.FOCUS) focus(target);
            return response != WindowContent.Response.PASS;
        } finally { inputNanos = System.nanoTime()-start; }
    }
    boolean drawCompactIndicator(UiId id,RenderService.Context canvas,long now,UiPreferences preferences,boolean lowPower) {
        var window=windows.get(id);
        if(window==null||window.owner!=surface||window.geometry==null||!window.geometry.visible()||window.progress>.98f||window.indicator==null)return false;
        return window.drawIndicator(canvas,window.geometry.bounds(),now,preferences,lowPower);
    }

    /** One topmost hover owner; a small corridor retains, but never acquires, hover. */
    void updateHover(double x,double y,long now) {
        var top=pick(x,y);
        for(var window:windows.values()) {
            if(window.owner!=surface||window.geometry==null||!window.geometry.visible())continue;
            var bounds=window.geometry.bounds();
            boolean inside=top==window||top==null&&window.hover.inside()
                    &&x>=bounds.x()-3&&y>=bounds.y()-3&&x<bounds.x()+bounds.width()+3&&y<bounds.y()+bounds.height()+3;
            window.hovered=top==window;
            if(window.placement.mode()!=DockMode.AUTO_HIDE)continue;
            window.hovered=inside;
            boolean next=window.hover.update(now,inside,window.protectedInteraction(),window.expanded,window.spec.expandOnHover(),
                    java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(window.spec.openDelayMs()),
                    java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(window.spec.closeDelayMs()));
            if(next!=window.expanded)window.target(next,false);
        }
    }
    private Window pick(double x,double y) {
        Window result = null;
        for (var entry : presented.entries().entrySet()) if (entry.getValue().visible() && contains(entry.getValue().bounds(),x,y)) result = windows.get(entry.getKey());
        return result;
    }
    private void focus(Window next) {
        if (focused == next) return;
        var previous = focused;
        if (previous == null && next != null && surface != null) {
            previousFocus = surface.getFocused(); surface.setFocused(null);
        }
        focused = next;
        if (previous != null) {previous.contentFocus(false);previous.holdHoverClock();}
        if (next != null && next.progress > .98f) next.contentFocus(true);
        if (next == null && surface != null && surface == NativePort.screen(Minecraft.getInstance())) {
            if (previousFocus != null && surface.children().contains(previousFocus)) surface.setFocused(previousFocus);
        }
        if (next == null) previousFocus = null;
    }
    private void cancelGesture() {
        if (gesture != null) { gesture.window.placement = gesture.before; gesture.window.holdHoverClock(); gesture = null; }
        if (captured != null) { var old = captured; captured = null; capturedButton = -1; old.content().captureCancelled(); old.holdHoverClock(); }
    }
    static boolean contains(Rect b,double x,double y) { return x>=b.x() && y>=b.y() && x<b.x()+b.width() && y<b.y()+b.height(); }

    private final class Gesture {
        final Window window; final WindowPlacement before; final double x,y,startX,startY; final boolean resize;
        Gesture(Window window,double x,double y,boolean resize) { this.window=window; before=window.placement; this.x=x; this.y=y; this.resize=resize; startX=window.geometry.bounds().x(); startY=window.geometry.bounds().y(); }
        void update(double nx,double ny) {
            if (resize) window.resize(before.width()+nx-x,before.height()+ny-y);
            else {
                window.placement = before.withRequest(DockRequest.floating(startX+nx-x,startY+ny-y));
            }
        }
    }

    private static WindowPlacement initialPlacement(MiniWindowSpec spec) {
        return WindowPlacement.docked(spec.anchor(),spec.autoHide()?DockMode.AUTO_HIDE:DockMode.PINNED,spec.width(),spec.height()).withGroup(spec.group());
    }

    private final class Window implements MiniWindowHandle {
        final ModuleContext module; final Screen owner; final MiniWindowSpec spec; final ConfigKey<WindowPlacement> stored;
        WindowPlacement placement; Subscription lifetime; WindowContent content; DockLayoutSnapshot.Entry geometry;
        final DockHover hover=new DockHover(); final IndicatorRenderer indicator;
        boolean expanded, hovered, contentFocused, indicatorFailed; float progress, from; long changed; int z;
        Window(ModuleContext module,Screen owner,MiniWindowSpec spec,ConfigKey<WindowPlacement> stored) {
            this.module=module; this.owner=owner; this.spec=spec; this.stored=stored;
            indicator=spec.compactIndicator()==null?null:new IndicatorRenderer(spec.compactIndicator().get(),failure->{indicatorFailed=true;host.report("Dock indicator "+spec.id(),failure);});
            reset();
            if(stored!=null){ var saved=NativeDockPlacements.read(module,stored); placement=saved.withSize(Math.clamp(saved.width(),spec.minWidth(),spec.maxWidth()),Math.clamp(saved.height(),spec.minHeight(),spec.maxHeight())).withGroup(spec.group()); }
            expanded=spec.animation().initiallyExpanded(); progress=expanded?1:0; from=progress; changed=host.nowNanos(); z=++ClientDockWindows.this.z;
        }
        void reset() { placement=initialPlacement(spec); }
        void save() {
            if(stored!=null && !(gesture!=null && gesture.window==this))
                NativeDockPlacements.write(module,stored,placement);
        }
        void check() { host.assertOwner(); if (!isOpen()) throw new IllegalStateException("Closed window"); }
        @Override public UiId id() { return spec.id(); }
        @Override public boolean isOpen() { return windows.get(id())==this; }
        void target(boolean target) {target(target,true);}
        void target(boolean target,boolean command) {
            check();
            if(command)hover.command();
            if (!target) {
                if (captured == this || gesture != null && gesture.window == this) cancelGesture();
                if (focused == this) focus(null);
                else contentFocus(false);
            }
            sample(host.nowNanos());
            if (expanded!=target) { from=progress; expanded=target; changed=host.nowNanos(); }
        }
        void sample(long now) {
            long duration=(expanded?spec.animation().expandMillis():spec.animation().collapseMillis())*1_000_000L;
            float t=duration==0||UiPreferences.configured().reducedMotion()?1:Math.min(1,Math.max(0,(float)(now-changed)/duration));
            if (spec.animation().curve()==CollapseSpec.Curve.EASE) t=t*t*(3-2*t);
            progress=from+((expanded?1:0)-from)*t;
            if (expanded && progress > .98f && focused == this) contentFocus(true);
        }
        boolean protectedInteraction(){return placement.pinned()||this==focused||this==captured||gesture!=null&&gesture.window==this;}
        void holdHoverClock(){hover.update(host.nowNanos(),hover.inside(),true,expanded,spec.expandOnHover(),0,0);}
        boolean drawIndicator(RenderService.Context context,Rect bounds,long now,UiPreferences preferences,boolean lowPower){
            if(indicatorFailed)return false;
            var size=indicator.measure(new IndicatorConstraints(Math.max(0,(int)bounds.width()),Math.max(0,(int)bounds.height())));
            if(indicatorFailed||size.width()==0||size.height()==0)return false;
            indicator.render(context,(int)bounds.x(),(int)bounds.y(),size.width(),size.height(),1,
                    new IndicatorContext(now,progress,preferences.reducedMotion(),lowPower));
            return !indicatorFailed;
        }
        void contentFocus(boolean value) {
            if (contentFocused == value) return;
            contentFocused = value;
            if (value) content().focusChanged(true);
            else if (content != null) content.focusChanged(false);
        }
        WindowContent content() { if (content==null) content=Objects.requireNonNull(spec.content().get()); return content; }
        Rect body() { var b=geometry.bounds(); return new Rect(b.x()+2,b.y()+20,Math.max(0,b.width()-4),Math.max(0,b.height()-22),Space.GUI); }
        WindowContent.View view() { return new WindowContent.View(body(),progress,this==focused,0xffffffff); }
        @Override public void expand() { target(true); }
        @Override public void collapse() { target(false); }
        @Override public void toggle() { target(!expanded); }
        @Override public void pin(boolean value) { check(); placement=placement.withPinned(value); if(value)expand(); save(); }
        @Override public void dock(DockEdge edge) { check(); placement=placement.withRequest(DockRequest.edge(edge,DockMode.AUTO_HIDE)); save(); }
        @Override public void floatAt(double x,double y) { check(); placement=placement.withRequest(DockRequest.floating(x,y)); save(); }
        @Override public void detach(double x,double y) { check(); placement=placement.withRequest(DockRequest.detached(x,y)); save(); }
        @Override public void resize(double width,double height) { check(); placement=placement.withSize(Math.clamp(width,spec.minWidth(),spec.maxWidth()),Math.clamp(height,spec.minHeight(),spec.maxHeight())); save(); }
        @Override public void resetPlacement() { check(); reset(); save(); }
        @Override public Optional<WindowSnapshot> snapshot() {
            host.assertOwner(); if (!isOpen() || geometry==null || owner!=surface) return Optional.empty();
            return Optional.of(new WindowSnapshot(presented.version(),geometry,body(),progress==0?CollapseState.COLLAPSED:progress==1?CollapseState.EXPANDED:expanded?CollapseState.EXPANDING:CollapseState.COLLAPSING,progress,expanded,hovered,this==focused,this==captured||gesture!=null&&gesture.window==this));
        }
        @Override public void close() {
            host.assertOwner(); if(!isOpen())return;
            if(captured==this || gesture!=null&&gesture.window==this)cancelGesture();
            if(focused==this)focus(null);
            windows.remove(id()); if(lifetime!=null)lifetime.close();
            if(content!=null){var old=content;content=null;old.close();}
        }
    }

    private static final class Canvas implements RenderService.Context {
        final GuiGraphicsExtractor graphics; final RenderService.Frame frame; final Thread thread=Thread.currentThread(); boolean open=true;
        Canvas(GuiGraphicsExtractor graphics,int width,int height,double scale,float tick) { this.graphics=graphics; frame=new RenderService.Frame(RenderService.Surface.SCREEN,width,height,scale,tick); }
        void check(){if(!open || Thread.currentThread()!=thread)throw new IllegalStateException("Expired dock drawing context");}
        @Override public RenderService.Frame frame(){check();return frame;}
        @Override public void fill(int x,int y,int r,int b,int argb){check();graphics.fill(x,y,r,b,argb);}
        @Override public void text(Component text,int x,int y,int argb){check();graphics.text(Minecraft.getInstance().font,text,x,y,argb,false);}
        @Override public void clip(int x,int y,int r,int b,Runnable draw){check();graphics.enableScissor(x,y,r,b);try{draw.run();}finally{graphics.disableScissor();}}
        @Override public void transform(double x,double y,float scale,Runnable draw){check();graphics.pose().pushMatrix();try{graphics.pose().translate((float)x,(float)y).scale(scale);draw.run();}finally{graphics.pose().popMatrix();}}
    }
}
