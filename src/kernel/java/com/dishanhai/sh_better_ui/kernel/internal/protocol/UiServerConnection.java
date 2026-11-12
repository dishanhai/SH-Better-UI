// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.protocol;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionPolicy;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiActionPrompt;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiActionState;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiErrorCode;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiOpenResult;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiOpenResult.Status;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiInteractionProof;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiInteractionTarget;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocol;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolCapabilities;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolChannel;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolFraming.Reassembler;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolLimits;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.*;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.Error;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolTrace;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** One authoritative page coordinator per authenticated player connection, confined to the platform server thread. */
public final class UiServerConnection implements AutoCloseable {
    private static final long SECOND = Duration.ofSeconds(1).toNanos();
    private static final long OPEN_TIMEOUT = Duration.ofSeconds(10).toNanos();
    private static final long HANDSHAKE_TIMEOUT = Duration.ofSeconds(5).toNanos();
    private static final long NOTIFICATION_LIFETIME = Duration.ofSeconds(15).toNanos();
    private static final long PAGE_LIFETIME = Duration.ofMinutes(30).toNanos();
    private static final long CONFIRMATION_TIMEOUT = Duration.ofSeconds(30).toNanos();

    private final Thread ownerThread = Thread.currentThread();
    private final UUID playerId;
    private final Consumer<byte[]> sendFrame;
    private final UiProtocolCapabilities advertised;
    private final UiProtocolLimits limits;
    private final LongSupplier clock;
    private final Reassembler initial = new Reassembler();
    private final UiInteractionLedger interactions = new UiInteractionLedger();
    private final Map<UUID, PageSession> pages = new LinkedHashMap<>();
    private final Map<ActionKey, ActionHandler> handlers = new HashMap<>();
    private final Window inboundBudget;
    private final Window actionBudget;
    private final Window snapshotBudget;
    private final Window updateBudget;
    private final Window notificationBudget;
    private final Window interactionBudget = new Window(8, SECOND);
    private UiProtocolChannel channel;
    private UiProtocolCapabilities peer;
    private UiProtocolCapabilities.Negotiated negotiated;
    private UiErrorCode negotiationError;
    private long handshakeStarted;
    private long invalidFrames;
    private long droppedFrames;
    private boolean handlingAction;
    private Confirmation confirmation;
    private Consumer<UiInteractionProof> pageRequests = ignored -> {};
    private boolean tracing;
    private boolean closed;

    public UiServerConnection(UUID playerId, Consumer<byte[]> sendFrame, UiProtocolCapabilities available,
                              LongSupplier nanoTime) {
        this(playerId, sendFrame, available, nanoTime, UiProtocolLimits.DEFAULT);
    }

