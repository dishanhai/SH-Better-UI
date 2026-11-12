// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceClaim;
import com.dishanhai.sh_better_ui.kernel.api.OwnershipLease;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.Subscription;
import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Owns scope generations, shape, and invalidation before cleanup. */
final class ScopeLifecycle {
    private static final UiId KERNEL_ID = new UiId("sh_better_ui", "kernel");

    private final Thread ownerThread = Thread.currentThread();
    private final CleanupManager cleanup;
    private final ResourceOwnership ownership;
    private final Consumer<List<Scope>> invalidated;
    private final Map<Scope, Set<Scope>> mounts = new LinkedHashMap<>();
    private Runnable mutationGuard = () -> {};
    private long generation;
    private final Scope root = new Scope(null, new Identity(ScopeKind.ENGINE, KERNEL_ID), KERNEL_ID, Set.of());

    ScopeLifecycle(BiConsumer<Scope, Throwable> failures) {
        this(failures, scopes -> {}, System::nanoTime);
    }

    ScopeLifecycle(BiConsumer<Scope, Throwable> failures, Consumer<List<Scope>> invalidated, LongSupplier clock) {
        cleanup = new CleanupManager(failures);
        ownership = new ResourceOwnership(cleanup, clock, () -> mutationGuard.run());
        this.invalidated = invalidated;
    }

    void guardMutations(Runnable guard) {
        mutationGuard = guard;
    }

    ResourceOwnership ownership() {
        return ownership;
    }

    Scope root() {
        return root;
    }

    Scope checkedScope(ScopeHandle handle) {
        Scope scope = handle instanceof Scope direct ? direct : handle instanceof Reference ref ? ref.scope() : null;
        if (scope == null || scope.lifecycle() != this) {
            throw new SecurityException("UI scope was not issued by this engine");
        }
        return scope;
    }

    Scope openModule(ModuleDescriptor descriptor) {
        root.requireOpen();
        if (descriptor.id().equals(KERNEL_ID)) {
            throw new IllegalArgumentException("UI module ID is reserved for the engine: " + KERNEL_ID);
        }
        return create(root, new Identity(ScopeKind.MODULE, descriptor.id()), descriptor.id(), descriptor.resourceClaims());
    }

    Scope mountTree(Scope screen, Scope module, UiId id) {
        if (screen.kind() != ScopeKind.SCREEN) {
            throw new IllegalArgumentException("A module tree must mount under a native Screen");
        }
        return mount(screen, module, ScopeKind.UI_TREE, id);
    }

    Scope mountSession(Scope session, Scope module, UiId id) {
        if (session.kind() != ScopeKind.CLIENT_SESSION) {
            throw new IllegalArgumentException("A module session must mount under a native client session");
        }
        return mount(session, module, ScopeKind.CLIENT_SESSION, id);
    }

    private Scope mount(Scope parent, Scope module, ScopeKind kind, UiId id) {
        parent.requireOpen();
        module.requireOpen();
        mutationGuard.run();
        module.requireNamespace(id);
        if (!parent.owner().equals(KERNEL_ID) || module.kind() != ScopeKind.MODULE) {
            throw new SecurityException("Only a running module may mount onto a native UI scope");
        }
        var child = create(parent, new Identity(kind, id), module.owner, module.declared);
        child.mountedFrom = module;
        child.bindAccess(module.access);
        mounts.computeIfAbsent(module, ignored -> new LinkedHashSet<>()).add(child);
        return child;
    }

    Subscription onClose(Scope scope, Runnable release) {
        scope.requireOpen();
        mutationGuard.run();
        var subscription = new Subscription() {
            private volatile Runnable action = Objects.requireNonNull(release);

            @Override public boolean isActive() { return scope.isOpen() && action != null; }

            @Override public void close() {
                scope.assertOwner();
                if (action == null) return;
                var cleanupAction = action;
                action = null;
                cleanup.forget(scope, this);
                cleanupAction.run();
            }
        };
        cleanup.track(scope, subscription, subscription);
        return subscription;
    }

    void beginClose(Scope scope) {
        scope.assertOwner();
        invalidate(descendants(scope));
    }

    void close(Scope scope) {
        scope.assertOwner();
        if (scope.released) {
            return;
        }
        var order = descendants(scope);
        invalidate(order);
        for (Scope child : order) {
            child.released = true;
            // Cleanup may synchronously mount another scope with the same identity.
            if (child.parent != null) {
                child.parent.children.remove(child.identity, child);
            }
            if (child.mountedFrom != null) {
                var owned = mounts.get(child.mountedFrom);
                if (owned != null) {
                    owned.remove(child);
                    if (owned.isEmpty()) mounts.remove(child.mountedFrom);
                }
                child.mountedFrom = null;
            }
        }
        for (int i = order.size() - 1; i >= 0; i--) {
            cleanup.release(order.get(i));
        }
    }

    private void invalidate(List<Scope> order) {
        var closing = new ArrayList<Scope>();
        for (Scope child : order) {
            if (child.open) {
                child.open = false;
                closing.add(child);
            }
        }
        if (!closing.isEmpty()) {
            invalidated.accept(closing);
        }
    }

    private Scope create(Scope parent, Identity identity, UiId owner, Set<ResourceClaim> declared) {
        if (parent.children.containsKey(identity)) {
            throw new IllegalStateException("UI scope is already open: " + identity.id());
        }
        var child = new Scope(parent, identity, owner, declared);
        parent.children.put(identity, child);
        return child;
    }

