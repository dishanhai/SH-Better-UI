// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.indicator.IndicatorContext;
import com.dishanhai.sh_better_ui.kernel.api.dock.indicator.IndicatorRenderer;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Rect;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Space;
import com.dishanhai.sh_better_ui.kernel.internal.dock.DockLayout;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Shape;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Transform;
import com.dishanhai.sh_better_ui.spatial.SpatialScene;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Material;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Native adapter. ClientLayout solves once, and the presented result also drives routing. */
final class ClientDockWindows implements WindowService, DockService, UIModule {
    private static final UiVersion VERSION = new UiVersion(1, 0, 0);
    private static final UiId TREE = UiId.parse("sh_better_ui:dock/input");
    private static final UiId HUD_TREE = UiId.parse("sh_better_ui:dock/hud");
    private static final UiId POINTER = UiId.parse("sh_better_ui:dock/pointer");
    private static final ModuleDescriptor.ResourceClaim FOCUS = new ModuleDescriptor.ResourceClaim(
            ModuleDescriptor.ResourceKind.KEYBOARD_FOCUS, UiId.parse("sh_better_ui:dock/focus"));
    private static final Set<UIPermission> GRANTS = Set.of(UIPermission.BLOCK_VANILLA_INPUT, UIPermission.CAPTURE_INPUT);
    private static final Rect EMPTY = new Rect(0, 0, 0, 0, Space.GUI);
    private static final int HEADER = 20;
    private static final Material SHADOW_MATERIAL = new Material(0, 1, 1, 0, 0, 0, 0);
    private static final int LIMIT = 128;
    private final KernelHost host;
    private final ClientScreens screens;
    private final ClientInput input;
    private final ClientLayout layout;
    private final ClientCollapse collapses;
    private final ClientDockStore store = new ClientDockStore();
    private final LinkedHashMap<UiId, Window> windows = new LinkedHashMap<>();
    private final Set<Integer> consumedKeys = new HashSet<>();
    private final HashMap<Integer, Window> contentKeys = new HashMap<>();
    private ModuleContext module;
    private ScopeHandle screenTree;
    private ScopeHandle hudTree;
    private Screen screen;
    private Window focused;
    private OwnershipLease focusLease;
    private Object previousNativeFocus;
    private Gesture gesture;
    private boolean visible;
    private double mouseX, mouseY;
    private long paintNanos, inputNanos;
    private long nextNode;
    private DockLayoutSnapshot presented = DockLayout.solve(0, EMPTY, List.of(), 0);
    private DockLayoutSnapshot prepared = presented;
    private List<Window> paintOrder = List.of();

    ClientDockWindows(KernelHost host, ClientScreens screens, ClientInput input, ClientLayout layout,
                      ClientCollapse collapses) {
        this.host = host;
        this.screens = screens;
        this.input = input;
        this.layout = layout;
        this.collapses = collapses;
    }

    ModuleDiscovery.Candidate candidate() {
        return new ModuleDiscovery.Candidate(UIApis.WINDOWS.id().namespace(), getClass().getName(),
                this, GRANTS, false, true);
    }

