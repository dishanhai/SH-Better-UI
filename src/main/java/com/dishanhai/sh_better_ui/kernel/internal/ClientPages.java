// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiScreenBuilder;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityKey;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.RegistrationHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.ServiceDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionPolicy;
import com.dishanhai.sh_better_ui.kernel.api.page.UiBuiltinComponents;
import com.dishanhai.sh_better_ui.kernel.api.page.UiComponentDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.page.UiComponentFactory;
import com.dishanhai.sh_better_ui.kernel.api.page.UiComponentRegistry;
import com.dishanhai.sh_better_ui.kernel.api.page.UiNode;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.page.UiResolvedPage;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiActionState;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiActionPrompt;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiErrorCode;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolCapabilities;
import com.dishanhai.sh_better_ui.kernel.internal.page.PageDataState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/** Connects validated page definitions to the existing Screen service. */
final class ClientPages implements PageService, UIModule {
    private static final UiVersion VERSION = new UiVersion(1, 0, 0);
    static final Set<UiId> COMPONENTS = Stream.concat(NativePageView.supportedComponents().stream(),
                    Stream.of(UiBuiltinComponents.id("hud_panel"), UiBuiltinComponents.id("notification")))
            .collect(Collectors.toUnmodifiableSet());
    static final Set<UiPageType> PAGE_TYPES = Set.of(UiPageType.SCREEN, UiPageType.MODAL, UiPageType.HUD, UiPageType.NOTIFICATION);
    static final Map<UiId, UiVersion> RENDERERS = COMPONENTS.stream()
            .collect(Collectors.toUnmodifiableMap(id -> id, id -> UiBuiltinComponents.VERSION));
    private static final int MAX_REGISTRATIONS = 256;

    private final KernelHost host;
    private final ClientScreens screens;
    private final LiveSurfaceRuntime live;
    private final UiComponentRegistry componentRegistry = new UiComponentRegistry();
    private final Map<UiId, Registered> definitions = new LinkedHashMap<>();
    private final Map<UUID, OpenPage> instances = new LinkedHashMap<>();
    private final Set<ScopeHandle> scripts = Collections.newSetFromMap(new IdentityHashMap<>());
    private ModuleContext context;
    private OpenPage active;
    private PageStudioReport.Page recentStudioPage;