    private List<Scope> descendants(Scope scope) {
        var order = new ArrayList<Scope>();
        var seen = new LinkedHashSet<Scope>();
        order.add(scope);
        seen.add(scope);
        for (int i = 0; i < order.size(); i++) {
            var parent = order.get(i);
            for (var child : parent.children.values()) if (seen.add(child)) order.add(child);
            for (var mount : mounts.getOrDefault(parent, Set.of())) {
                if (!mount.isWithin(scope) && seen.add(mount)) order.add(mount);
            }
        }
        return order;
    }

    private static boolean allows(ScopeKind parent, ScopeKind child) {
        return switch (parent) {
            case ENGINE, MODULE -> child == ScopeKind.CLIENT_SESSION || child == ScopeKind.SCREEN
                    || child == ScopeKind.RESOURCE_RELOAD;
            case CLIENT_SESSION -> child == ScopeKind.SCREEN || child == ScopeKind.UI_TREE;
            case SCREEN -> child == ScopeKind.UI_TREE;
            case UI_TREE, COMPONENT -> child == ScopeKind.COMPONENT;
            case RESOURCE_RELOAD -> false;
        };
    }

    private record Identity(ScopeKind kind, UiId id) {}

    private record Reference(Scope scope) implements ScopeHandle {
        @Override public UiId id() { return scope.id(); }
        @Override public UiId owner() { return scope.owner(); }
        @Override public ScopeKind kind() { return scope.kind(); }
        @Override public long generation() { return scope.generation(); }
        @Override public boolean isOpen() { return scope.isOpen(); }

        @Override
        public ScopeHandle openChild(ScopeKind kind, UiId id) {
            throw readOnly();
        }

        @Override
        public void track(AutoCloseable resource) {
            throw readOnly();
        }

        @Override
        public OwnershipLease claim(ResourceClaim resource, AutoCloseable release) {
            throw readOnly();
        }

        @Override
        public void close() {
            throw readOnly();
        }

        private SecurityException readOnly() {
            return new SecurityException("UI scope reference cannot manage the lifetime of " + id());
        }
    }

    final class Scope implements ScopeHandle {
        private final Scope parent;
        private final Identity identity;
        private final UiId owner;
        private final Set<ResourceClaim> declared;
        private final long generation = ++ScopeLifecycle.this.generation;
        private final int depth;
        private final ScopeHandle reference = new Reference(this);
        private final Map<Identity, Scope> children = new LinkedHashMap<>();
        private final List<CleanupManager.Binding> resources = new ArrayList<>();
        private volatile boolean open = true;
        private boolean released;
        private ModuleAccess access;
        private Scope mountedFrom;

        private Scope(Scope parent, Identity identity, UiId owner, Set<ResourceClaim> declared) {
            this.parent = parent;
            depth = parent == null ? 0 : parent.depth + 1;
            this.identity = identity;
            this.owner = owner;
            this.declared = declared;
        }

        @Override
        public UiId id() {
            return identity.id();
        }

        @Override
        public UiId owner() {
            return owner;
        }

        @Override
        public ScopeKind kind() {
            return identity.kind();
        }

        @Override
        public long generation() {
            return generation;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public ScopeHandle openChild(ScopeKind kind, UiId id) {
            requireOpen();
            mutationGuard.run();
            Objects.requireNonNull(kind, "kind");
            requireNamespace(Objects.requireNonNull(id, "id"));
            if (!allows(kind(), kind)) {
                throw new IllegalArgumentException("Cannot open " + kind + " under " + kind());
            }
            return create(this, new Identity(kind, id), owner, declared);
        }

        @Override
        public void track(AutoCloseable resource) {
            requireOpen();
            mutationGuard.run();
            cleanup.track(this, resource, resource);
        }

        @Override
        public OwnershipLease claim(ResourceClaim resource, AutoCloseable release) {
            requireOpen();
            mutationGuard.run();
            requireNamespace(Objects.requireNonNull(resource, "resource").id());
            if (!owner.equals(KERNEL_ID) && !declared.contains(resource)) {
                throw new SecurityException("UI resource was not declared by " + owner + ": " + resource);
            }
            return ownership.acquireLegacy(this, principal(), resource, release);
        }

        @Override
        public void close() {
            if (!open) {
                return;
            }
            assertOwner();
            if (kind() == ScopeKind.ENGINE || kind() == ScopeKind.MODULE) {
                throw new IllegalStateException("Only the host closes the " + kind() + " UI scope");
            }
            mutationGuard.run();
            ScopeLifecycle.this.close(this);
        }

        List<CleanupManager.Binding> resources() {
            return resources;
        }

        Scope parent() {
            return parent;
        }

        Scope ancestor(ScopeKind kind) {
            for (Scope current = this; current != null; current = current.parent) {
                if (current.kind() == kind) return current;
            }
            return null;
        }

        ScopeHandle reference() {
            return reference;
        }

        boolean isWithin(Scope ancestor) {
            for (Scope current = this; current != null; current = current.parent) {
                if (current == ancestor) {
                    return true;
                }
            }
            return false;
        }

        void bindAccess(ModuleAccess principal) {
            access = principal;
        }

        private ModuleAccess principal() {
            for (Scope current = this; current != null; current = current.parent) {
                if (current.access != null) return current.access;
            }
            return null;
        }

        int depth() {
            return depth;
        }

        Collection<Scope> children() {
            return children.values();
        }

        private ScopeLifecycle lifecycle() {
            return ScopeLifecycle.this;
        }

        void assertOwner() {
            if (Thread.currentThread() != ownerThread) {
                throw new IllegalStateException("UI scopes must be mutated on the owner thread");
            }
        }

        private void requireOpen() {
            assertOwner();
            if (!open) {
                throw new IllegalStateException("UI scope is closed: " + id());
            }
        }

        private void requireNamespace(UiId id) {
            if (!id.namespace().equals(owner.namespace())) {
                throw new SecurityException(owner + " cannot own UI scope or resource " + id);
            }
        }
    }
}
