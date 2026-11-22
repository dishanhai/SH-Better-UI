// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.RegistrationHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionPolicy;
import com.dishanhai.sh_better_ui.kernel.api.page.UiNode;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageValidator;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.internal.page.PageDataState;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Local declarative foreground pages for the 26.x native extraction pipeline. */
final class ClientPages implements PageService {
    private static final int MAX_REGISTRATIONS = 256;
    private static final Set<UiPageType> PAGE_TYPES = Set.of(UiPageType.SCREEN, UiPageType.MODAL);
    private final KernelHost host;
    private static final UiPageValidator VALIDATOR = new UiPageValidator();
    private final Map<UiId, Registered> definitions = new LinkedHashMap<>();
    private OpenPage active;

    ClientPages(KernelHost host) { this.host = host; }

    @Override public Map<UiId, UiVersion> components() {
        host.assertOwner();
        return NativePageScreen.COMPONENTS;
    }

    @Override public Set<UiPageType> pageTypes() {
        host.assertOwner();
        return PAGE_TYPES;
    }

    @Override public RegistrationHandle register(ModuleContext owner, UiPageDefinition page) {
        host.moduleAccess(owner);
        if (!page.id().namespace().equals(owner.descriptor().id().namespace())) {
            throw new SecurityException("Page namespace must belong to its registering scope");
        }
        resolve(page);
        if (definitions.size() >= MAX_REGISTRATIONS) throw new IllegalStateException("Page registry is full");
        var registration = new Registered(owner.scope(), page);
        if (definitions.putIfAbsent(page.id(), registration) != null) {
            throw new IllegalArgumentException("Page already registered: " + page.id());
        }
        registration.owner.track(registration);
        return registration;
    }

    @Override public Optional<UiPageDefinition> find(ModuleContext owner, UiId id) {
        host.moduleAccess(owner);
        var found = definitions.get(Objects.requireNonNull(id));
        return found != null && found.owner == owner.scope() ? Optional.of(found.page) : Optional.empty();
    }

    @Override public Instance open(ModuleContext owner, UiId id, Map<String, UiValue> data, Consumer<UiId> action) {
        var page = find(owner, id).orElseThrow(() -> new IllegalArgumentException("Page not owned: " + id));
        Objects.requireNonNull(action);
        var client = Minecraft.getInstance();
        if (active != null || NativePort.hasOverlay(client)) {
            throw new IllegalStateException("A native declarative foreground page cannot open now");
        }
        var instance = new OpenPage(owner.scope(), resolve(page), new PageDataState(page.dataSchema(), data), action);
        active = instance;
        instance.scope.track(instance::released);
        try {
            NativePort.show(client, instance.screen);
            if (NativePort.screen(client) != instance.screen) {
                throw new IllegalStateException("Native declarative Screen did not open");
            }
        } catch (RuntimeException failure) {
            instance.finish(State.FAILED, true);
            throw failure;
        }
        return instance;
    }

    @Override public boolean update(ModuleContext owner, UUID id, Map<String, UiValue> updates) {
        host.moduleAccess(owner);
        var instance = active;
        if (instance == null || instance.owner != owner.scope() || !instance.id.equals(id) || !instance.scope.isOpen()) {
            return false;
        }
        long revision = instance.data.revision();
        instance.data.apply(revision, Math.incrementExact(revision), updates);
        instance.publish();
        return true;
    }

    static UiPageDefinition resolve(UiPageDefinition page) {
        if (!PAGE_TYPES.contains(page.type())) throw new IllegalArgumentException("Unsupported page type: " + page.type());
        var resolved = VALIDATOR.resolve(page, false, NativePageScreen.COMPONENTS);
        var effective = resolved.page().orElseThrow(() -> new IllegalArgumentException(resolved.problems().get(0).toString()));
        validateActions(effective.root(), effective);
        return effective;
    }