    @Override public ModuleDescriptor descriptor() {
        var builder = ModuleDescriptor.builder().id(UIApis.WINDOWS.id()).version(VERSION)
                .initializationPhase(ModuleDescriptor.InitializationPhase.SERVICE)
                .claimResource(CollapseService.PROGRESS_CLAIM)
                .claimResource(FOCUS)
                .claimResource(new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.POINTER_CAPTURE, POINTER));
        for (var permission : GRANTS) builder.requestPermission(permission);
        for (var key : List.of(UIApis.COLLAPSE, UIApis.WINDOWS, UIApis.DOCK)) {
            builder.providedCapability(new CapabilityKey(key.id()), VERSION);
        }
        return builder.build();
    }

    @Override public void register(ModuleContext context) {
        register(context, UIApis.COLLAPSE, collapses);
        register(context, UIApis.WINDOWS, this);
        register(context, UIApis.DOCK, this);
    }

    private static <T extends UIService> void register(ModuleContext context, ServiceKey<T> key, T service) {
        context.services().register(new ServiceDescriptor<>(key, VERSION, ServiceDescriptor.Visibility.PUBLIC, 0, false),
                service);
    }

    @Override public void start(ModuleContext context) { module = context; }
    @Override public void stop(ModuleContext context) {
        suspend();
        store.finish();
    }
    ModuleContext context() { return module; }
    void configure(Path config) { store.start(config); }

    @Override
    public MiniWindowHandle createMiniWindow(ModuleContext owner, ScopeHandle scope, MiniWindowSpec spec) {
        var owned = host.ownedScope(owner, scope);
        var nativeScreen = screens.ownerOf(owned);
        boolean hud = screens.ownsSession(owned);
        if (!hud && (nativeScreen == null || owned.kind() != ScopeKind.UI_TREE && owned.kind() != ScopeKind.COMPONENT)) {
            throw new IllegalArgumentException("Mini-windows require an active Screen node or client session");
        }
        if (!spec.id().namespace().equals(owner.descriptor().id().namespace())) {
            throw new SecurityException("Window IDs must use the owning module namespace");
        }
        if (windows.containsKey(spec.id())) throw new IllegalArgumentException("Duplicate mini-window ID " + spec.id());
        if (windows.size() >= LIMIT) throw new IllegalStateException("Mini-window capacity reached");
        if (!hud) bind(nativeScreen);
        var parent = hud ? bindHud() : screenTree;
        var node = parent.openChild(ScopeKind.COMPONENT, new UiId(TREE.namespace(), "dock/window/" + ++nextNode));
        try {
            var window = new Window(owner, scope, nativeScreen, node, spec);
            windows.put(spec.id(), window);
            node.track(window::disposed);
            window.ownerLifetime = host.onScopeClose(owned, window::close);
            return window;
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            node.close();
            throw failure;
        }
    }

    private ScopeHandle bindHud() {
        if (hudTree == null || !hudTree.isOpen()) {
            var session = screens.session(module, HUD_TREE);
            hudTree = session.openChild(ScopeKind.UI_TREE, HUD_TREE);
        }
        return hudTree;
    }

    @Override public Optional<MiniWindowHandle> find(ModuleContext owner, UiId id) {
        host.moduleAccess(owner);
        var window = windows.get(id);
        return window != null && window.owner == owner ? Optional.of(window) : Optional.empty();
    }

    @Override public List<MiniWindowHandle> windows(ModuleContext owner) {
        host.moduleAccess(owner);
        return windows.values().stream().filter(window -> window.owner == owner).map(w -> (MiniWindowHandle) w).toList();
    }

    @Override public DockResult dock(MiniWindowHandle handle, DockRequest request) {
        host.assertOwner();
        if (!(handle instanceof Window window) || windows.get(window.id()) != window || !window.isOpen()) {
            throw new IllegalArgumentException("Unknown or closed mini-window");
        }
        window.place(window.placement.withRequest(request));
        return new DockResult(true, window.placement);
    }

    @Override public DockLayoutSnapshot layout() { host.assertOwner(); return presented; }
    @Override public DockDiagnostics diagnostics() {
        host.assertOwner();
        return new DockDiagnostics(layout.dockNanos(), paintNanos, inputNanos, layout.dockSolves(), paintOrder.size());
    }

    private void bind(Screen owner) {
        if (screen == owner && screenTree != null && screenTree.isOpen()) return;
        screen = owner;
        var tree = screens.mountTrusted(module, owner, TREE);
        screenTree = tree;
        tree.track(() -> {
            if (screenTree != tree) return;
            suspend();
            consumedKeys.clear();
            contentKeys.clear();
            screen = null;
            screenTree = null;
        });
        input.target(module, tree, 550, this::targets);
        module.engine().events().subscribe(InputService.EVENT, tree, UIEventPhase.TARGET, 0, event -> {
            long start = System.nanoTime();
            try {
                if (route(event.payload())) event.consume();
            } finally {
                inputNanos = System.nanoTime() - start;
            }
        });
    }

    void sync(Minecraft minecraft, boolean available) {
        visible = available && (minecraft.screen == null || UiLocalModal.current(minecraft.screen) == null);
        if (!visible || minecraft.screen != screen && minecraft.screen != null) suspend();
        if (focused != null && screen.getFocused() != previousNativeFocus) focus(null);
    }

    void observe(NativeInput event) {
        if (!visible || !(event instanceof NativeInput.Pointer pointer)
                || pointer.action() != NativeInput.PointerAction.PRESS) return;
        var hit = pick(pointer.x(), pointer.y());
        if (hit != focused) focus(null);
        for (var window : paintOrder) {
            if (window != hit && window.spec.outsideClickCloses() && !window.protectedInteraction()) window.collapse();
        }
    }

    void suspend() {
        if (gesture != null && screen != null) UiPointerCapture.cancel(screen);
        focus(null);
        for (var window : paintOrder) window.hover.suspend(host.nowNanos());
        clearPresentation();
    }

    void reload() {
        suspend();
        for (var window : List.copyOf(windows.values())) window.close();
    }

    private void clearPresentation() {
        for (var window : paintOrder) window.painted = null;
        paintOrder = List.of();
        if (!presented.entries().isEmpty()) presented = layout.emptyDock();
    }

    void advance(Minecraft minecraft) {
        store.update((id, placement) -> {
            var window = windows.get(id);
            if (window != null && window.spec.persistent()) window.restore(placement);
        }, failure -> host.report("Dock layout persistence", failure));
        if (!visible || minecraft == null || minecraft.screen != screen || screen == null || windows.isEmpty()) return;
        var nativeWindow = minecraft.getWindow();
        var viewport = screens.viewport();
        mouseX = minecraft.mouseHandler.xpos() * viewport.width() / Math.max(1, nativeWindow.getScreenWidth());
        mouseY = minecraft.mouseHandler.ypos() * viewport.height() / Math.max(1, nativeWindow.getScreenHeight());
        var hovered = pick(mouseX, mouseY);
        long now = host.nowNanos();
        for (var window : paintOrder) {
            if (!window.isOpen() || window.placement.mode() != DockMode.AUTO_HIDE) continue;
            boolean inside = hovered == window || hovered == null && window.hover.inside()
                    && window.painted != null && corridor(window.painted.geometry().bounds(), mouseX, mouseY);
            boolean target = window.fold.targetExpanded();
            boolean next = window.hover.update(now, inside, window.protectedInteraction(), target, window.spec.expandOnHover(),
                    TimeUnit.MILLISECONDS.toNanos(window.spec.openDelayMs()),
                    TimeUnit.MILLISECONDS.toNanos(window.spec.closeDelayMs()));
            if (next != target) window.fold.setExpanded(next);
        }
    }

    private static boolean corridor(Rect bounds, double x, double y) {
        return x >= bounds.x() - 3 && y >= bounds.y() - 3 && x < bounds.right() + 3 && y < bounds.bottom() + 3;
    }

    void prepare(Minecraft minecraft) {
        if (minecraft == null || windows.isEmpty()) {
            prepared = layout.emptyDock();
            return;
        }
        var viewport = screens.viewport();
        boolean hud = minecraft.screen == null;
        double margin = Math.min(6, Math.min(viewport.width(), viewport.height()) * .05);
        double footer = hud ? Math.min(48, viewport.height() * .30) : margin;
        var safe = new Rect(margin, margin, Math.max(0, viewport.width() - 2 * margin),
                Math.max(0, viewport.height() - margin - footer), Space.GUI);
        var items = new ArrayList<DockLayout.Item>();
        if (minecraft.getOverlay() == null && (hud ? !minecraft.options.hideGui : visible)) {
            for (var window : windows.values()) {
                if (window.isOpen() && window.nativeScreen == minecraft.screen) items.add(window.item());
            }
        }
        var nativeScope = screens.activeScope();
        prepared = layout.solveDock(nativeScope == null ? 0 : nativeScope.generation(), safe, items);
        for (var item : items) {
            var window = windows.get(item.id());
            if (window == null || !window.isOpen()) continue;
            window.prepare(prepared.version(), prepared.entries().get(item.id()));
        }
    }

    void render(ClientRender renderer, Screen owner, RenderService.Frame frame) {
        if (windows.isEmpty()) return;
        var local = owner == null ? null : UiLocalModal.current(owner);
        if (owner != null && (local != null || !visible)) {
            clearPresentation();
            return;
        }
        var plan = prepared;
        if (plan.entries().isEmpty()) {
            clearPresentation();
            return;
        }
        var drawWindows = plan.entries().entrySet().stream()
                .sorted(Comparator.<java.util.Map.Entry<UiId, DockLayoutSnapshot.Entry>>comparingInt(e -> e.getValue().zIndex())
                        .thenComparing(java.util.Map.Entry::getKey))
                .filter(e -> e.getValue().visible()).toList();
        var painted = new ArrayList<Window>(drawWindows.size());
        long start = System.nanoTime();
        for (var row : drawWindows) {
            var window = windows.get(row.getKey());
            if (window == null || !window.isOpen() || window.nativeScreen != owner
                    || window.prepared == null || window.prepared.layoutVersion() != plan.version()) continue;
            renderer.paintDock(window.node, frame.surface(), window::paint);
            if (window.isOpen()) painted.add(window);
        }
        if (plan.entries().keySet().stream().anyMatch(id -> !windows.containsKey(id))) {
            for (var window : painted) window.painted = null;
            clearPresentation();
            return;
        }
        for (var window : paintOrder) {
            if (!plan.entries().containsKey(window.id()) || !plan.entries().get(window.id()).visible()) window.painted = null;
        }
        paintOrder = List.copyOf(painted);
        presented = plan;
        paintNanos = System.nanoTime() - start;
    }

    private Window pick(double x, double y) {
        var viewport = screens.viewport();
        if (!viewport.focused() || x < 0 || y < 0 || x >= viewport.width() || y >= viewport.height()) return null;
        for (int i = paintOrder.size() - 1; i >= 0; i--) {
            var window = paintOrder.get(i);
            if (window.isOpen() && window.nativeScreen == screen && window.painted != null
                    && raster(window.painted.geometry().bounds()).contains(x, y)) return window;
        }
        return null;
    }

    private boolean targets(NativeInput value) {
        if (!visible) return false;
        if (value instanceof NativeInput.Pointer pointer) {
            var local = UiLocalModal.current(screen);
            return (local == null || !local.blocksPointer(pointer.x(), pointer.y()))
                    && (pointer.action() == NativeInput.PointerAction.PRESS || pointer.action() == NativeInput.PointerAction.SCROLL)
                    && pick(pointer.x(), pointer.y()) != null;
        }
        if (value instanceof NativeInput.Key key && consumedKeys.contains(key.key())) return true;
        if (focused != null && focused.isOpen() && focused.painted != null) return true;
        return value instanceof NativeInput.Key key && (shortcut(key) != null || dockTab(key));
    }

    private Window shortcut(NativeInput.Key key) {
        if (screen == null || key.action() != NativeInput.KeyAction.PRESS) return null;
        boolean functionKey = key.key() >= GLFW.GLFW_KEY_F1 && key.key() <= GLFW.GLFW_KEY_F25;
        if (screen.getFocused() != null && !functionKey && key.modifiers() == 0) return null;
        for (int i = paintOrder.size() - 1; i >= 0; i--) {
            var window = paintOrder.get(i);
            if (window.isOpen() && window.spec.shortcut() == key.key()
                    && window.spec.shortcutModifiers() == key.modifiers()) return window;
        }
        return null;
    }

    private boolean route(NativeInput value) {
        if (value instanceof NativeInput.Pointer pointer) {
            var window = pick(pointer.x(), pointer.y());
            return window != null && window.pointer(pointer);
        }
        if (value instanceof NativeInput.Character) {
            return focused != null && focused.send(value) != WindowContent.Response.PASS;
        }
        var key = (NativeInput.Key) value;
        if (key.action() == NativeInput.KeyAction.RELEASE) {
            var receiver = contentKeys.remove(key.key());
            if (receiver != null && receiver == focused && receiver.isOpen()) receiver.send(key);
            return consumedKeys.remove(key.key());
        }
        boolean handled = focused != null && focused.key(key);
        if (!handled && dockTab(key)) handled = tab((key.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0);
        if (!handled && key.action() == NativeInput.KeyAction.PRESS) {
            var window = shortcut(key);
            if (window != null) {
                if (focused == window) focus(null);
                window.toggle();
                focus(window.fold.targetExpanded() ? window : null);
                handled = true;
            }
        }
        if (handled) consumedKeys.add(key.key());
        return handled || consumedKeys.contains(key.key());
    }

    private boolean dockTab(NativeInput.Key key) {
        if (key.action() != NativeInput.KeyAction.PRESS || key.key() != GLFW.GLFW_KEY_TAB
                || (key.modifiers() & ~GLFW.GLFW_MOD_SHIFT) != 0 || screen == null || paintOrder.isEmpty()) return false;
        boolean forward = (key.modifiers() & GLFW.GLFW_MOD_SHIFT) == 0;
        return focused != null || screen.nextFocusPath(new FocusNavigationEvent.TabNavigation(forward)) == null;
    }

    private boolean tab(boolean backwards) {
        var order = paintOrder.stream().filter(Window::isOpen)
                .sorted(Comparator.comparingInt((Window window) -> window.spec.priority()).reversed()
                        .thenComparing(Window::id)).toList();
        int at = order.indexOf(focused);
        int next = at < 0 && backwards ? order.size() - 1 : at + (backwards ? -1 : 1);
        if (next < 0 || next >= order.size()) {
            focus(null);
            return false;
        }
        focus(order.get(next));
        return focused != null;
    }

    private void focus(Window next) {
        if (focused == next) return;
        var previous = focused;
        focused = null;
        previousNativeFocus = null;
        contentKeys.values().removeIf(window -> window == previous);
        var oldLease = focusLease;
        focusLease = null;
        if (oldLease != null) oldLease.close();
        if (previous != null) previous.notifyFocus(false);
        if (next != null && next.isOpen()) {
            try {
                focusLease = host.acquire(host.moduleAccess(module),
                        new ResourceRequest(FOCUS, next.node, 0, 0, ConflictPolicy.REJECT),
                        () -> { if (focused == next) focus(null); });
            } catch (IllegalStateException occupied) {
                return;
            }
            focused = next;
            previousNativeFocus = screen.getFocused();
            next.notifyFocus(true);
            if (focused != next) return;
            var narrator = Minecraft.getInstance().getNarrator();
            if (narrator.isActive()) {
                var label = next.spec.title().copy();
                if (next.content != null && !next.contentFailed) next.guard(() -> label.append(next.content.narration()));
                narrator.sayNow(label);
            }
        }
    }

    private static Rect raster(Rect bounds) {
        int x = (int) Math.round(bounds.x()), y = (int) Math.round(bounds.y());
        return new Rect(x, y, Math.max(0, Math.round(bounds.right()) - x),
                Math.max(0, Math.round(bounds.bottom()) - y), Space.GUI);
    }

    private static DockEdge resizeEdge(WindowPlacement placement) {
        return placement.mode().onEdge() ? placement.edge() : null;
    }

    private static Rect resizeHandle(Rect bounds, DockEdge edge) {
        double left = edge == DockEdge.RIGHT ? bounds.x() : bounds.right() - 7;
        double top = edge == DockEdge.BOTTOM ? bounds.y() : bounds.bottom() - 7;
        return new Rect(left, top, 7, edge == DockEdge.BOTTOM ? 5 : 7, Space.GUI);
    }

    private static void clip(RenderService.Context draw, Rect bounds, Runnable action) {
        if (!bounds.empty()) draw.clip((int) bounds.x(), (int) bounds.y(), (int) bounds.right(), (int) bounds.bottom(), action);
    }

    private static int x(Rect bounds) { return (int) bounds.x(); }
    private static int y(Rect bounds) { return (int) bounds.y(); }

    private final class Window implements MiniWindowHandle {
        final ModuleContext owner;
        final ScopeHandle ownerScope, node;
        final Screen nativeScreen;
        final MiniWindowSpec spec;
        final CollapseHandle fold;
        final DockHover hover = new DockHover();
        final IndicatorRenderer indicator;
        final WindowPlacement defaults;
        WindowPlacement placement, previous, preview;
        WindowContent content;
        boolean factoryCalled, contentFailed;
        Subscription ownerLifetime;
        WindowSnapshot prepared, painted;
        SpatialScene scene;
        SpatialScene.Node surface;
        int background, foreground, border, focusBorder;
        boolean spatialFailed;

        Window(ModuleContext owner, ScopeHandle ownerScope, Screen nativeScreen, ScopeHandle node, MiniWindowSpec spec) {
            this.owner = owner;
            this.ownerScope = ownerScope;
            this.nativeScreen = nativeScreen;
            this.node = node;
            this.spec = spec;
            defaults = WindowPlacement.docked(spec.anchor(), spec.autoHide() ? DockMode.AUTO_HIDE : DockMode.EDGE_DOCKED,
                    spec.width(), spec.height()).withGroup(spec.group());
            indicator = spec.compactIndicator() == null ? null : new IndicatorRenderer(spec.compactIndicator().get(),
                    failure -> host.report("Dock indicator " + id(), failure));
            fold = collapses.create(module, node, spec.animation());
            var stored = spec.persistent() ? store.get(id()) : null;
            restore(stored == null ? defaults : stored);
        }

        @Override public UiId id() { return spec.id(); }
        @Override public boolean isOpen() { return node.isOpen() && ownerScope.isOpen(); }
        @Override public Optional<WindowSnapshot> snapshot() { host.assertOwner(); return Optional.ofNullable(painted); }
        @Override public void expand() { set(true); }
        @Override public void collapse() { set(false); }
        @Override public void toggle() { set(!fold.targetExpanded()); }
        @Override public void pin(boolean pinned) {
            var next = placement.withPinned(pinned);
            if (!pinned && next.mode() == DockMode.PINNED) {
                next = next.withRequest(DockRequest.edge(next.edge(),
                        spec.autoHide() ? DockMode.AUTO_HIDE : DockMode.EDGE_DOCKED));
            }
            place(next);
        }
        @Override public void dock(DockEdge edge) {
            ClientDockWindows.this.dock(this, DockRequest.edge(edge, spec.autoHide() ? DockMode.AUTO_HIDE : DockMode.EDGE_DOCKED));
        }
        @Override public void floatAt(double x, double y) { ClientDockWindows.this.dock(this, DockRequest.floating(x, y)); }
        @Override public void detach(double x, double y) { ClientDockWindows.this.dock(this, DockRequest.detached(x, y)); }
        @Override public void resize(double width, double height) { place(sized(placement, width, height)); }
        @Override public void resetPlacement() {
            check();
            previous = placement;
            restore(defaults);
            if (defaults.mode() == DockMode.AUTO_HIDE) fold.setExpanded(spec.animation().initiallyExpanded());
            if (spec.persistent()) store.set(id(), null);
        }
        @Override public void close() { host.assertOwner(); if (node.isOpen()) node.close(); }

        void disposed() {
            if (gesture != null && gesture.window == this && screen != null) UiPointerCapture.cancel(screen);
            if (focused == this) focus(null);
            windows.remove(id(), this);
            if (presented.entries().containsKey(id())) clearPresentation();
            if (ownerLifetime != null) ownerLifetime.close();
            painted = null;
            prepared = null;
            var closed = content;
            content = null;
            scene = null;
            surface = null;
            if (closed != null) guard(closed::close);
        }

        void check() {
            host.assertOwner();
            if (!isOpen()) throw new IllegalStateException("Closed mini-window " + id());
        }

        boolean protectedInteraction() { return placement.pinned() || focused == this || gesture != null && gesture.window == this; }

        void set(boolean expanded) {
            check();
            if (!expanded && protectedInteraction()) return;
            hover.command();
            fold.setExpanded(expanded);
        }

        void place(WindowPlacement next) {
            check();
            if (next.equals(placement)) return;
            previous = placement;
            restore(next);
            if (spec.persistent()) store.set(id(), placement);
        }

        void restore(WindowPlacement next) {
            placement = sized(next, next.width(), next.height());
            if (placement.pinned() || placement.mode() != DockMode.AUTO_HIDE) fold.expand();
        }

        WindowPlacement sized(WindowPlacement current, double width, double height) {
            if (!Double.isFinite(width) || !Double.isFinite(height)) throw new IllegalArgumentException("Nonfinite window size");
            return current.withSize(Math.max(spec.minWidth(), Math.min(spec.maxWidth(), width)),
                    Math.max(spec.minHeight(), Math.min(spec.maxHeight(), height)));
        }

        DockLayout.Item item() {
            int z = spec.priority() + (focused == this || gesture != null && gesture.window == this ? 1000 : 0);
            return new DockLayout.Item(id(), preview == null ? placement : preview, spec.compactWidth(), spec.compactHeight(),
                    fold.progress(), spec.priority(), z, spec.expansionLayout());
        }

        void prepare(long version, DockLayoutSnapshot.Entry geometry) {
            if (geometry == null || !geometry.visible()) {
                prepared = null;
                return;
            }
            var body = raster(geometry.bounds());
            float progress = fold.progress();
            int top = y(body), bottom = (int) body.bottom();
            int contentTop = Math.min(bottom, top + HEADER);
            var contentBounds = new Rect(body.x(), contentTop, body.width(), bottom - contentTop, Space.GUI);
            var prefs = UiPreferences.configured();
            boolean hasChrome = progress >= .35f && body.width() >= 62 && body.height() >= HEADER;
            if (!hasChrome) contentBounds = EMPTY;
            prepared = new WindowSnapshot(version, geometry, contentBounds, fold.state(), progress, fold.targetExpanded(),
                    hover.inside(), focused == this, gesture != null && gesture.window == this);
            var theme = owner.engine().require(UIApis.THEME).snapshot();
            background = prefs.highContrast() ? 0xff101010 : theme.resolve(ThemeTokens.SURFACE_OVERLAY).argb();
            foreground = prefs.highContrast() ? 0xffffffff : theme.resolve(ThemeTokens.TEXT).argb();
            border = theme.resolve(ThemeTokens.BORDER).argb();
            focusBorder = theme.resolve(ThemeTokens.BORDER_FOCUSED).argb();
            if (spec.glass() != null && prefs.useBackdropBlur()) foreground = spec.glass().foreground().argb();
            if (progress > 0 && !prefs.highContrast() && !body.empty() && body.right() <= 32768 && body.bottom() <= 32768) {
                if (!spatialFailed) {
                    try {
                        prepareSurface(body, progress);
                    } catch (RuntimeException | LinkageError | AssertionError failure) {
                        spatialFailed = true;
                        host.report("Dock spatial preparation " + id(), failure);
                    }
                }
            } else if (surface != null) {
                surface.visible(false);
            }
        }

        void prepareSurface(Rect body, float progress) {
            var shape = Shape.rounded(body.width(), body.height(), 5 + 4 * progress);
            var transform = new Transform(body.x(), body.y(), 1, 1, 0, 0, 0);
            if (scene == null) {
                scene = module.engine().require(UIApis.SPATIAL).mount(module, node);
                surface = scene.create(new SpatialScene.Spec(id(), null, shape, transform, 0, 0, false, true));
                surface.material(SHADOW_MATERIAL);
            } else {
                if (!surface.shape().equals(shape)) surface.shape(shape);
                if (!surface.transform().equals(transform)) surface.transform(transform);
            }
            surface.visible(true);
            surface.elevation(2 + 4 * progress);
        }

        void paint(RenderService.Context draw) {
            painted = prepared;
            var geometry = painted.geometry();
            var body = raster(geometry.bounds());
            var compact = raster(geometry.compactBounds());
            var contentBounds = painted.contentBounds();
            float progress = painted.progress();
            int left = x(body), top = y(body), right = (int) body.right(), bottom = (int) body.bottom();
            var prefs = UiPreferences.configured();
            if (body.empty()) return;
            if (scene != null && !spatialFailed && progress > 0 && !prefs.highContrast()) {
                try {
                    draw.spatial(scene);
                } catch (RuntimeException | LinkageError | AssertionError failure) {
                    spatialFailed = true;
                    host.report("Dock spatial rendering " + id(), failure);
                }
            }
            if (spec.glass() != null && prefs.useBackdropBlur()) {
                draw.glass(List.of(new GlassDraw(GlassRegion.rounded(left, top, (float) body.width(), (float) body.height(),
                        5 + 4 * progress), spec.glass())));
            } else {
                draw.fill(left, top, right, bottom, background);
            }
            int line = painted.focused() || painted.pointerCaptured() ? focusBorder : border;
            draw.fill(left, top, right, top + 1, line);
            draw.fill(left, bottom - 1, right, bottom, line);
            draw.fill(left, top, left + 1, bottom, line);
            draw.fill(right - 1, top, right, bottom, line);
            if (contentBounds.empty()) {
                if (indicator != null && !compact.empty()) {
                    float alpha = Math.max(.12f, 1 - progress / .35f);
                    indicator.render(draw, x(compact), y(compact), (int) compact.width(), (int) compact.height(), alpha,
                            new IndicatorContext(host.nowNanos(), progress, prefs.reducedMotion(), !screens.viewport().focused()));
                } else {
                    clip(draw, compact, () -> draw.text(spec.title(), x(compact) + 4, y(compact) + 6, foreground));
                }
                return;
            }
            var titleBounds = new Rect(left + 4, top + 2, body.width() - 46, HEADER - 3, Space.GUI);
            clip(draw, titleBounds, () -> draw.text(spec.title(), left + 5, top + 6, foreground));
            draw.fill(right - 32, top + 5, right - 25, top + 12, geometry.placement().pinned() ? focusBorder : foreground);
            draw.fill(right - 29, top + 12, right - 28, top + 15, foreground);
            draw.fill(right - 15, top + 9, right - 6, top + 11, foreground);
            if (spec.draggable()) {
                var handle = resizeHandle(body, resizeEdge(geometry.placement()));
                draw.fill(x(handle) + 2, y(handle) + 2, x(handle) + 6, y(handle) + 3, foreground);
            }
            if (!factoryCalled) {
                factoryCalled = true;
                guard(() -> {
                    var created = java.util.Objects.requireNonNull(spec.content().get());
                    if (!isOpen()) {
                        created.close();
                        return;
                    }
                    content = created;
                    if (focused == this) notifyFocus(true);
                });
            }
            if (content != null && !contentFailed) {
                var view = view();
                clip(draw, contentBounds, () -> guard(() -> content.render(draw, view)));
            }
            if (gesture != null && gesture.window == this) gesture.drawSnap(draw);
        }

        WindowContent.View view() {
            var snapshot = painted;
            return new WindowContent.View(snapshot.contentBounds(), snapshot.progress(), focused == this, foreground);
        }

        void guard(Runnable work) {
            try {
                work.run();
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                contentFailed = true;
                host.report("Dock content " + id(), failure);
            }
        }

        void notifyFocus(boolean active) {
            if (content != null && !contentFailed) guard(() -> content.focusChanged(active));
        }

        WindowContent.Response send(NativeInput value) {
            if (painted == null || painted.contentBounds().empty() || content == null || contentFailed) {
                return WindowContent.Response.PASS;
            }
            try {
                return java.util.Objects.requireNonNull(content.input(value, view()));
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                contentFailed = true;
                host.report("Dock input " + id(), failure);
                return WindowContent.Response.PASS;
            }
        }

        boolean pointer(NativeInput.Pointer pointer) {
            var snapshot = painted;
            if (snapshot == null) return false;
            var bounds = raster(snapshot.geometry().bounds());
            if (snapshot.contentBounds().empty()) {
                if (pointer.action() != NativeInput.PointerAction.PRESS || pointer.button() != 0) return false;
                toggle();
                return true;
            }
            boolean press = pointer.action() == NativeInput.PointerAction.PRESS;
            if (press && pointer.button() == 0 && spec.draggable()
                    && resizeHandle(bounds, resizeEdge(snapshot.geometry().placement())).contains(pointer.x(), pointer.y())) {
                return capture(this, pointer, bounds, DragKind.RESIZE);
            }
            if (press && pointer.button() == 0 && pointer.y() < bounds.y() + HEADER) {
                if (pointer.x() >= bounds.right() - 20) {
                    pin(false);
                    focus(null);
                    collapse();
                } else if (pointer.x() >= bounds.right() - 40) {
                    pin(!placement.pinned());
                } else if (spec.draggable()) {
                    return capture(this, pointer, bounds, DragKind.MOVE);
                }
                return true;
            }
            if (!snapshot.contentBounds().contains(pointer.x(), pointer.y())) return false;
            var response = send(pointer);
            if (!isOpen()) return response != WindowContent.Response.PASS;
            if (response == WindowContent.Response.FOCUS && press) {
                focus(this);
                if (focused != this) notifyFocus(false);
            }
            if (response == WindowContent.Response.CAPTURE && press) {
                if (!capture(this, pointer, bounds, DragKind.CONTENT) && content != null) guard(content::captureCancelled);
            }
            return response != WindowContent.Response.PASS;
        }

        boolean key(NativeInput.Key key) {
            if (gesture != null && gesture.window == this && key.key() == GLFW.GLFW_KEY_ESCAPE) {
                UiPointerCapture.cancel(screen);
                return true;
            }
            if (send(key) != WindowContent.Response.PASS) {
                if (isOpen() && focused == this) contentKeys.put(key.key(), this);
                return true;
            }
            if (!isOpen() || key.action() != NativeInput.KeyAction.PRESS) return false;
            if (key.key() == GLFW.GLFW_KEY_ESCAPE) {
                focus(null);
                if (!placement.pinned()) collapse();
                return true;
            }
            if ((key.key() == GLFW.GLFW_KEY_ENTER || key.key() == GLFW.GLFW_KEY_SPACE)
                    && painted != null && painted.contentBounds().empty() && !fold.targetExpanded()) {
                expand();
                return true;
            }
            if (key.key() == GLFW.GLFW_KEY_Z && (key.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0 && previous != null) {
                place(previous);
                return true;
            }
            return false;
        }
    }

    private enum DragKind { MOVE, RESIZE, CONTENT }

    private boolean capture(Window window, NativeInput.Pointer press, Rect bounds, DragKind kind) {
        var next = new Gesture(window, press, bounds, kind);
        if (!input.capture(module, window.node, POINTER, press.button(), next)) return false;
        gesture = next;
        if (kind != DragKind.CONTENT) focus(window);
        return true;
    }

    private final class Gesture implements UiPointerCapture.Handler {
        final Window window;
        final NativeInput.Pointer press;
        final Rect bounds;
        final DragKind kind;
        final DockEdge resizeEdge;
        double lastX, lastY;
        boolean moved;

        Gesture(Window window, NativeInput.Pointer press, Rect bounds, DragKind kind) {
            this.window = window;
            this.press = press;
            this.bounds = bounds;
            this.kind = kind;
            this.resizeEdge = resizeEdge(window.painted.geometry().placement());
            lastX = press.x();
            lastY = press.y();
        }

        @Override public void drag(double x, double y) {
            if (!window.isOpen()) return;
            double dx = x - press.x(), dy = y - press.y();
            moved |= Math.abs(dx) >= 3 || Math.abs(dy) >= 3;
            if (kind == DragKind.CONTENT) {
                window.send(new NativeInput.Pointer(NativeInput.PointerAction.DRAG, x, y, press.button(), x - lastX, y - lastY));
                lastX = x;
                lastY = y;
            } else if (moved) {
                window.preview = kind == DragKind.MOVE
                        ? window.placement.withRequest(DockRequest.floating(bounds.x() + dx, bounds.y() + dy))
                        : window.sized(window.placement, bounds.width() + (resizeEdge == DockEdge.RIGHT ? -dx : dx),
                                bounds.height() + (resizeEdge == DockEdge.BOTTOM ? -dy : dy));
            }
        }

        @Override public void release(double x, double y) {
            gesture = null;
            var proposed = window.preview;
            window.preview = null;
            if (!window.isOpen()) return;
            if (kind == DragKind.CONTENT) {
                window.send(new NativeInput.Pointer(NativeInput.PointerAction.RELEASE, x, y, press.button(), 0, 0));
            } else if (proposed != null) {
                if (kind == DragKind.MOVE) {
                    var edge = snap(proposed);
                    if (edge.isPresent()) {
                        proposed = proposed.withRequest(DockRequest.edge(edge.get(),
                                window.spec.autoHide() ? DockMode.AUTO_HIDE : DockMode.EDGE_DOCKED));
                    }
                }
                window.place(proposed);
            }
        }

        Optional<DockEdge> snap(WindowPlacement proposed) {
            return DockLayout.snap(new Rect(proposed.x(), proposed.y(), bounds.width(), bounds.height(), Space.GUI),
                    presented.safeArea(), 14);
        }

        void drawSnap(RenderService.Context draw) {
            if (kind != DragKind.MOVE || window.preview == null) return;
            var target = snap(window.preview);
            if (target.isEmpty()) return;
            var area = raster(presented.safeArea());
            int left = x(area), top = y(area), right = (int) area.right(), bottom = (int) area.bottom();
            int color = window.focusBorder;
            switch (target.get()) {
                case LEFT -> draw.fill(left, top, left + 2, bottom, color);
                case RIGHT -> draw.fill(right - 2, top, right, bottom, color);
                case TOP -> draw.fill(left, top, right, top + 2, color);
                case BOTTOM -> draw.fill(left, bottom - 2, right, bottom, color);
            }
        }

        @Override public void cancel() {
            gesture = null;
            window.preview = null;
            if (kind == DragKind.CONTENT && window.content != null) window.guard(window.content::captureCancelled);
        }
    }
}
