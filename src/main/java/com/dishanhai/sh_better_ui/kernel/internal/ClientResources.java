// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentAssets;
import com.dishanhai.sh_better_ui.kernel.api.*;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

final class ClientResources implements ResourcesService {
    private static final UiId RELOAD_ID = UiId.parse("sh_better_ui:native/reload");
    private final KernelHost host;
    private final AtomicLong preparing = new AtomicLong();
    private final Set<AlignmentAssets> caches = new LinkedHashSet<>();
    private final Set<Watch> watchers = new LinkedHashSet<>();
    private record Address(ScopeLifecycle.Scope scope, UiId id) {}
    private record Value(long generation, Object data) {}
    private record Published(long generation, java.util.Map<DataBinding<?>, Value> values) {}
    record Prepared(long ticket, java.util.Map<DataBinding<?>, Object> values) {}
    private final java.util.Map<Address, DataBinding<?>> datasets = new java.util.LinkedHashMap<>();
    private volatile List<DataBinding<?>> registrations = List.of();
    private volatile Published published = new Published(0, java.util.Map.of());
    private boolean applying;

    ClientResources(KernelHost host) { this.host = host; }

    @Override public long version() { return published.generation(); }

    long begin() { return preparing.incrementAndGet(); }

    java.util.concurrent.CompletableFuture<Prepared> prepare(ResourceManager manager,
                                                            java.util.concurrent.Executor executor) {
        long ticket = begin();
        var sources = registrations;
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            var values = new java.util.LinkedHashMap<DataBinding<?>, Object>();
            for (var source : sources) {
                if (!source.isActive()) continue;
                values.put(source, source.prepare(manager));
            }
            return new Prepared(ticket, java.util.Map.copyOf(values));
        }, executor);
    }

    boolean commit(long ticket, Runnable install) {
        return commit(new Prepared(ticket, java.util.Map.of()), install);
    }

    boolean commit(Prepared prepared, Runnable install) {
        host.assertOwner();
        long ticket = prepared.ticket();
        if (host.lifecycle().state() != EngineState.RUNNING || ticket != preparing.get()
                || ticket <= published.generation()) return false;
        if (applying) throw new IllegalStateException("Reentrant UI resource application");
        applying = true;
        try (var lifetime = host.rootScope().openChild(ScopeKind.RESOURCE_RELOAD, RELOAD_ID)) {
            var values = new java.util.LinkedHashMap<>(published.values());
            values.keySet().removeIf(source -> !source.isActive());
            prepared.values().forEach((source, value) -> {
                if (source.isActive()) values.put(source, new Value(ticket, value));
            });
            install.run();
            if (!lifetime.isOpen() || ticket != preparing.get()) return false;
            values.keySet().removeIf(source -> !source.isActive());
            caches.forEach(AlignmentAssets::invalidate);
            published = new Published(ticket, java.util.Map.copyOf(values));
            for (var watch : List.copyOf(watchers)) {
                if (!watch.lifetime.isActive()) continue;
                try {
                    watch.observer.accept(ticket);
                } catch (RuntimeException | LinkageError | AssertionError failure) {
                    host.report("UI resource observer", failure);
                    watch.lifetime.close();
                }
            }
            return true;
        } finally {
            applying = false;
        }
    }

    @Override
    public <T> Data<T> registerData(ModuleContext module, ScopeHandle scope, ResourceDataKey<T> key,
                                    DataLoader<T> loader, java.util.function.Predicate<? super T> validator) {
        var target = host.ownedScope(module, scope);
        host.moduleAccess(module).requireSchemaOwner(key.id());
        var address = new Address(target, key.id());
        if (datasets.containsKey(address)) throw new IllegalArgumentException("Duplicate resource data: " + key.id());
        if (datasets.size() >= 1024) throw new IllegalStateException("Too many managed UI resource datasets");
        var binding = new DataBinding<>(key, Objects.requireNonNull(loader), Objects.requireNonNull(validator));
        binding.lifetime = host.onScopeClose(target, () -> {
            datasets.remove(address, binding);
            registrations = List.copyOf(datasets.values());
            var values = new java.util.LinkedHashMap<>(published.values());
            values.remove(binding);
            published = new Published(published.generation(), java.util.Map.copyOf(values));
        });
        datasets.put(address, binding);
        registrations = List.copyOf(datasets.values());
        return binding;
    }

    private final class DataBinding<T> implements Data<T> {
        private final ResourceDataKey<T> key;
        private final DataLoader<T> loader;
        private final java.util.function.Predicate<? super T> validator;
        private volatile Subscription lifetime;

        private DataBinding(ResourceDataKey<T> key, DataLoader<T> loader,
                            java.util.function.Predicate<? super T> validator) {
            this.key = key;
            this.loader = loader;
            this.validator = validator;
        }

        Object prepare(ResourceManager manager) {
            try {
                T value = key.type().cast(Objects.requireNonNull(loader.prepare(manager), "Resource data"));
                if (!validator.test(value)) throw new IllegalArgumentException("Invalid resource data: " + key.id());
                return value;
            } catch (Exception failure) {
                throw new java.util.concurrent.CompletionException("Preparing UI resource data " + key.id(), failure);
            }
        }

        @Override public boolean isActive() { return lifetime != null && lifetime.isActive(); }
        @Override public void close() { lifetime.close(); }
        @Override public DataSnapshot<T> snapshot() {
            var current = published;
            var value = isActive() ? current.values().get(this) : null;
            return value == null ? new DataSnapshot<>(current.generation(), java.util.Optional.empty())
                    : new DataSnapshot<>(value.generation(), java.util.Optional.of(key.type().cast(value.data())));
        }
    }

    @Override
    public Subscription observe(ModuleContext module, ScopeHandle scope, LongConsumer observer) {
        var target = host.ownedScope(module, scope);
        var watch = new Watch(Objects.requireNonNull(observer));
        watch.lifetime = host.onScopeClose(target, () -> watchers.remove(watch));
        watchers.add(watch);
        return watch.lifetime;
    }

    @Override
    public AlignmentAssets alignmentAssets(ModuleContext module, ScopeHandle scope, ResourceManager manager) {
        var target = host.ownedScope(module, scope);
        var assets = new AlignmentAssets(manager, () -> host.metrics().cache(true), () -> host.metrics().cache(false));
        host.onScopeClose(target, () -> {
            caches.remove(assets);
            assets.invalidate();
        });
        caches.add(assets);
        return assets;
    }

    private static final class Watch {
        final LongConsumer observer;
        Subscription lifetime;

        Watch(LongConsumer observer) { this.observer = observer; }
    }
}