    static void validateActions(UiNode node, UiPageDefinition page) {
        if (node.properties().containsKey("payloadField")) {
            throw new IllegalArgumentException("Native 26.x pages expose input through Instance.localValues(), not action payloads");
        }
        node.action().ifPresent(id -> {
            var action = page.actions().get(id);
            if (action.policy() != UiActionPolicy.LOCAL) throw new IllegalArgumentException("Unsupported action policy");
            action.payloadSchema().validate(Map.of(), true);
        });
        for (var child : node.children()) validateActions(child, page);
    }

    private final class Registered implements RegistrationHandle {
        private final ScopeHandle owner;
        private final UiPageDefinition page;

        private Registered(ScopeHandle owner, UiPageDefinition page) {
            this.owner = owner;
            this.page = page;
        }

        @Override public Status status() {
            host.assertOwner();
            return definitions.get(page.id()) == this ? Status.ACTIVE : Status.REVOKED;
        }

        @Override public void close() {
            host.assertOwner();
            if (definitions.remove(page.id(), this) && active != null
                    && active.owner == owner && active.pageId.equals(page.id())) active.close();
        }
    }

    private final class OpenPage implements Instance {
        private final UUID id = UUID.randomUUID();
        private final ScopeHandle owner;
        private final ScopeHandle scope;
        private final UiId pageId;
        private final PageDataState data;
        private final Consumer<UiId> action;
        private final NativePageScreen screen;
        private final CompletableFuture<Boolean> displayed = new CompletableFuture<>();
        private final CompletableFuture<Snapshot> closed = new CompletableFuture<>();
        private volatile Snapshot snapshot;
        private State state = State.OPENING;
        private boolean restoreParent = true;

        OpenPage(ScopeHandle owner, UiPageDefinition page, PageDataState data, Consumer<UiId> action) {
            this.owner = owner;
            this.pageId = page.id();
            this.data = data;
            this.action = action;
            screen = new NativePageScreen(page, this::snapshot, this::act, this::presented, this::close,
                    () -> finish(State.CLOSED, false));
            scope = owner.openChild(ScopeKind.SCREEN, new UiId(owner.owner().namespace(), "page/" + id));
            publish();
        }

        @Override public Snapshot snapshot() { return snapshot; }
        @Override public Map<String, UiValue> localValues() {
            host.assertOwner();
            return screen.localValues();
        }
        @Override public CompletionStage<Boolean> displayed() { return displayed.minimalCompletionStage(); }
        @Override public CompletionStage<Snapshot> closed() { return closed.minimalCompletionStage(); }
        @Override public void close() { finish(State.CLOSED, true); }

        private void publish() {
            snapshot = new Snapshot(id, pageId, state, data.revision(), data.data(), data.synchronizedData());
            screen.synchronizeInputs(snapshot);
        }

        private void presented() {
            if (!scope.isOpen() || state != State.OPENING) return;
            state = State.ACTIVE;
            publish();
            displayed.complete(true);
        }

        private void act(UiId id) {
            if (!scope.isOpen() || state != State.ACTIVE || NativePort.screen(Minecraft.getInstance()) != screen) return;
            try {
                action.accept(id);
            } catch (RuntimeException failure) {
                host.report("Declarative page action " + pageId, failure);
                screen.actionFailed();
            }
        }

        private void finish(State result, boolean restore) {
            host.assertOwner();
            if (!scope.isOpen()) return;
            restoreParent = restore;
            state = result;
            scope.close();
        }

        private void released() {
            active = null;
            if (state != State.FAILED) state = State.CLOSED;
            publish();
            try {
                if (restoreParent) screen.restoreParent();
            } finally {
                if (restoreParent) {
                    complete();
                } else {
                    // Screen.removed runs before Minecraft installs the replacement Screen.
                    Minecraft.getInstance().schedule(this::complete);
                }
            }
        }

        private void complete() {
            displayed.complete(false);
            closed.complete(snapshot);
        }
    }
}