    ClientPages(KernelHost host, ClientScreens screens, LiveSurfaceRuntime live) {
        this.host = host;
        this.screens = screens;
        this.live = live;
    }

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id(KEY.id()).version(VERSION)
                .initializationPhase(ModuleDescriptor.InitializationPhase.SERVICE)
                .providedCapability(new CapabilityKey(KEY.id()), VERSION).build();
    }

    @Override
    public void register(ModuleContext owner) {
        context = owner;
        owner.services().register(new ServiceDescriptor<>(KEY, VERSION, ServiceDescriptor.Visibility.PUBLIC,
                0, false), this);
    }

    @Override public void start(ModuleContext owner) {}
    @Override public void stop(ModuleContext owner) {}
    @Override public Map<UiId, UiVersion> components() {
        host.assertOwner();
        return componentRegistry.renderers(RENDERERS, false);
    }

    Map<UiId, UiVersion> remoteComponents() {
        host.assertOwner();
        return componentRegistry.renderers(RENDERERS, true);
    }

    @Override
    public RegistrationHandle registerComponent(ModuleContext owner, UiComponentDescriptor descriptor,
                                                UiComponentFactory factory) {
        host.moduleAccess(owner);
        if (!descriptor.typeId().namespace().equals(owner.descriptor().id().namespace())) {
            throw new SecurityException("Component namespace must belong to its registering module");
        }
        if (componentRegistry.snapshot().size() >= MAX_REGISTRATIONS
                || descriptor.remoteAllowed() && remoteComponents().size()
                + ClientPageNetwork.PROTOCOL_CAPABILITIES.size() >= UiProtocolCapabilities.MAX_CAPABILITIES) {
            throw new IllegalStateException("Page component registry is full");
        }
        return componentRegistry.register(owner.scope(), descriptor, factory);
    }
    @Override public Set<UiPageType> pageTypes() {
        host.assertOwner();
        return PAGE_TYPES.stream().filter(this::available).collect(Collectors.toUnmodifiableSet());
    }

    boolean available(UiPageType type) {
        var policy = live.policy();
        return switch (type) {
            case SCREEN, MODAL -> true;
            case HUD -> policy.enabled();
            case NOTIFICATION -> policy.enabled() && policy.notifications();
            case CONTAINER -> false;
        };
    }

    private void requireAvailable(UiPageType type) {
        if (!available(type)) {
            throw new Rejected(type == UiPageType.CONTAINER ? UiErrorCode.CONTAINER_UNAVAILABLE
                    : UiErrorCode.CAPABILITY_MISSING, "Page surface is unavailable: " + type);
        }
    }

    @Override
    public RegistrationHandle register(ModuleContext owner, UiPageDefinition page) {
        host.moduleAccess(owner);
        return registerOwned(owner.scope(), owner.descriptor().id().namespace(), page,
                NativeTextReadability.PageOptions.DEFAULT);
    }

    private RegistrationHandle registerOwned(ScopeHandle owner, String namespace, UiPageDefinition page,
                                             NativeTextReadability.PageOptions text) {
        host.assertOwner();
        Objects.requireNonNull(page);
        if (!owner.isOpen() || !page.id().namespace().equals(namespace)) {
            throw new SecurityException("Page namespace must belong to its registering scope");
        }
        validate(page, false);
        if (definitions.size() >= MAX_REGISTRATIONS) throw new IllegalStateException("Page registry is full");
        var registration = new Registered(owner, page, text);
        if (definitions.putIfAbsent(page.id(), registration) != null) {
            throw new IllegalArgumentException("Page already registered: " + page.id());
        }
        owner.track(registration);
        return registration;
    }

    @Override
    public Optional<UiPageDefinition> find(ModuleContext owner, UiId id) {
        host.moduleAccess(owner);
        return findOwned(owner.scope(), id);
    }

    private Optional<UiPageDefinition> findOwned(ScopeHandle owner, UiId id) {
        var found = definitions.get(Objects.requireNonNull(id));
        return found == null || found.owner != owner ? Optional.empty() : Optional.of(found.page);
    }

    @Override
    public Instance open(ModuleContext owner, UiId id, Map<String, UiValue> initialData, Consumer<UiId> action) {
        host.moduleAccess(owner);
        return openOwned(owner.scope(), id, initialData, action);
    }

    private Instance openOwned(ScopeHandle owner, UiId id, Map<String, UiValue> data, Consumer<UiId> action) {
        var page = findOwned(owner, id).orElseThrow(() -> new IllegalArgumentException("Page not owned: " + id));
        return show(owner, UUID.randomUUID(), resolve(page, false), data, false, (idValue, payload) -> action.accept(idValue),
                () -> {}, definitions.get(id).text);
    }

    ScopeHandle openScriptScope() {
        host.assertOwner();
        var scope = context.scope().openChild(ScopeKind.CLIENT_SESSION,
                new UiId("sh_better_ui", "page_scripts/" + UUID.randomUUID()));
        scripts.add(scope);
        scope.track(() -> scripts.remove(scope));
        return scope;
    }

    RegistrationHandle registerScript(ScopeHandle scope, UiPageDefinition page, NativeTextReadability.PageOptions text) {
        requireScript(scope);
        return registerOwned(scope, "kubejs", page, text);
    }

    NativeTextReadability.Mount mountText(UiScreen screen) {
        return new NativeTextReadability.Mount(context, screens.mount(context, screen,
                new UiId("sh_better_ui", "page_text/" + UUID.randomUUID())));
    }

    Instance openScript(ScopeHandle scope, UiId id, Map<String, UiValue> data, Consumer<UiId> action) {
        requireScript(scope);
        return openOwned(scope, id, data, action);
    }

    private void requireScript(ScopeHandle scope) {
        host.assertOwner();
        if (!scripts.contains(scope) || !scope.isOpen()) throw new SecurityException("Inactive script page scope");
    }

    @Override
    public boolean update(ModuleContext owner, UUID instanceId, Map<String, UiValue> updates) {
        host.moduleAccess(owner);
        return updateOwned(owner.scope(), instanceId, updates);
    }

    boolean updateScript(ScopeHandle owner, UUID instanceId, Map<String, UiValue> updates) {
        requireScript(owner);
        return updateOwned(owner, instanceId, updates);
    }

    private boolean updateOwned(ScopeHandle owner, UUID instanceId, Map<String, UiValue> updates) {
        var page = instances.get(instanceId);
        if (page == null || page.owner != owner || page.remote) return false;
        long revision = page.data.revision();
        return page.patch(revision, Math.incrementExact(revision), updates) == PageDataState.Update.APPLIED;
    }

    OpenPage openRemote(UUID instanceId, UiPageDefinition page, Map<String, UiValue> initialData,
                        OpenPage suspended, BiConsumer<UiId, Map<String, UiValue>> action, Runnable closed) {
        requireAvailable(page.type());
        var resolved = resolve(page, true);
        if (suspended == null ? !canOpenRemote(page.type(), false)
                : page.type() != UiPageType.MODAL || active != null || suspended.state != State.SUSPENDED
                        || Minecraft.getInstance().screen != suspended.screen) {
            throw new IllegalStateException("Client surface is unavailable");
        }
        var scope = screens.session(context, new UiId(KEY.id().namespace(), "page_session/" + instanceId));
        try {
            return show(scope, instanceId, resolved, initialData, true, action, closed, NativeTextReadability.PageOptions.DEFAULT);
        } catch (RuntimeException failure) {
            scope.close();
            throw failure;
        }
    }

    void validateRemote(UiPageDefinition page, Map<String, UiValue> data) {
        requireAvailable(page.type());
        resolve(page, true);
        page.dataSchema().validate(data, true);
    }

    boolean canOpenRemote(UiPageType type, boolean highPriority) {
        var minecraft = Minecraft.getInstance();
        if (!available(type) || !screens.hasClientSession()) return false;
        if (!isForeground(type)) return true;
        if (minecraft.getOverlay() != null) return false;
        if (active == null) {
            return minecraft.screen == null && instances.values().stream().noneMatch(page -> page.state == State.SUSPENDED);
        }
        return highPriority && type == UiPageType.MODAL && active.canSuspend();
    }

    OpenPage suspendForModal() {
        var current = active;
        if (current == null || !current.canSuspend()) return null;
        current.saveFocus();
        current.state = State.SUSPENDED;
        current.publish();
        active = null;
        return current;
    }

    private static boolean isForeground(UiPageType type) {
        return type == UiPageType.SCREEN || type == UiPageType.MODAL;
    }

    void tickPresentation() {
        for (var page : java.util.List.copyOf(instances.values())) {
            if (page.live == null) continue;
            if (!available(page.definition.type())) {
                page.finish(page.state == State.OPENING ? State.FAILED : State.CLOSED, true);
            } else if (page.state == State.OPENING && page.live.painted().getAsBoolean()) {
                page.presented();
            }
        }
    }

    private OpenPage show(ScopeHandle owner, UUID instanceId, UiResolvedPage resolution,
                          Map<String, UiValue> data, boolean remote,
                          BiConsumer<UiId, Map<String, UiValue>> action, Runnable closed,
                          NativeTextReadability.PageOptions text) {
        host.assertOwner();
        Objects.requireNonNull(action);
        var definition = resolution.page().orElseThrow();
        requireAvailable(definition.type());
        boolean screenPage = isForeground(definition.type());
        if (screenPage && active != null || instances.containsKey(instanceId) || instances.size() >= 8) {
            throw new IllegalStateException("Declarative page capacity reached");
        }
        var model = new PageDataState(definition.dataSchema(), data);
        var scope = owner.openChild(ScopeKind.SCREEN, new UiId(owner.owner().namespace(), "page/" + instanceId));
        var page = new OpenPage(owner, scope, instanceId, definition, model, remote, action, closed,
                resolution.degradations());
        if (screenPage) active = page;
        instances.put(instanceId, page);
        scope.track(page::released);
        try {
            if (!screenPage) {
                var surface = remote ? scope : screens.session(context, new UiId(KEY.id().namespace(), "live_page/" + instanceId));
                if (!remote) {
                    scope.track(surface);
                    surface.track(page);
                }
                page.live = LivePageView.mount(live, context, surface, page);
                return page;
            }
            var view = new NativePageView(page, context.engine().require(
                    com.dishanhai.sh_better_ui.kernel.api.UIApis.LAYOUT).layout(), text, () -> mountText(page.screen));
            page.view = view;
            var title = remote ? Component.translatable("sh_better_ui.pages.remote_title", definition.options().title())
                    : Component.literal(definition.options().title());
            var builder = new UiScreenBuilder(title)
                    .dynamicTheme()
                    .onInit(nativeScreen -> page.runView(() -> {
                        view.init(nativeScreen);
                        if (page.savedFocus != null) {
                            view.restoreFocus(nativeScreen, page.savedFocus);
                            page.savedFocus = null;
                        }
                    }))
                    .onRender((nativeScreen, frame) -> page.renderView(() -> view.render(nativeScreen, frame)))
                    .onForeground((nativeScreen, frame) -> page.runView(() -> view.presented(nativeScreen, frame)))
                    .onRemoved(ignored -> page.nativeRemoved())
                    .onError((ignored, failure) -> {
                        host.report("Declarative page " + definition.id(), failure);
                        page.finish(State.FAILED, false);
                    });
            if (definition.type() == UiPageType.SCREEN) builder.themeBackground();
            if (remote && definition.type() == UiPageType.MODAL) builder.parent(null);
            page.screen = screens.create(context, builder);
            page.screen.open();
            return page;
        } catch (RuntimeException failure) {
            page.finish(State.FAILED, true);
            throw failure;
        }
    }

    private void validate(UiPageDefinition page, boolean remote) {
        resolve(page, remote);
    }

    private UiResolvedPage resolve(UiPageDefinition page, boolean remote) {
        if (!PAGE_TYPES.contains(page.type())) {
            throw new Rejected(page.type() == UiPageType.CONTAINER
                    ? UiErrorCode.CONTAINER_UNAVAILABLE : UiErrorCode.CAPABILITY_MISSING, "Unsupported page surface");
        }
        var resolution = componentRegistry.resolve(page, remote, RENDERERS);
        var resolved = resolution.page().orElseThrow(() -> {
            var problem = resolution.problems().get(0);
            var error = switch (problem.code()) {
                case COMPONENT_MISSING -> UiErrorCode.COMPONENT_MISSING;
                case CAPABILITY_MISSING -> UiErrorCode.CAPABILITY_MISSING;
                case RESOURCE_LIMIT -> UiErrorCode.RESOURCE_LIMIT_EXCEEDED;
                case REMOTE_DENIED -> UiErrorCode.PERMISSION_DENIED;
                default -> UiErrorCode.SCHEMA_INVALID;
            };
            return new Rejected(error, problem.toString());
        });
        if (page.type() == UiPageType.SCREEN || page.type() == UiPageType.MODAL) {
            validateRendered(resolved.root(), resolved, remote);
        } else LivePageView.validate(resolved);
        return resolution;
    }

    java.util.List<PageStudioReport.Page> studioPages() {
        host.assertOwner();
        var mounted = instances.values().stream().map(OpenPage::studioPage).toList();
        if (!mounted.isEmpty()) return mounted;
        if (recentStudioPage != null) return java.util.List.of(recentStudioPage);
        return definitions.values().stream().map(entry -> PageStudioReport.Page.registered(entry.page)).toList();
    }

    void clearStudioPage() { recentStudioPage = null; }

    static final class Rejected extends IllegalArgumentException {
        final UiErrorCode code;

        Rejected(UiErrorCode code, String detail) {
            super(detail);
            this.code = code;
        }
    }

    private void validateRendered(UiNode node, UiPageDefinition page, boolean remote) {
        if (!COMPONENTS.contains(node.typeId())) throw new IllegalArgumentException("Unsupported component: " + node.typeId());
        if (node.typeId().path().equals("hud_panel") || node.typeId().path().equals("notification")) {
            throw new IllegalArgumentException("A live surface must be the root of its own page");
        }
        node.action().ifPresent(id -> {
            var action = page.actions().get(id);
            boolean supported = remote ? action.policy() == UiActionPolicy.SERVER || action.policy() == UiActionPolicy.CONFIRMED
                    : action.policy() == UiActionPolicy.LOCAL;
            if (!supported) throw new IllegalArgumentException("Unsupported action policy");
            if (node.typeId().equals(UiBuiltinComponents.id("button"))) {
                action.payloadSchema().validate(Map.of(), true);
            }
        });
        for (var child : node.children()) validateRendered(child, page, remote);
    }

    private final class Registered implements RegistrationHandle {
        private final ScopeHandle owner;
        private final UiPageDefinition page;
        private final NativeTextReadability.PageOptions text;

        Registered(ScopeHandle owner, UiPageDefinition page, NativeTextReadability.PageOptions text) {
            this.owner = owner;
            this.page = page;
            this.text = Objects.requireNonNull(text);
        }

        @Override public Status status() {
            host.assertOwner();
            return definitions.get(page.id()) == this ? Status.ACTIVE : Status.REVOKED;
        }

        @Override
        public void close() {
            host.assertOwner();
            if (!definitions.remove(page.id(), this)) return;
            for (var instance : java.util.List.copyOf(instances.values())) {
                if (instance.owner == owner && instance.definition.id().equals(page.id())) instance.close();
            }
        }
    }

    final class OpenPage implements Instance {
        private final ScopeHandle owner;
        private final ScopeHandle scope;
        final UUID id;
        final UiPageDefinition definition;
        final PageDataState data;
        private final boolean remote;
        private final BiConsumer<UiId, Map<String, UiValue>> action;
        private final Runnable closed;
        private final CompletableFuture<Boolean> displayed = new CompletableFuture<>();
        private final CompletableFuture<Snapshot> terminal = new CompletableFuture<>();
        private volatile Snapshot snapshot;
        private State state = State.OPENING;
        private UiScreen screen;
        private NativePageView view;
        private final java.util.List<String> degradations;
        private long renderSamples;
        private long maxRenderNanos;
        private LivePageView.Mount live;
        private boolean nativeRemoval;
        private String savedFocus;
        private ConfirmScreen confirmation;
        private UUID request;
        private UiId pendingAction;
        private boolean waitingForAuthority;
        private long deadline;
        private UiActionState actionState = UiActionState.IDLE;

        OpenPage(ScopeHandle owner, ScopeHandle scope, UUID id, UiPageDefinition definition,
                 PageDataState data, boolean remote, BiConsumer<UiId, Map<String, UiValue>> action, Runnable closed,
                 java.util.List<String> degradations) {
            this.owner = owner;
            this.scope = scope;
            this.id = id;
            this.definition = definition;
            this.data = data;
            this.remote = remote;
            this.action = action;
            this.closed = closed;
            this.degradations = degradations;
            publish();
        }

        private void publish() {
            snapshot = new Snapshot(id, definition.id(), state, data.revision(), data.data(),
                    data.synchronizedData() && !waitingForAuthority);
        }

        @Override public Snapshot snapshot() { return snapshot; }

        @Override public Map<String, UiValue> localValues() {
            host.assertOwner();
            return view == null ? Map.of() : view.localValues();
        }

        private PageStudioReport.Page studioPage() {
            var viewport = screen == null ? null : screen.viewport();
            return PageStudioReport.Page.mounted(definition, state, data.revision(), snapshot.synchronizedData(),
                    remote, view == null ? null : view.inspect(), actionState, degradations, viewport,
                    renderSamples, maxRenderNanos);
        }

        @Override public CompletionStage<Boolean> displayed() { return displayed.minimalCompletionStage(); }

        @Override public CompletionStage<Snapshot> closed() { return terminal.minimalCompletionStage(); }

        void presented() {
            if (!scope.isOpen() || state != State.OPENING) return;
            state = State.ACTIVE;
            publish();
            displayed.complete(true);
        }

        PageDataState.Update patch(long base, long revision, Map<String, UiValue> updates) {
            var result = data.apply(base, revision, updates);
            publish();
            if (result == PageDataState.Update.APPLIED && live != null) live.update().accept(data.data());
            return result;
        }

        boolean restore(long revision, Map<String, UiValue> fields) {
            if (!data.restore(revision, fields) && !(waitingForAuthority && data.synchronizedData()
                    && revision == data.revision() && fields.equals(data.data()))) return false;
            waitingForAuthority = false;
            publish();
            if (live != null) live.update().accept(data.data());
            return true;
        }

        void requireSynchronization() {
            waitingForAuthority = true;
            publish();
        }

        void act(UiNode node) {
            act(node, Map.of());
        }

        void act(UiNode node, Map<String, UiValue> payload) {
            if (state == State.ACTIVE && snapshot.synchronizedData() && actionAvailable() && scope.isOpen()) {
                try {
                    node.action().ifPresent(id -> {
                        var values = Map.copyOf(payload);
                        definition.actions().get(id).payloadSchema().validate(values, true);
                        action.accept(id, values);
                    });
                } catch (RuntimeException failure) {
                    host.report("Declarative page action " + definition.id(), failure);
                    actionState = UiActionState.FAILED;
                    publish();
                }
            }
        }

        void runView(Runnable render) {
            try {
                render.run();
            } catch (RuntimeException failure) {
                host.report("Declarative page " + definition.id(), failure);
                finish(State.FAILED, true);
            }
        }

        void renderView(Runnable render) {
            if (!PageStudioReport.CAPTURE) {
                runView(render);
                return;
            }
            long start = System.nanoTime();
            runView(render);
            renderSamples++;
            maxRenderNanos = Math.max(maxRenderNanos, System.nanoTime() - start);
        }

        boolean actionAvailable() { return actionState != UiActionState.PENDING; }

        UiActionState actionState() { return actionState; }

        boolean hasRequest(UUID requestId) { return requestId.equals(request); }

        void request(UUID requestId, UiId actionId, long expiry) {
            request = requestId;
            pendingAction = actionId;
            deadline = expiry;
            actionState = UiActionState.PENDING;
            publish();
        }

        void result(UUID requestId, UiActionState result) {
            if (request == null || !request.equals(requestId) || result == UiActionState.PENDING
                    || result == UiActionState.IDLE) return;
            request = null;
            pendingAction = null;
            actionState = result;
            dismissConfirmation(true);
            publish();
        }

        void tick(long now) {
            if (confirmation != null && Minecraft.getInstance().screen != confirmation) {
                close();
                return;
            }
            if (request != null && now - deadline >= 0) result(request, UiActionState.TIMED_OUT);
        }

        private boolean canSuspend() {
            var minecraft = Minecraft.getInstance();
            return remote && definition.type() == UiPageType.SCREEN && scope.isOpen() && state == State.ACTIVE
                    && screen != null && minecraft.screen == screen && minecraft.getOverlay() == null
                    && !screen.isDragging() && actionAvailable() && snapshot.synchronizedData()
                    && UiLocalModal.current(screen) == null && (view == null || !view.hasUnsavedInput());
        }

        private void saveFocus() {
            if (view != null && screen != null) savedFocus = view.focusedNodeId(screen);
        }

        boolean prepareResume(long revision, Map<String, UiValue> fields) {
            if (state != State.SUSPENDED || confirmation != null || !scope.isOpen()) return false;
            if (revision == data.revision() && data.synchronizedData()) {
                definition.dataSchema().validate(fields, true);
                return fields.equals(data.data());
            }
            return restore(revision, fields);
        }

        boolean resume() {
            var minecraft = Minecraft.getInstance();
            if (state != State.SUSPENDED || confirmation != null || !scope.isOpen()
                    || !snapshot.synchronizedData() || active != null || minecraft.getOverlay() != null
                    || minecraft.screen != null && minecraft.screen != screen) return false;
            active = this;
            state = State.ACTIVE;
            publish();
            screen.open();
            return true;
        }

        boolean confirm(UUID requestId, UiActionPrompt prompt, long expires, Consumer<Boolean> response) {
            var minecraft = Minecraft.getInstance();
            if (confirmation != null || state != State.ACTIVE || !scope.isOpen() || !requestId.equals(request)
                    || pendingAction == null || definition.actions().get(pendingAction).policy() != UiActionPolicy.CONFIRMED
                    || screen == null || minecraft.screen != screen || minecraft.getOverlay() != null
                    || !snapshot.synchronizedData()) return false;
            saveFocus();
            deadline = expires;
            state = State.SUSPENDED;
            var message = Component.translatable("sh_better_ui.pages.action.confirm_details",
                    Component.literal(prompt.subject()), Long.toString(prompt.quantity()),
                    Component.literal(prompt.consequence()));
            confirmation = new ActionConfirmScreen(message, accepted -> {
                if (confirmation == null || minecraft.screen != confirmation || !requestId.equals(request)
                        || !scope.isOpen()) return;
                dismissConfirmation(true);
                response.accept(accepted);
            });
            publish();
            minecraft.setScreen(confirmation);
            return true;
        }

        void extendRequest(UUID requestId, long expires) {
            if (requestId.equals(request)) deadline = expires;
        }

        private void dismissConfirmation(boolean returnToPage) {
            var dialog = confirmation;
            confirmation = null;
            if (dialog == null) return;
            var minecraft = Minecraft.getInstance();
            if (minecraft.screen != dialog) return;
            if (returnToPage && scope.isOpen()) {
                state = State.ACTIVE;
                publish();
                screen.open();
            } else {
                minecraft.setScreen(null);
            }
        }

        @Override public void close() { finish(State.CLOSED, true); }

        void nativeRemoved() {
            if (state == State.SUSPENDED && scope.isOpen()) return;
            nativeRemoval = true;
            finish(State.CLOSED, false);
        }

        private void finish(State result, boolean disposeScreen) {
            host.assertOwner();
            if (!scope.isOpen()) return;
            nativeRemoval |= !disposeScreen;
            state = result;
            scope.close();
        }

        private void released() {
            if (active == this) active = null;
            instances.remove(id, this);
            if (state != State.FAILED) state = State.CLOSED;
            if (PageStudioReport.CAPTURE && view != null) recentStudioPage = studioPage();
            publish();
            try {
                dismissConfirmation(false);
                if (screen != null && !nativeRemoval) screen.dispose();
            } finally {
                displayed.complete(false);
                try {
                    closed.run();
                } finally {
                    try {
                        if (remote && owner.isOpen()) owner.close();
                    } finally {
                        terminal.complete(snapshot);
                    }
                }
            }
        }
    }

    private static final class ActionConfirmScreen extends ConfirmScreen {
        private final Consumer<Boolean> response;

        ActionConfirmScreen(Component details, Consumer<Boolean> response) {
            super(response::accept, Component.translatable("sh_better_ui.pages.action.confirm_title"), details,
                    Component.translatable("sh_better_ui.pages.action.confirm"),
                    Component.translatable("gui.cancel"));
            this.response = response;
        }

        @Override public void onClose() { response.accept(false); }
    }
}
