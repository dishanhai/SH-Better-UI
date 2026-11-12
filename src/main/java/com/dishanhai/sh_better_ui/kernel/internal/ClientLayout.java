// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.LayoutApi;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.visual.VisualFrameCoordinator;
import com.dishanhai.sh_better_ui.client.visual.VisualLayoutBinding;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockLayoutSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Rect;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Space;
import com.dishanhai.sh_better_ui.kernel.internal.dock.DockLayout;
import com.dishanhai.sh_better_ui.theme.ThemeScope;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

final class ClientLayout implements LayoutService {
    private static final Set<UIDirtyFlag> MEASURE_AND_ARRANGE = Set.of(UIDirtyFlag.MEASURE, UIDirtyFlag.LAYOUT);
    private static final Rect EMPTY_DOCK_AREA = new Rect(0, 0, 0, 0, Space.GUI);
    private final KernelHost host;
    private final Map<ScopeLifecycle.Scope, Work> bindings = new HashMap<>();
    private List<DockLayout.Item> dockItems = List.of();
    private DockLayoutSnapshot dock = DockLayout.solve(0, EMPTY_DOCK_AREA, List.of(), 0);
    private long dockVersion, dockNanos, dockSolves, dockSurface;

    DockLayoutSnapshot solveDock(long surface, Rect safe, List<DockLayout.Item> items) {
        if (dockSurface == surface && dock.safeArea().equals(safe) && dockItems.equals(items)) return dock;
        long start = System.nanoTime();
        dock = DockLayout.solve(++dockVersion, safe, items, 4);
        dockSurface = surface;
        dockItems = List.copyOf(items);
        dockNanos = System.nanoTime() - start;
        dockSolves++;
        return dock;
    }

    DockLayoutSnapshot emptyDock() {
        return solveDock(0, EMPTY_DOCK_AREA, List.of());
    }
    long dockNanos() { return dockNanos; }
    long dockSolves() { return dockSolves; }

    @Override
    public Optional<Rect> dockContentArea(ModuleContext module, ScopeHandle node) {
        var target = host.ownedNode(module, node);
        var screen = target.ancestor(ScopeKind.SCREEN);
        var insets = dock.pushInsets();
        return screen != null && screen.generation() == dockSurface
                && (insets.top() != 0 || insets.right() != 0 || insets.bottom() != 0 || insets.left() != 0)
                ? Optional.of(dock.contentArea()) : Optional.empty();
    }

    private final ClientScreens screens;
    private final Map<Long, VisualLayoutBinding> visualBindings = new HashMap<>();

    ClientLayout(KernelHost host, ClientScreens screens) {
        this.host = host;
        this.screens = screens;
    }

    @Override
    public VisualLayoutBinding bindVisual(ModuleContext module, ScopeHandle node, AlignmentLayout layout,
                                          Consumer<VisualLayoutBinding.FrameTime> sample) {
        var target = host.ownedNode(module, node);
        var owner = screens.ownerOf(target);
        if (owner == null) throw new IllegalStateException("Visual layout requires an active Screen scope");
        long mount = screens.activeScope(owner).generation();
        var existing = visualBindings.get(mount);
        if (existing != null && existing.isActive()) {
            throw new IllegalStateException("The Screen mount already owns a visual layout");
        }
        var binding = VisualFrameCoordinator.bind(owner, mount, target, layout, sample);
        visualBindings.put(mount, binding);
        target.track(() -> visualBindings.remove(mount, binding));
        return binding;
    }

    @Override public LayoutApi layout() { return ShBetterUiApi.layout(); }

    @Override
    public Binding bind(ModuleContext module, ScopeHandle node, UiId writer, Runnable measure, Runnable arrange) {
        var access = host.moduleAccess(module);
        var target = host.dirty().validate(access, node);
        var work = new Work(target, Objects.requireNonNull(measure), Objects.requireNonNull(arrange));
        var claim = new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.LAYOUT_MUTATION, writer);
        work.lease = host.acquire(access, new ResourceRequest(claim, target, 0, 0, ConflictPolicy.REJECT), work::release);
        bindings.put(target, work);
        work.invalidate();
        return work;
    }

    @Override
    public ThemeScope.InvalidationOwner styles(ModuleContext module, ScopeHandle node, ThemeScope theme) {
        var access = host.moduleAccess(module);
        var target = host.dirty().validate(access, node);
        if (!module.scope().owner().equals(target.owner())) {
            throw new SecurityException("Theme bindings require an owned layout node");
        }
        var owner = theme.invalidationOwner(changes -> {
            if (!target.isOpen()) return;
            var flags = EnumSet.noneOf(UIDirtyFlag.class);
            for (var change : changes) {
                switch (change) {
                    case TEXT_MEASURE, RESOURCE -> flags.addAll(MEASURE_AND_ARRANGE);
                    case LAYOUT -> flags.add(UIDirtyFlag.LAYOUT);
                    case PAINT, COMPOSITE -> flags.add(UIDirtyFlag.PAINT);
                }
            }
            host.dirty().invalidate(target, flags);
        });
        target.track(owner);
        return owner;
    }

    void process(UIDirtyFlag flag) {
        for (var node : host.dirty().drain(flag)) {
            var work = bindings.get(node);
            if (work == null || !work.isActive()) continue;
            var category = flag == UIDirtyFlag.MEASURE ? RuntimeMetrics.Category.MEASURE : RuntimeMetrics.Category.LAYOUT;
            try (var sample = host.metrics().begin(category)) {
                (flag == UIDirtyFlag.MEASURE ? work.measure : work.arrange).run();
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                host.metrics().failure(category);
                host.report("UI layout " + node.id() + " at " + flag, failure);
                work.close();
            }
        }
    }

    private final class Work implements Binding {
        private final ScopeLifecycle.Scope target;
        private Runnable measure;
        private Runnable arrange;
        private OwnershipLease lease;

        Work(ScopeLifecycle.Scope target, Runnable measure, Runnable arrange) {
            this.target = target;
            this.measure = measure;
            this.arrange = arrange;
        }

        @Override public boolean isActive() { return lease.isActive(); }
        @Override public void close() { lease.close(); }

        @Override
        public void invalidate() {
            host.assertOwner();
            if (!isActive()) throw new IllegalStateException("UI layout writer was released");
            host.dirty().invalidate(target, MEASURE_AND_ARRANGE);
        }

        void release() {
            bindings.remove(target, this);
            measure = null;
            arrange = null;
        }
    }
}
