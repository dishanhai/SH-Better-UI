// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Coordinates startup and shutdown on the owner thread. Loader bridges supply the candidates. */
final class KernelHost implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(KernelHost.class.getName());
    private static final Consumer<UIPhase> NO_PHASE_WORK = phase -> {};

    private final Thread ownerThread = Thread.currentThread();
    private final ModuleDescriptor.Platform platform;
    private final EnginePublication publication;
    private final EngineLifecycle lifecycle = new EngineLifecycle();
    private final ServiceRegistry registry = new ServiceRegistry();
    private final RuntimeStatistics metrics = new RuntimeStatistics();

    RuntimeStatistics metrics() { return metrics; }
    private final ScopeLifecycle scopes;
    private final DirtyWork dirty;
    private final CentralScheduler scheduler;
    private final MutationCoordinator mutations;
    private final ConfigCenter configs;
    private final StateStore states;
    private final CommandBus commands;
    private final EventBus events;
    private final DynamicCapabilities capabilities;
    private final Map<UiId, MountedModule> mounted = new LinkedHashMap<>();
    private final Map<UiId, ModuleDiscovery.Module> running = new LinkedHashMap<>();
    private Map<UiId, Set<UiId>> required = Map.of();
    private volatile EngineAccess.Published published;
    private EnginePublication.Installation installation;
    private boolean releasing;

    KernelHost(ModuleDescriptor.Platform platform, EnginePublication publication) {
        this(platform, publication, System::nanoTime, null, SchedulerBudget.DEFAULT);
    }

    KernelHost(ModuleDescriptor.Platform platform, EnginePublication publication, LongSupplier clock,
               Executor executor, SchedulerBudget budget) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.publication = Objects.requireNonNull(publication, "publication");
        scopes = new ScopeLifecycle((scope, failure) -> report("Cleanup of " + scope.kind() + " " + scope.id(), failure),
                this::scopeInvalidated, clock);
        dirty = new DirtyWork(scopes, lifecycle::diagnostic);
        scheduler = new CentralScheduler(clock, executor, budget, this::report);
        mutations = new MutationCoordinator(scheduler, lifecycle::diagnostic);
        scopes.guardMutations(mutations::assertOutsideValidation);
        scopes.root().bindAccess(access(scopes.root(), null));
        configs = new ConfigCenter(scopes.root(), lifecycle, mutations, this::report);
        states = new StateStore(scopes.root(), mutations, this::report);
        commands = new CommandBus(scheduler, states, dirty, mutations);
        events = new EventBus(scopes, scheduler, mutations, this::report, metrics);
        capabilities = new DynamicCapabilities(scopes, () -> {
            var snapshot = published;
            return snapshot == null ? CapabilityIndex.fromModules(running.values()) : snapshot.capabilities();
        }, this::report);
    }

    void storage(java.nio.file.Path directory) {
        assertOwner();
        if (lifecycle.state() != EngineState.NEW) throw new IllegalStateException("Configure storage before startup");
        configs.storage(directory);
    }

    UILifecycle lifecycle() {
        return lifecycle;
    }

    ScopeHandle rootScope() {
        assertRunning();
        return scopes.root();
    }

    void runFrame() {
        runFrame(NO_PHASE_WORK);
    }

    void runFrame(Consumer<UIPhase> phaseWork) {
        assertRunning();
        scheduler.runFrame(this::prepareFrame, phaseWork);
    }

    void beginNativeFrame(Runnable begin, Consumer<UIPhase> phaseWork) {
        assertRunning();
        scheduler.beginNativeFrame(() -> {
            prepareFrame();
            begin.run();
        }, phaseWork);
    }

    boolean renderNativeFrame(ScopeHandle screen) {
        return renderNativeFrame(screen, () -> {});
    }

    boolean renderNativeFrame(ScopeHandle screen, Runnable drawBuiltin) {
        assertRunning();
        var surface = screen == null ? null : scopes.checkedScope(screen);
        if (surface != null && (surface.kind() != ScopeKind.SCREEN || !surface.isOpen())) return false;
        return scheduler.renderNativeFrame(scope -> scope.ancestor(ScopeKind.SCREEN) == surface, phase -> drawBuiltin.run());
    }

    void finishNativeFrame() {
        assertRunning();
        scheduler.finishNativeFrame(NO_PHASE_WORK);
    }

    private void prepareFrame() {
        dirty.beginFrame();
        scopes.ownership().sweep();
        if (!capabilities.commit()) return;
        var available = capabilities.excluding(Set.of());
        for (var module : List.copyOf(running.values())) {
            if (!running.containsKey(module.id())) continue;
            var descriptor = module.descriptor();
            boolean missing = descriptor.requiredCapabilities().entrySet().stream().anyMatch(entry ->
                    available.select(entry.getKey(), entry.getValue(), provider -> {
                        var candidate = running.get(provider);
                        return candidate != null && !provider.equals(module.id())
                                && candidate.descriptor().initializationPhase()
                                .compareTo(descriptor.initializationPhase()) <= 0;
                    }) == null);
            if (missing) {
                var failure = new IllegalStateException("Required UI capability withdrawn from " + module.id());
                failModule(module.id(), failure);
                if (lifecycle.state() != EngineState.RUNNING) throw failure;
            }
        }
    }

    long nowNanos() { return scheduler.nowNanos(); }

    boolean canBeginNativeFrame() { return scheduler.canBeginNativeFrame(); }

    boolean canRouteNativeInput() { return scheduler.canRouteNativeInput(); }

    <T> T nativeInput(Supplier<T> route) {
        assertRunning();
        return scheduler.nativeInput(route);
    }

    ModuleAccess moduleAccess(ModuleContext context) {
        assertOwner();
        if (releasing || !(context instanceof MountedModule module) || mounted.get(module.module.id()) != module
                || !module.scope.isOpen() || !running.containsKey(module.module.id())
                && lifecycle.module(module.module.id()) != ModuleLifecycle.STARTING) {
            throw new SecurityException("UI module context was not issued by this running engine");
        }
        return module.access;
    }

    OwnershipLease acquire(ModuleAccess access, ResourceRequest request, AutoCloseable release) {
        return scopes.ownership().view(access).acquire(request, release);
    }

    boolean pointerAvailable(ScopeHandle scope, ModuleDescriptor.ResourceClaim claim) {
        assertRunning();
        return scopes.ownership().pointerAvailable(scopes.checkedScope(scope), claim);
    }

    OwnershipLease acquireNativePointer(ModuleAccess access, ResourceRequest request, AutoCloseable release) {
        return scopes.ownership().acquireNativePointer(access, request, release);
    }

    ScopeLifecycle.Scope ownedNode(ModuleContext context, ScopeHandle node) {
        var target = ownedScope(context, node);
        if (target.kind() != ScopeKind.UI_TREE && target.kind() != ScopeKind.COMPONENT) {
            throw new IllegalArgumentException("This UI binding requires a tree or component node");
        }
        return target;
    }

    ScopeLifecycle.Scope ownedScope(ModuleContext context, ScopeHandle scope) {
        var target = moduleAccess(context).write(scope);
        if (!context.scope().owner().equals(target.owner())) {
            throw new SecurityException("This UI binding requires an owned scope");
        }
        return target;
    }

    Subscription onScopeClose(ScopeLifecycle.Scope scope, Runnable release) {
        return scopes.onClose(scope, release);
    }

    ScopeHandle mountTree(ModuleContext context, ScopeHandle screen, UiId id) {
        var module = moduleAccess(context).requireIssuer();
        return scopes.mountTree(scopes.checkedScope(screen), module, Objects.requireNonNull(id));
    }

    ScopeHandle mountSession(ModuleContext context, ScopeHandle session, UiId id) {
        var module = moduleAccess(context).requireIssuer();
        return scopes.mountSession(scopes.checkedScope(session), module, Objects.requireNonNull(id));
    }

    boolean ownsNativeType(ModuleContext context, Class<?> type) {
        moduleAccess(context);
        var owner = mounted.get(context.descriptor().id()).module.implementation().getClass().getModule();
        return owner.isNamed() && type.getModule() == owner;
    }

    <T> void notifyScreen(UIEventType<T> type, ScopeHandle screen, T payload) {
        assertRunning();
        events.notifyScreen(type, scopes.checkedScope(screen), payload);
    }

    boolean isOnUIThread() { return Thread.currentThread() == ownerThread; }

    DirtyWork dirty() {
        return dirty;
    }

    private void scopeInvalidated(List<ScopeLifecycle.Scope> closing) {
        closing.forEach(scheduler::cancelScope);
        closing.forEach(dirty::remove);
        configs.remove(closing);
        states.remove(closing);
        commands.remove(closing);
        events.remove(closing);
        capabilities.remove(closing);
    }

    private ModuleAccess access(ScopeLifecycle.Scope scope, ModuleDiscovery.Module module) {
        return new ModuleAccess(scopes, scope, module, lifecycle::diagnostic, mutations::assertOutsideValidation);
    }

    private UIEngine engineView(ModuleDiscovery.Module module, ModuleAccess access,
                                Supplier<EngineAccess.Published> source) {
        var facets = new EngineAccess.Facets(scheduler.view(access), dirty.view(access),
                configs.view(access), states.view(access), access, commands.view(access), events.view(access),
                scopes.ownership().view(access), capabilities.view(access), metrics.view(access));
        var view = new UILifecycle() {
            @Override
            public EngineState state() {
                return lifecycle.state();
            }

            @Override
            public LifecycleSnapshot snapshot() {
                var snapshot = lifecycle.snapshot();
                return access.canReadDiagnostics() ? snapshot
                        : new LifecycleSnapshot(snapshot.state(), snapshot.modules(), List.of());
            }
        };
        return new EngineAccess(view, module, source, facets);
    }

    void restrictPermission(UiId id, UIPermission permission, ScopeHandle boundary) {
        var access = runningAccess(id);
        access.restrict(permission, scopes.checkedScope(boundary));
        permissionsChanged(access);
    }

    void revokePermission(UiId id, UIPermission permission) {
        var access = runningAccess(id);
        access.revoke(permission);
        permissionsChanged(access);
    }

    private ModuleAccess runningAccess(UiId id) {
        assertRunning();
        mutations.assertOutsideValidation();
        if (!running.containsKey(id)) {
            throw new IllegalArgumentException("UI module is not running: " + id);
        }
        return mounted.get(id).access;
    }

    private void permissionsChanged(ModuleAccess access) {
        scheduler.permissionsChanged(access);
        configs.permissionsChanged(access);
        states.permissionsChanged(access);
        events.permissionsChanged(access);
        scopes.ownership().permissionsChanged(access);
    }

    <T> EventResult routeInput(UIEventType<T> type, ScopeHandle activeScreen, ScopeHandle target, T payload) {
        assertRunning();
        return events.routeInput(type, activeScreen, target, payload);
    }

    void start(Collection<ModuleDiscovery.Candidate> candidates) {
        start(candidates, false);
    }

    void startNative(Collection<ModuleDiscovery.Candidate> candidates) {
        start(candidates, true);
    }

    private void start(Collection<ModuleDiscovery.Candidate> candidates, boolean isolateExtensions) {
        assertOwner();
        lifecycle.transition(EngineState.DISCOVERING);
        try {
            var discovered = ModuleDiscovery.discover(platform, candidates, isolateExtensions ? this::report : null);
            discovered.diagnostics().forEach(lifecycle::diagnostic);
            discovered.modules().forEach(module -> lifecycle.discover(module.id()));
            var plan = isolateExtensions ? resolveNative(discovered.modules()) : DependencyGraph.resolve(discovered.modules());
            plan.diagnostics().forEach(lifecycle::diagnostic);
            required = plan.requiredDependencies();

            lifecycle.transition(EngineState.REGISTERING);
            register(plan);
            lifecycle.transition(EngineState.RESOLVING);
            registry.freeze();
            lifecycle.transition(EngineState.INITIALIZING);
            initialize();

            published = capture(running.keySet());
            mounted.values().forEach(module -> module.startupView = null);
            lifecycle.transition(EngineState.READY);
            installation = publication.install(engineView(null, access(null, null), () -> published));
            lifecycle.transition(EngineState.RUNNING);
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            report("UI engine startup", failure);
            withdraw();
            lifecycle.transition(EngineState.FAILED);
            tearDown(null);
            throw failure;
        }
    }

    private DependencyGraph.Plan resolveNative(List<ModuleDiscovery.Module> discovered) {
        var modules = new ArrayList<>(discovered);
        while (true) {
            try {
                return DependencyGraph.resolve(modules);
            } catch (DependencyGraph.Rejected failure) {
                boolean removed = modules.removeIf(module -> {
                    if (module.trusted() || !failure.affected().contains(module.id())) return false;
                    lifecycle.transition(module.id(), ModuleLifecycle.BLOCKED);
                    return true;
                });
                if (!removed) throw failure;
                report("Rejected client UI extension dependency graph", failure);
            }
        }
    }

    void stopModule(UiId id) {
        assertRunning();
        var target = running.get(Objects.requireNonNull(id, "id"));
        if (target == null) {
            return;
        }
        if (target.core()) {
            throw new IllegalStateException("Stopping a core module requires stopping the engine: " + id);
        }
        stopAffected(id, false);
    }

    void failModule(UiId id, Throwable failure) {
        assertRunning();
        Objects.requireNonNull(failure, "failure");
        var target = running.get(Objects.requireNonNull(id, "id"));
        if (target == null) {
            return;
        }
        report("Runtime failure of " + id, failure);
        if (target.core()) {
            withdraw();
            lifecycle.transition(EngineState.FAILED);
            tearDown(id);
        } else {
            stopAffected(id, true);
        }
    }

    private void stopAffected(UiId id, boolean failed) {
        releasing = true;
        try {
            var removed = new LinkedHashSet<UiId>();
            removed.add(id);
            boolean changed;
            do {
                changed = false;
                var remaining = capabilities.excluding(removed);
                for (var module : running.values()) {
                    if (removed.contains(module.id())) continue;
                    var descriptor = module.descriptor();
                    boolean missingModule = descriptor.dependencies().entrySet().stream().anyMatch(entry ->
                            entry.getValue().required() && removed.contains(entry.getKey()));
                    boolean missingCapability = descriptor.requiredCapabilities().entrySet().stream().anyMatch(entry ->
                            remaining.select(entry.getKey(), entry.getValue(), provider -> {
                                var candidate = running.get(provider);
                                return candidate != null && !provider.equals(module.id())
                                        && candidate.descriptor().initializationPhase()
                                        .compareTo(descriptor.initializationPhase()) <= 0;
                            }) == null);
                    if (missingModule || missingCapability) {
                        removed.add(module.id());
                        changed = true;
                        lifecycle.diagnostic(module.id() + " stopped after losing required dependencies of " + id);
                    }
                }
            } while (changed);
            for (UiId module : removed) {
                scopes.beginClose(mounted.get(module).scope);
                running.remove(module);
                registry.remove(module);
            }
            published = capture(running.keySet());
            var reverse = new ArrayList<>(removed);
            for (int i = reverse.size() - 1; i >= 0; i--) {
                UiId module = reverse.get(i);
                stop(mounted.remove(module), failed && module.equals(id));
            }
        } finally {
            releasing = false;
        }
    }

    @Override
    public void close() {
        assertOwner();
        EngineState state = lifecycle.state();
        if (state == EngineState.STOPPING || state == EngineState.TERMINATED) {
            return;
        }
        if (releasing || (state != EngineState.NEW && state != EngineState.RUNNING && state != EngineState.FAILED)) {
            throw new IllegalStateException("Cannot close the UI engine during a lifecycle callback");
        }
        withdraw();
        lifecycle.transition(EngineState.STOPPING);
        tearDown(null);
        lifecycle.transition(EngineState.TERMINATED);
    }

    private void register(DependencyGraph.Plan plan) {
        var registered = new LinkedHashSet<UiId>();
        for (var module : plan.order()) {
            if (blockIfUnavailable(module, registered)) {
                continue;
            }
            var context = new MountedModule(module);
            mounted.put(module.id(), context);
            lifecycle.transition(module.id(), ModuleLifecycle.REGISTERING);
            try {
                registry.register(module, permission -> context.access.require(permission, context.scope), registrar -> {
                    context.registrar = registrar;
                    try {
                        module.implementation().register(context);
                    } finally {
                        context.registrar = null;
                    }
                });
                lifecycle.transition(module.id(), ModuleLifecycle.REGISTERED);
                registered.add(module.id());
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                lifecycle.transition(module.id(), ModuleLifecycle.FAILED);
                report("Registration of " + module.id(), failure);
                scopes.close(context.scope);
                if (module.core()) {
                    throw failure;
                }
            }
        }
    }

    private void initialize() {
        for (var context : mounted.values()) {
            var module = context.module;
            UiId id = module.id();
            if (lifecycle.module(id) != ModuleLifecycle.REGISTERED) {
                continue;
            }
            if (blockIfUnavailable(module, running.keySet())) {
                registry.remove(id);
                scopes.close(context.scope);
                continue;
            }
            var visible = new LinkedHashSet<>(running.keySet());
            visible.add(id);
            context.startupView = capture(visible);
            lifecycle.transition(id, ModuleLifecycle.STARTING);
            try {
                module.implementation().start(context);
                lifecycle.transition(id, ModuleLifecycle.RUNNING);
                running.put(id, module);
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                report("Initialization of " + id, failure);
                registry.remove(id);
                stop(context, true);
                if (module.core()) {
                    throw failure;
                }
            }
        }
        mounted.values().removeIf(context -> !running.containsKey(context.module.id()));
    }

    private boolean blockIfUnavailable(ModuleDiscovery.Module module, Set<UiId> available) {
        var missing = required.get(module.id()).stream().filter(id -> !available.contains(id)).sorted().findFirst();
        if (missing.isEmpty()) {
            return false;
        }
        if (module.core()) {
            throw new IllegalStateException("Core module " + module.id() + " lost required module " + missing.get());
        }
        lifecycle.transition(module.id(), ModuleLifecycle.BLOCKED);
        lifecycle.diagnostic(module.id() + " blocked by unavailable required module " + missing.get());
        return true;
    }

    private EngineAccess.Published capture(Set<UiId> visible) {
        return new EngineAccess.Published(registry.capture(visible), CapabilityIndex.fromModules(running.values()));
    }

    private void stop(MountedModule context, boolean failed) {
        UiId id = context.module.id();
        scopes.beginClose(context.scope);
        context.startupView = null;
        lifecycle.transition(id, ModuleLifecycle.STOPPING);
        try {
            context.module.implementation().stop(context);
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            report("Shutdown of " + id, failure);
            failed = true;
        } finally {
            scopes.close(context.scope);
        }
        lifecycle.transition(id, failed ? ModuleLifecycle.FAILED : ModuleLifecycle.TERMINATED);
    }

    private void withdraw() {
        if (installation != null) {
            installation.close();
            installation = null;
        }
        published = null;
    }

    private void tearDown(UiId failedModule) {
        releasing = true;
        try {
            scopes.beginClose(scopes.root());
            var reverse = new ArrayList<>(mounted.values());
            for (int i = reverse.size() - 1; i >= 0; i--) {
                var context = reverse.get(i);
                UiId id = context.module.id();
                registry.remove(id);
                switch (lifecycle.module(id)) {
                    case STARTING, RUNNING -> stop(context, id.equals(failedModule));
                    case REGISTERED -> {
                        scopes.close(context.scope);
                        lifecycle.transition(id, ModuleLifecycle.TERMINATED);
                    }
                    default -> { /* Already failed, blocked or closed. */ }
                }
            }
            scopes.close(scopes.root());
            configs.closeStorage();
            scheduler.close();
            mounted.clear();
            running.clear();
            required = Map.of();
        } finally {
            releasing = false;
        }
    }

    void report(String action, Throwable failure) {
        lifecycle.diagnostic(action + ": " + failure);
        LOGGER.log(System.Logger.Level.ERROR, action, failure);
    }

    void assertOwner() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("UI engine lifecycle requires the owner thread");
        }
    }

    private void assertRunning() {
        assertOwner();
        if (lifecycle.state() != EngineState.RUNNING || releasing) {
            throw new IllegalStateException("UI engine is not available for lifecycle changes");
        }
    }

    private final class MountedModule implements ModuleContext {
        private final ModuleDiscovery.Module module;
        private final ScopeLifecycle.Scope scope;
        private final ModuleAccess access;
        private final UIEngine engine;
        private volatile EngineAccess.Published startupView;
        private ServiceRegistrar registrar;

        private MountedModule(ModuleDiscovery.Module module) {
            this.module = module;
            scope = scopes.openModule(module.descriptor());
            access = access(scope, module);
            scope.bindAccess(access);
            engine = engineView(module, access, this::current);
        }

        @Override
        public ModuleDescriptor descriptor() {
            return module.descriptor();
        }

        @Override
        public ServiceRegistrar services() {
            assertOwner();
            if (registrar == null || !scope.isOpen()) {
                throw new IllegalStateException("UI module registration window is closed: " + module.id());
            }
            return registrar;
        }

        @Override
        public UIEngine engine() {
            if (current() == null) {
                throw new IllegalStateException("UI module has no active engine view: " + module.id());
            }
            return engine;
        }

        @Override
        public ScopeHandle scope() {
            return scope;
        }

        private EngineAccess.Published current() {
            var active = published;
            var view = active == null ? startupView : active;
            return scope.isOpen() ? view : null;
        }
    }
}