    public UiServerConnection(UUID playerId, Consumer<byte[]> sendFrame, UiProtocolCapabilities available,
                              LongSupplier nanoTime, UiProtocolLimits limits) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.sendFrame = Objects.requireNonNull(sendFrame, "sendFrame");
        advertised = Objects.requireNonNull(available, "available");
        clock = Objects.requireNonNull(nanoTime, "nanoTime");
        this.limits = Objects.requireNonNull(limits, "limits");
        inboundBudget = new Window(limits.inboundFramesPerSecond(), SECOND);
        actionBudget = new Window(limits.actionsPerSecond(), SECOND);
        snapshotBudget = new Window(limits.snapshotsPerSecond(), SECOND);
        updateBudget = new Window(limits.updatesPerSecond(), SECOND);
        notificationBudget = new Window(limits.notificationsPerFiveSeconds(), 5 * SECOND);
    }

    /** Initiates negotiation when the adapter has confirmed that the peer listens on the channel. */
    public void start() {
        checkThread();
        if (closed || channel != null || negotiationError != null) {
            return;
        }
        channel = new UiProtocolChannel(UUID.randomUUID(), sendFrame);
        channel.setTracing(tracing);
        handshakeStarted = clock.getAsLong();
        send(new Hello(advertised));
    }

    public boolean available() {
        checkThread();
        return !closed && negotiated != null;
    }

    public void receive(byte[] frame) {
        checkThread();
        if (closed || negotiationError != null) {
            return;
        }
        long now = clock.getAsLong();
        if (!inboundBudget.take(now)) {
            ++droppedFrames;
            return;
        }
        try {
            if (channel == null) {
                UiProtocolChannel.acceptInitial(initial, frame, now).ifPresent(message -> respondToHello(message, now));
            } else {
                channel.receive(frame, now).ifPresent(message -> receivePayload(message.payload(), now));
            }
        } catch (IllegalArgumentException exception) {
            ++invalidFrames;
        }
    }

    private void respondToHello(UiProtocolMessage message, long now) {
        channel = new UiProtocolChannel(message, sendFrame);
        channel.setTracing(tracing);
        handshakeStarted = now;
        acceptPeer(((Hello) message.payload()).capabilities(), true);
    }

    private void receivePayload(Payload payload, long now) {
        if (payload instanceof Terminate) {
            end(false);
            return;
        }
        if (negotiated == null) {
            if (now - handshakeStarted >= HANDSHAKE_TIMEOUT) {
                channel = null;
                return;
            }
            if (payload instanceof Capabilities capabilities) {
                acceptPeer(capabilities.capabilities(), false);
            } else if (payload instanceof Error error && error.code() == UiErrorCode.PROTOCOL_INCOMPATIBLE) {
                negotiationError = error.code();
            }
            return;
        }
        if (payload instanceof PageOpened result) {
            acknowledge(result, now);
        } else if (payload instanceof PageClose close) {
            removePage(close.instanceId(), false);
        } else if (payload instanceof HudUnmount close) {
            PageSession session = pages.get(close.instanceId());
            if (session != null && session.page.type() == UiPageType.HUD) removePage(close.instanceId(), false);
        } else if (payload instanceof StateRequest request && snapshotBudget.take(now)) {
            PageSession session = pages.get(request.instanceId());
            if (session != null && session.interactive(now) && authorized(session)) {
                publishSnapshot(session);
            }
        } else if (payload instanceof ActionRequest request && actionBudget.take(now)) {
            performAction(request, now, null);
        } else if (payload instanceof ActionConfirm reply && actionBudget.take(now)) {
            confirm(reply, now);
        } else if (payload instanceof InteractionRequest interaction && supports(UiProtocol.INTERACTION_OPEN)
                && interactionBudget.take(now)) {
            if (interactions.record(interaction.proof(), now)
                    && interaction.proof().target() instanceof UiInteractionTarget.Page) {
                pageRequests.accept(interaction.proof());
            }
        } else if (payload instanceof PageVisibility visibility && supports(UiProtocol.PRIORITY_MODAL)) {
            visibility(visibility, now);
        }
    }

    private void acceptPeer(UiProtocolCapabilities capabilities, boolean reply) {
        var common = advertised.negotiate(capabilities);
        if (common.isEmpty()) {
            negotiationError = UiErrorCode.PROTOCOL_INCOMPATIBLE;
            send(new Error(negotiationError));
            return;
        }
        if (reply && !send(new Capabilities(advertised))) {
            return;
        }
        peer = capabilities;
        negotiated = common.orElseThrow();
        channel.limitMessages(negotiated.maxMessageBytes());
    }

    public CompletionStage<UiOpenResult> open(UiPageDefinition page, Map<String, UiValue> initialData) {
        return open(page, initialData, result -> {});
    }

    /** The progress observer runs on the owner thread. The future completes with a terminal result. */
    public CompletionStage<UiOpenResult> open(UiPageDefinition page, Map<String, UiValue> initialData,
                                              Consumer<UiOpenResult> progress) {
        return open(page, initialData, progress, () -> true);
    }

    public CompletionStage<UiOpenResult> open(UiPageDefinition page, Map<String, UiValue> initialData,
                                              Consumer<UiOpenResult> progress, BooleanSupplier stillAuthorized) {
        return open(page, initialData, progress, stillAuthorized, Optional.empty(), false);
    }

    public CompletionStage<UiOpenResult> openForInteraction(UiPageDefinition page, Map<String, UiValue> initialData,
                                                            Consumer<UiOpenResult> progress, BooleanSupplier stillAuthorized,
                                                            UiInteractionTarget target) {
        checkThread();
        if (!available()) return unavailable();
        if (!supports(UiProtocol.INTERACTION_OPEN)) return rejected(UiErrorCode.CAPABILITY_MISSING);
        if (target instanceof UiInteractionTarget.Page request && !request.pageId().equals(page.id())) {
            return rejected(UiErrorCode.INVALID_INTERACTION);
        }
        var proof = interactions.take(target, clock.getAsLong());
        if (proof.isEmpty()) return rejected(UiErrorCode.INVALID_INTERACTION);
        return open(page, initialData, progress, stillAuthorized, proof, false);
    }

    public CompletionStage<UiOpenResult> openPriorityModal(UiPageDefinition page, Map<String, UiValue> initialData,
                                                          Consumer<UiOpenResult> progress, BooleanSupplier stillAuthorized) {
        checkThread();
        if (!available()) return unavailable();
        if (!supports(UiProtocol.PRIORITY_MODAL)) return rejected(UiErrorCode.CAPABILITY_MISSING);
        if (page.type() != UiPageType.MODAL) return rejected(UiErrorCode.SCHEMA_INVALID);
        return open(page, initialData, progress, stillAuthorized, Optional.empty(), true);
    }

    private CompletionStage<UiOpenResult> open(UiPageDefinition page, Map<String, UiValue> initialData,
                                               Consumer<UiOpenResult> progress, BooleanSupplier stillAuthorized,
                                               Optional<UiInteractionProof> interaction, boolean highPriority) {
        checkThread();
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(stillAuthorized, "stillAuthorized");
        if (!available()) {
            return unavailable();
        }
        UiErrorCode preflight = checkPage(page);
        if (preflight != null) {
            return rejected(preflight);
        }
        var data = Map.copyOf(initialData);
        try {
            page.dataSchema().validate(data, true);
        } catch (IllegalArgumentException exception) {
            return rejected(UiErrorCode.SCHEMA_INVALID);
        }
        UUID instanceId = UUID.randomUUID();
        PageOpen request = new PageOpen(instanceId, page, data, interaction, highPriority);
        if (!channel.canSend(request) || !channel.canSend(new StateSnapshot(instanceId, 0, data))) {
            return rejected(UiErrorCode.RESOURCE_LIMIT_EXCEEDED);
        }
        long now = clock.getAsLong();
        if (page.type() == UiPageType.NOTIFICATION && !notificationBudget.take(now)) {
            return rejected(UiErrorCode.RATE_LIMITED);
        }
        var session = new PageSession(instanceId, page, data, now, progress, stillAuthorized);
        pages.put(instanceId, session);
        if (!authorized(session)) {
            pages.remove(instanceId, session);
            session.report(Status.REJECTED, Optional.of(UiErrorCode.PERMISSION_DENIED), true);
            return session.result.minimalCompletionStage();
        }
        session.report(Status.RECEIVED, Optional.empty(), false);
        if (pages.get(instanceId) == session) {
            send(request);
        }
        return session.result.minimalCompletionStage();
    }

    private UiErrorCode checkPage(UiPageDefinition page) {
        if (!page.schemaVersion().equals(negotiated.schemaVersion())) {
            return UiErrorCode.SCHEMA_INVALID;
        }
        if (!advertised.pageTypes().contains(page.type()) || !peer.pageTypes().contains(page.type())) {
            return page.type() == UiPageType.CONTAINER
                    ? UiErrorCode.CONTAINER_UNAVAILABLE : UiErrorCode.CAPABILITY_MISSING;
        }
        if (page.actions().values().stream().anyMatch(action -> action.policy() == UiActionPolicy.CONFIRMED)
                && !supports(UiProtocol.CONFIRMED_ACTION)) {
            return UiErrorCode.CAPABILITY_MISSING;
        }
        for (var requirement : page.options().requiredCapabilities().entrySet()) {
            if (!peer.supports(requirement.getKey(), requirement.getValue())) {
                return UiErrorCode.CAPABILITY_MISSING;
            }
        }
        if (pages.size() >= limits.maxPages() || page.type() == UiPageType.HUD
                && pages.values().stream().filter(session -> session.page.type() == UiPageType.HUD).count() >= limits.maxHuds()) {
            return UiErrorCode.RESOURCE_LIMIT_EXCEEDED;
        }
        return null;
    }

    private boolean supports(UiId capability) {
        return peer != null && advertised.supports(capability, UiProtocol.VERSION)
                && peer.supports(capability, UiProtocol.VERSION);
    }

    public void onPageRequest(Consumer<UiInteractionProof> listener) {
        checkThread();
        pageRequests = Objects.requireNonNull(listener, "page request listener");
    }

    public void rejectInteraction(UiInteractionProof proof, UiErrorCode error) {
        checkThread();
        if (!available() || !supports(UiProtocol.INTERACTION_OPEN)) return;
        interactions.consume(proof, clock.getAsLong());
        send(new InteractionRejected(proof.attempt(), proof.token(), error));
    }

    private CompletionStage<UiOpenResult> unavailable() {
        UiErrorCode code = closed ? UiErrorCode.SESSION_CLOSED : negotiationError;
        return rejected(code == null ? UiErrorCode.CLIENT_UNAVAILABLE : code);
    }

    private static CompletionStage<UiOpenResult> rejected(UiErrorCode error) {
        return CompletableFuture.completedStage(UiOpenResult.rejected(error));
    }

    private void acknowledge(PageOpened result, long now) {
        PageSession session = pages.get(result.instanceId());
        if (session == null || session.displayed || now - session.started >= OPEN_TIMEOUT) {
            return;
        }
        if (!authorized(session)) {
            removePage(session.id, true);
            return;
        }
        Status status = result.status();
        if (status == Status.REJECTED) {
            pages.remove(session.id);
            session.report(status, result.error(), true);
        } else if (status.ordinal() > session.status.ordinal()) {
            boolean displayed = status == Status.DISPLAYED;
            if (displayed) {
                session.displayed = true;
                session.displayedAt = now;
            }
            session.report(status, result.error(), displayed);
        }
    }

    public Optional<UiId> pageId(UUID instanceId) {
        checkThread();
        return Optional.ofNullable(pages.get(instanceId)).map(session -> session.page.id());
    }

    public boolean hasPage(UiId pageId) {
        checkThread();
        return pages.values().stream().anyMatch(session -> session.page.id().equals(pageId));
    }

    public boolean closePage(UUID instanceId) {
        checkThread();
        return removePage(instanceId, true);
    }

    public void closePages(UiId pageId) {
        checkThread();
        for (PageSession session : List.copyOf(pages.values())) {
            if (session.page.id().equals(pageId)) {
                removePage(session.id, true);
            }
        }
    }

    private boolean removePage(UUID instanceId, boolean notifyPeer) {
        PageSession removed = pages.remove(instanceId);
        if (removed == null) {
            return false;
        }
        if (confirmation != null && confirmation.session == removed) confirmation = null;
        removed.report(Status.CLOSED, Optional.of(UiErrorCode.SESSION_CLOSED), true);
        if (notifyPeer) {
            sendClose(removed);
        }
        return true;
    }

    private void sendClose(PageSession session) {
        send(session.page.type() == UiPageType.HUD ? new HudUnmount(session.id) : new PageClose(session.id));
    }

    public boolean update(UUID instanceId, Map<String, UiValue> values) {
        checkThread();
        PageSession session = pages.get(instanceId);
        return session != null && session.active(clock.getAsLong()) && authorized(session) && queueUpdate(session, values);
    }

    private boolean authorized(PageSession session) {
        try {
            return !closed && pages.get(session.id) == session && session.stillAuthorized.getAsBoolean()
                    && !closed && pages.get(session.id) == session;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void visibility(PageVisibility visibility, long now) {
        var session = pages.get(visibility.instanceId());
        if (session == null || !session.active(now) || !authorized(session)) {
            if (!visibility.suspended()) {
                send(new PageResumed(visibility.instanceId(), 0, Map.of(), Optional.of(UiErrorCode.SESSION_EXPIRED)));
            }
            if (session != null) removePage(session.id, true);
            return;
        }
        if (visibility.suspended()) {
            session.suspended = true;
            if (confirmation != null && confirmation.session == session) {
                finishConfirmation(confirmation, UiActionState.CANCELLED, UiErrorCode.ACTION_CANCELLED);
            }
        } else if (snapshotBudget.take(now)) {
            var result = new PageResumed(session.id, session.revision, session.data, Optional.empty());
            if (!channel.canSend(result)) {
                removePage(session.id, true);
                return;
            }
            session.publishedRevision = session.revision;
            session.changes = Map.of();
            session.suspended = false;
            send(result);
        }
    }

    private boolean queueUpdate(PageSession session, Map<String, UiValue> values) {
        var requested = Map.copyOf(values);
        if (requested.isEmpty()) {
            return true;
        }
        try {
            session.page.dataSchema().validate(requested, false);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        var data = new HashMap<>(session.data);
        data.putAll(requested);
        if (data.equals(session.data)) {
            return true;
        }
        if (session.revision == Long.MAX_VALUE) {
            return false;
        }
        long revision = session.revision + 1;
        var changes = new HashMap<>(session.changes);
        changes.putAll(requested);
        if (!channel.canSend(new DataUpdate(session.id, session.publishedRevision, revision, changes))
                || !channel.canSend(new StateSnapshot(session.id, revision, data))) {
            return false;
        }
        session.data = Map.copyOf(data);
        session.revision = revision;
        session.changes = Map.copyOf(changes);
        return true;
    }

    private void flush(PageSession session) {
        if (!session.changes.isEmpty()) {
            var patch = new DataUpdate(session.id, session.publishedRevision, session.revision, session.changes);
            session.publishedRevision = session.revision;
            session.changes = Map.of();
            send(patch);
        }
    }

    private void publishSnapshot(PageSession session) {
        var snapshot = new StateSnapshot(session.id, session.revision, session.data);
        session.publishedRevision = session.revision;
        session.changes = Map.of();
        send(snapshot);
    }

    public AutoCloseable registerAction(UiId id, Predicate<UUID> permission, Function<ActionContext, ActionOutcome> handler) {
        return registerAction(new ActionKey(Optional.empty(), Objects.requireNonNull(id)), permission, Optional.empty(), handler);
    }

    public AutoCloseable registerAction(UiId pageId, UiId actionId, Predicate<UUID> permission,
                                        Function<ActionContext, ActionOutcome> handler) {
        return registerAction(pageId, actionId, permission, Optional.empty(), handler);
    }

    public AutoCloseable registerAction(UiId pageId, UiId actionId, Predicate<UUID> permission,
                                        Function<ActionContext, UiActionPrompt> preview,
                                        Function<ActionContext, ActionOutcome> handler) {
        return registerAction(pageId, actionId, permission, Optional.of(preview), handler);
    }

    private AutoCloseable registerAction(UiId pageId, UiId actionId, Predicate<UUID> permission,
                                         Optional<Function<ActionContext, UiActionPrompt>> preview,
                                         Function<ActionContext, ActionOutcome> handler) {
        if (!Objects.requireNonNull(pageId).namespace().equals(Objects.requireNonNull(actionId).namespace())) {
            throw new IllegalArgumentException("Page action must share the page namespace");
        }
        return registerAction(new ActionKey(Optional.of(pageId), actionId), permission, preview, handler);
    }

    private AutoCloseable registerAction(ActionKey key, Predicate<UUID> permission,
                                         Optional<Function<ActionContext, UiActionPrompt>> preview,
                                         Function<ActionContext, ActionOutcome> handler) {
        checkThread();
        if (closed) {
            throw new IllegalStateException("Page connection is closed");
        }
        if (handlers.size() >= limits.maxActionRegistrations()) {
            throw new IllegalStateException("Page action registration limit reached");
        }
        var registration = new ActionHandler(Objects.requireNonNull(permission), preview, Objects.requireNonNull(handler));
        if (handlers.putIfAbsent(key, registration) != null) {
            throw new IllegalArgumentException("Page action already registered");
        }
        return () -> {
            checkThread();
            if (handlers.get(key) == registration) {
                handlers.remove(key);
                if (confirmation != null && confirmation.handler == registration) {
                    finishConfirmation(confirmation, UiActionState.CANCELLED, UiErrorCode.PERMISSION_DENIED);
                }
            }
        };
    }

    private void performAction(ActionRequest request, long now, Confirmation approved) {
        PageSession session = pages.get(request.instanceId());
        if (session == null || !session.interactive(now)) {
            send(failure(request, UiErrorCode.SESSION_EXPIRED));
            return;
        }
        ActionReceipt previous = session.completedActions.get(request.requestId());
        if (previous != null && !previous.actionId.equals(request.actionId())) {
            send(failure(request, UiErrorCode.ACTION_INVALID));
            return;
        }
        if (session.runningActions.contains(request.requestId())) {
            return;
        }
        if (handlingAction) {
            send(failure(request, UiErrorCode.ACTION_BUSY));
            return;
        }
        if (confirmation != null) {
            if (confirmation.request.equals(request)) send(confirmation.challenge);
            else send(failure(request, UiErrorCode.ACTION_BUSY));
            return;
        }
        if (previous == null
                && session.completedActions.size() + session.runningActions.size() >= limits.maxActionReceipts()) {
            send(failure(request, UiErrorCode.RESOURCE_LIMIT_EXCEEDED));
            return;
        }
        session.runningActions.add(request.requestId());
        handlingAction = true;
        ActionResult result;
        try {
            result = authorizeAndExecute(session, request, previous, approved);
        } catch (RuntimeException exception) {
            result = failure(request, UiErrorCode.INTERNAL_ERROR);
        } finally {
            session.runningActions.remove(request.requestId());
            handlingAction = false;
        }
        if (result == null) {
            var pending = confirmation;
            if (pending != null && !send(pending.challenge)) confirmation = null;
            return;
        }
        if (previous == null) {
            session.completedActions.put(request.requestId(), new ActionReceipt(request.actionId(), result));
        }
        if (pages.get(session.id) == session) {
            if (result.state() == UiActionState.SUCCEEDED && previous == null) {
                flush(session);
            } else if (result.error().filter(error -> error == UiErrorCode.REVISION_MISMATCH).isPresent()
                    && snapshotBudget.take(now)) {
                publishSnapshot(session);
            }
            send(result);
        }
    }

    private ActionResult authorizeAndExecute(PageSession session, ActionRequest request, ActionReceipt previous,
                                             Confirmation approved) {
        var definition = session.page.actions().get(request.actionId());
        ActionHandler handler = actionHandler(session.page.id(), request.actionId());
        if (definition == null || handler == null || definition.policy() == UiActionPolicy.LOCAL
                || definition.policy() == UiActionPolicy.CONTAINER) {
            return failure(request, UiErrorCode.ACTION_INVALID);
        }
        if (!authorized(session) || !handler.permission.test(playerId)
                || actionHandler(session.page.id(), request.actionId()) != handler) {
            return failure(request, UiErrorCode.PERMISSION_DENIED);
        }
        if (pages.get(session.id) != session || !session.interactive(clock.getAsLong())) {
            return failure(request, UiErrorCode.SESSION_EXPIRED);
        }
        if (previous != null) {
            return previous.result;
        }
        if (session.revision != request.revision()) {
            return failure(request, UiErrorCode.REVISION_MISMATCH);
        }
        try {
            definition.payloadSchema().validate(request.data(), true);
        } catch (IllegalArgumentException exception) {
            return failure(request, UiErrorCode.ACTION_INVALID);
        }
        var context = new ActionContext(playerId, session.id, session.page, request.requestId(),
                request.actionId(), session.revision, session.data, request.data());
        if (definition.policy() == UiActionPolicy.CONFIRMED) {
            if (handler.preview.isEmpty() || !supports(UiProtocol.CONFIRMED_ACTION)) {
                return failure(request, UiErrorCode.CONFIRMATION_REQUIRED);
            }
            var prompt = Objects.requireNonNull(handler.preview.orElseThrow().apply(context), "action preview");
            long now = clock.getAsLong();
            if (!authorized(session) || actionHandler(session.page.id(), request.actionId()) != handler
                    || !session.interactive(now) || session.revision != request.revision()) {
                return failure(request, UiErrorCode.CONFIRMATION_EXPIRED);
            }
            if (approved == null) {
                var challenge = new ActionChallenge(session.id, request.requestId(), UUID.randomUUID(), prompt);
                if (!channel.canSend(challenge)) return failure(request, UiErrorCode.RESOURCE_LIMIT_EXCEEDED);
                confirmation = new Confirmation(session, request, handler, challenge, now);
                return null;
            }
            if (approved.handler != handler || !approved.challenge.prompt().equals(prompt)
                    || now - approved.started >= CONFIRMATION_TIMEOUT) {
                return failure(request, UiErrorCode.CONFIRMATION_EXPIRED);
            }
        }
        ActionOutcome outcome = Objects.requireNonNull(handler.execute.apply(context), "action outcome");
        if (!outcome.succeeded()) {
            return failure(request, outcome.error().orElseThrow());
        }
        if (!authorized(session)) {
            if (pages.get(session.id) == session) removePage(session.id, true);
            return failure(request, UiErrorCode.PERMISSION_DENIED);
        }
        if (!queueUpdate(session, outcome.updates())) {
            return failure(request, UiErrorCode.INTERNAL_ERROR);
        }
        return new ActionResult(session.id, request.requestId(), UiActionState.SUCCEEDED, Optional.empty());
    }

    private void confirm(ActionConfirm reply, long now) {
        var pending = confirmation;
        if (handlingAction || pending == null || !pending.challenge.instanceId().equals(reply.instanceId())
                || !pending.challenge.requestId().equals(reply.requestId())
                || !pending.challenge.token().equals(reply.token())) return;
        if (now - pending.started >= CONFIRMATION_TIMEOUT) {
            finishConfirmation(pending, UiActionState.TIMED_OUT, UiErrorCode.CONFIRMATION_EXPIRED);
        } else if (!reply.accepted()) {
            finishConfirmation(pending, UiActionState.CANCELLED, UiErrorCode.ACTION_CANCELLED);
        } else {
            confirmation = null;
            performAction(pending.request, now, pending);
        }
    }

    private void finishConfirmation(Confirmation pending, UiActionState state, UiErrorCode error) {
        confirmation = null;
        if (pages.get(pending.session.id) != pending.session) return;
        var result = new ActionResult(pending.session.id, pending.request.requestId(), state, Optional.of(error));
        pending.session.completedActions.put(pending.request.requestId(),
                new ActionReceipt(pending.request.actionId(), result));
        send(result);
    }

    private ActionHandler actionHandler(UiId pageId, UiId actionId) {
        ActionHandler scoped = handlers.get(new ActionKey(Optional.of(pageId), actionId));
        return scoped != null ? scoped : handlers.get(new ActionKey(Optional.empty(), actionId));
    }

    private static ActionResult failure(ActionRequest request, UiErrorCode error) {
        return new ActionResult(request.instanceId(), request.requestId(), UiActionState.FAILED, Optional.of(error));
    }

    public void tick() {
        checkThread();
        if (closed) {
            return;
        }
        long now = clock.getAsLong();
        initial.expire(now);
        interactions.expire(now);
        if (confirmation != null && now - confirmation.started >= CONFIRMATION_TIMEOUT) {
            finishConfirmation(confirmation, UiActionState.TIMED_OUT, UiErrorCode.CONFIRMATION_EXPIRED);
        }
        if (channel != null) {
            channel.expireFragments(now);
            if (negotiated == null && now - handshakeStarted >= HANDSHAKE_TIMEOUT) {
                channel = null;
            }
        }
        for (PageSession session : List.copyOf(pages.values())) {
            if (pages.get(session.id) != session) {
                continue;
            }
            if (now - session.lastAuthorizationCheck >= SECOND && !checkAuthorization(session, now)) {
                removePage(session.id, true);
            } else if (!session.displayed && now - session.started >= OPEN_TIMEOUT) {
                pages.remove(session.id);
                session.report(Status.TIMED_OUT, Optional.of(UiErrorCode.OPEN_TIMEOUT), true);
                sendClose(session);
            } else if (session.displayed && !session.active(now)) {
                removePage(session.id, true);
            } else if (session.interactive(now) && !session.changes.isEmpty() && updateBudget.take(now)) {
                flush(session);
            }
        }
    }

    private boolean checkAuthorization(PageSession session, long now) {
        session.lastAuthorizationCheck = now;
        return authorized(session);
    }

    public Snapshot snapshot() {
        checkThread();
        return new Snapshot(available(), pages.size(), invalidFrames, droppedFrames,
                Optional.ofNullable(negotiationError));
    }

    public void setTracing(boolean enabled) {
        checkThread();
        tracing = enabled;
        if (channel != null) {
            channel.setTracing(enabled);
        }
    }

    public List<UiProtocolTrace> traceSnapshot() {
        checkThread();
        return channel == null ? List.of() : channel.traceSnapshot();
    }

    @Override
    public void close() {
        checkThread();
        end(true);
    }

    private void end(boolean notifyPeer) {
        if (closed) {
            return;
        }
        closed = true;
        if (notifyPeer && negotiated != null) {
            send(new Terminate());
        }
        var removed = List.copyOf(pages.values());
        pages.clear();
        handlers.clear();
        confirmation = null;
        interactions.clear();
        pageRequests = ignored -> {};
        channel = null;
        peer = null;
        negotiated = null;
        for (PageSession session : removed) {
            session.report(Status.CLOSED, Optional.of(UiErrorCode.SESSION_CLOSED), true);
        }
    }

    private boolean send(Payload payload) {
        if (channel == null) {
            return false;
        }
        try {
            channel.send(payload);
            return true;
        } catch (RuntimeException exception) {
            end(false);
            return false;
        }
    }

    private void checkThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Page connection must be called from its server thread");
        }
    }

    public record Snapshot(boolean available, int pages, long invalidFrames, long droppedFrames,
                           Optional<UiErrorCode> negotiationError) {}

    public record ActionContext(UUID playerId, UUID instanceId, UiPageDefinition page, UUID requestId, UiId actionId,
                                long revision, Map<String, UiValue> authoritativeData, Map<String, UiValue> data) {
        public ActionContext {
            authoritativeData = Map.copyOf(authoritativeData);
            data = Map.copyOf(data);
        }
    }

    public record ActionOutcome(boolean succeeded, Map<String, UiValue> updates, Optional<UiErrorCode> error) {
        public ActionOutcome {
            updates = Map.copyOf(updates);
            Objects.requireNonNull(error, "error");
            if (succeeded == error.isPresent() || !succeeded && !updates.isEmpty()) {
                throw new IllegalArgumentException("Action outcome status, updates and error disagree");
            }
        }

        public static ActionOutcome success(Map<String, UiValue> updates) {
            return new ActionOutcome(true, updates, Optional.empty());
        }

        public static ActionOutcome failure(UiErrorCode error) {
            return new ActionOutcome(false, Map.of(), Optional.of(error));
        }
    }

    private record ActionKey(Optional<UiId> pageId, UiId actionId) {}

    private record ActionHandler(Predicate<UUID> permission, Optional<Function<ActionContext, UiActionPrompt>> preview,
                                 Function<ActionContext, ActionOutcome> execute) {}
    private record ActionReceipt(UiId actionId, ActionResult result) {}

    private record Confirmation(PageSession session, ActionRequest request, ActionHandler handler,
                                ActionChallenge challenge, long started) {}

    private static final class PageSession {
        final UUID id;
        final UiPageDefinition page;
        final long started;
        final Consumer<UiOpenResult> progress;
        final BooleanSupplier stillAuthorized;
        final CompletableFuture<UiOpenResult> result = new CompletableFuture<>();
        final Map<UUID, ActionReceipt> completedActions = new HashMap<>();
        final Set<UUID> runningActions = new HashSet<>();
        Map<String, UiValue> data;
        Map<String, UiValue> changes = Map.of();
        Status status = Status.RECEIVED;
        long revision;
        long publishedRevision;
        long displayedAt;
        long lastAuthorizationCheck;
        boolean displayed;
        boolean suspended;

        PageSession(UUID id, UiPageDefinition page, Map<String, UiValue> data, long started,
                    Consumer<UiOpenResult> progress, BooleanSupplier stillAuthorized) {
            this.id = id;
            this.page = page;
            this.data = data;
            this.started = started;
            this.lastAuthorizationCheck = started;
            this.progress = progress;
            this.stillAuthorized = stillAuthorized;
        }

        boolean active(long now) {
            long lifetime = page.type() == UiPageType.NOTIFICATION ? NOTIFICATION_LIFETIME : PAGE_LIFETIME;
            return displayed && now - displayedAt < lifetime;
        }

        boolean interactive(long now) {
            return !suspended && active(now);
        }

        void report(Status next, Optional<UiErrorCode> error, boolean terminal) {
            status = next;
            UiOpenResult update = new UiOpenResult(id, next, error);
            if (terminal && !result.complete(update)) {
                return;
            }
            try {
                progress.accept(update);
            } catch (RuntimeException ignored) {
                // An external observer cannot interrupt connection state transitions.
            }
        }
    }

    private static final class Window {
        private final int limit;
        private final long duration;
        private long start;
        private int used;

        Window(int limit, long duration) {
            this.limit = limit;
            this.duration = duration;
        }

        boolean take(long now) {
            if (used == 0 || now - start >= duration) {
                used = 0;
                start = now;
            }
            if (used >= limit) {
                return false;
            }
            ++used;
            return true;
        }
    }
}
