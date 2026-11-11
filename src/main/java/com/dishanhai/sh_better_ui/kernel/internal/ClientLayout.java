// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.LayoutApi;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.theme.ThemeScope;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class ClientLayout implements LayoutService {
    private static final Set<UIDirtyFlag> MEASURE_AND_ARRANGE = Set.of(UIDirtyFlag.MEASURE, UIDirtyFlag.LAYOUT);
    private final KernelHost host;
    private final Map<ScopeLifecycle.Scope, Work> bindings = new HashMap<>();

    ClientLayout(KernelHost host) { this.host = host; }

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
