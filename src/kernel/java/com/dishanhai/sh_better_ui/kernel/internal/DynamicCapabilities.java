package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class DynamicCapabilities {
    private record Offer(ScopeLifecycle.Scope scope, CapabilityDescriptor descriptor, Handle handle) {}
    private record Publication(CapabilityIndex modules, List<Offer> offers, long generation) {}

    private final ScopeLifecycle scopes;
    private final Supplier<CapabilityIndex> modules;
    private final BiConsumer<String, Throwable> failures;
    private final List<Offer> offers = new ArrayList<>();
    private final List<Watch> watches = new ArrayList<>();
    private volatile Publication published = new Publication(new CapabilityIndex(List.of()), List.of(), 0);

    DynamicCapabilities(ScopeLifecycle scopes, Supplier<CapabilityIndex> modules,
                        BiConsumer<String, Throwable> failures) {
        this.scopes = scopes;
        this.modules = modules;
        this.failures = failures;
    }

    CapabilityResolver view(ModuleAccess access) {
        return view(access, access.defaultScope());
    }

    private CapabilityResolver view(ModuleAccess access, ScopeLifecycle.Scope target) {
        return new CapabilityResolver() {
            private Publication cachedPublication;
            private CapabilityIndex cachedModules;
            private CapabilityIndex cachedIndex;

            private synchronized CapabilityIndex current() {
                access.read(target);
                var snapshot = published;
                var base = modules.get();
                if (cachedPublication != snapshot || cachedModules != base) {
                    cachedIndex = index(new Publication(base, snapshot.offers(), snapshot.generation()), target);
                    cachedPublication = snapshot;
                    cachedModules = base;
                }
                return cachedIndex;
            }

            @Override public CapabilityStatus status(CapabilityKey key) { return current().status(key); }
            @Override public List<CapabilityDescriptor> available() { return current().available(); }
            @Override public CapabilityResolution resolve(CapabilityRequest request) { return current().resolve(request); }
            @Override public CapabilityResolver at(ScopeHandle scope) {
                return view(access, access.read(scope));
            }

            @Override public Subscription provide(ScopeHandle scope, CapabilityKey key,
                                                   ModuleDescriptor.CapabilityOffer offer) {
                access.assertSideEffectAllowed();
                var owner = access.requireIssuer();
                owner.assertOwner();
                var destination = access.write(scope);
                if (!destination.owner().equals(owner.owner())) {
                    throw new SecurityException("Capability offers require an owned scope");
                }
                switch (destination.kind()) {
                    case MODULE, CLIENT_SESSION, SCREEN, UI_TREE, COMPONENT -> {}
                    default -> throw new IllegalArgumentException("Unsupported capability scope: " + destination.kind());
                }
                Objects.requireNonNull(key, "key");
                Objects.requireNonNull(offer, "offer");
                var handle = new Handle(destination, access);
                offers.add(new Offer(destination, new CapabilityDescriptor(key, offer.version(), owner.owner(),
                        offer.priority(), offer.fallback()), handle));
                return handle;
            }

            @Override public Subscription observe(ScopeHandle scope, CapabilityRequest request,
                                                    Consumer<CapabilityChange> listener) {
                access.assertSideEffectAllowed();
                access.requireIssuer().assertOwner();
                var destination = access.read(scope);
                var handle = new Handle(destination, access);
                Objects.requireNonNull(request);
                Objects.requireNonNull(listener);
                var snapshot = published;
                var baseline = index(new Publication(modules.get(), snapshot.offers(), snapshot.generation()), destination)
                        .resolve(request);
                watches.add(new Watch(destination, request, listener, handle, baseline));
                return handle;
            }
        };
    }

    boolean commit() {
        scopes.root().assertOwner();
        offers.removeIf(offer -> !offer.handle().isActive());
        watches.removeIf(watch -> !watch.handle.isActive());
        var before = published;
        var base = modules.get();
        if (before.modules() == base && before.offers().equals(offers)) return false;
        var next = new Publication(base, List.copyOf(offers), before.generation() + 1);
        published = next;
        for (var watch : List.copyOf(watches)) {
            if (!watch.handle.isActive()) continue;
            var previous = watch.previous;
            var current = index(next, watch.scope).resolve(watch.request);
            watch.previous = current;
            if (previous.equals(current)) continue;
            try {
                watch.listener.accept(new CapabilityChange(next.generation(), previous, current));
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                failures.accept("Capability observer at " + watch.scope.id(), failure);
            }
        }
        return true;
    }

    void remove(List<ScopeLifecycle.Scope> closing) {
        offers.removeIf(offer -> closing.contains(offer.scope()));
        watches.removeIf(watch -> closing.contains(watch.scope));
    }

    CapabilityIndex excluding(java.util.Set<UiId> removed) {
        var visible = new ArrayList<CapabilityDescriptor>();
        for (var descriptor : modules.get().available()) {
            if (!removed.contains(descriptor.provider())) visible.add(descriptor);
        }
        for (var offer : published.offers()) {
            // A callback may revoke a handle, but that change belongs to the next publication.
            if (offer.scope().kind() == ScopeKind.MODULE
                    && !removed.contains(offer.descriptor().provider())) visible.add(offer.descriptor());
        }
        return new CapabilityIndex(visible);
    }

    private CapabilityIndex index(Publication publication, ScopeLifecycle.Scope target) {
        var visible = new ArrayList<>(publication.modules().available());
        for (var offer : publication.offers()) {
            if (offer.scope().kind() == ScopeKind.MODULE || target.isWithin(offer.scope())) {
                visible.add(offer.descriptor());
            }
        }
        return new CapabilityIndex(visible);
    }

    private static final class Watch {
        private final ScopeLifecycle.Scope scope;
        private final CapabilityRequest request;
        private final Consumer<CapabilityChange> listener;
        private final Handle handle;
        private CapabilityResolution previous;

        private Watch(ScopeLifecycle.Scope scope, CapabilityRequest request, Consumer<CapabilityChange> listener,
                      Handle handle, CapabilityResolution previous) {
            this.scope = scope;
            this.request = request;
            this.listener = listener;
            this.handle = handle;
            this.previous = previous;
        }
    }

    private static final class Handle implements Subscription {
        private final ScopeLifecycle.Scope scope;
        private final ModuleAccess access;
        private volatile boolean active = true;

        private Handle(ScopeLifecycle.Scope scope, ModuleAccess access) {
            this.scope = scope;
            this.access = access;
        }
        @Override public boolean isActive() {
            return active && scope.isOpen() && access.defaultScope().isOpen();
        }
        @Override public void close() {
            scope.assertOwner();
            access.assertSideEffectAllowed();
            active = false;
        }
    }
}
