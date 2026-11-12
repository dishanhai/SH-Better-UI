// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.protocol;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionPolicy;
import com.dishanhai.sh_better_ui.kernel.api.page.UiDataSchema;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValueSchema;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiActionState;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiActionPrompt;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiErrorCode;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiInteractionProof;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiInteractionTarget;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiOpenResult;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocol;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolCapabilities;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolChannel;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolFraming;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolLimits;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.*;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.Error;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

final class UiServerConnectionTest {
    private static final UiId EXCHANGE = UiId.parse("example:exchange");
    private static final UiId BUY = UiId.parse("example:buy");
    private static final UiProtocolCapabilities CAPABILITIES =
            UiProtocolCapabilities.standard(Map.of(UiProtocol.CONFIRMED_ACTION, UiProtocol.VERSION,
                    UiProtocol.INTERACTION_OPEN, UiProtocol.VERSION, UiProtocol.PRIORITY_MODAL, UiProtocol.VERSION),
                    Set.of(UiPageType.SCREEN, UiPageType.MODAL));
    private static final UiPageDefinition PAGE = UiPageDefinition.builder(EXCHANGE)
            .field("balance", UiValueSchema.integer(0, 1_000))
            .action(new UiActionDefinition(BUY, UiActionPolicy.SERVER,
                    new UiDataSchema(Map.of("count", UiValueSchema.integer(1, 3)))))
            .root("root", "button", button -> button.label("Exchange").action(BUY))
            .build();

    @Test
    void openWaitsForClientDisplayAndResolvesTimeoutRejectionAndClosure() {
        var wire = new Wire();
        assertEquals(Optional.of(UiErrorCode.CLIENT_UNAVAILABLE),
                wire.server.open(PAGE, balance(100)).toCompletableFuture().join().error());
        wire.startFromClient();
        var observed = new ArrayList<UiOpenResult.Status>();
        var opened = wire.server.open(PAGE, balance(100), result -> observed.add(result.status())).toCompletableFuture();
        assertFalse(opened.isDone());
        UUID id = assertInstanceOf(PageOpen.class, wire.readOne()).instanceId();
        wire.client.send(new PageOpened(id, UiOpenResult.Status.AUTHORIZED, Optional.empty()));
        assertFalse(opened.isDone());
        wire.client.send(new PageOpened(id, UiOpenResult.Status.CONFIRMED, Optional.empty()));
        assertFalse(opened.isDone());
        wire.client.send(new PageOpened(id, UiOpenResult.Status.DISPLAYED, Optional.empty()));
        assertEquals(id, opened.join().instanceId());
        assertEquals(UiOpenResult.Status.DISPLAYED, opened.join().status());
        assertEquals(List.of(UiOpenResult.Status.RECEIVED, UiOpenResult.Status.AUTHORIZED,
                UiOpenResult.Status.CONFIRMED, UiOpenResult.Status.DISPLAYED), observed);

        var rejected = wire.server.open(PAGE, balance(100)).toCompletableFuture();
        UUID denied = assertInstanceOf(PageOpen.class, wire.readOne()).instanceId();
        wire.client.send(new PageOpened(denied, UiOpenResult.Status.REJECTED, Optional.of(UiErrorCode.PERMISSION_DENIED)));
        assertEquals(Optional.of(UiErrorCode.PERMISSION_DENIED), rejected.join().error());
        assertTrue(wire.server.pageId(denied).isEmpty());

        var timedOut = wire.server.open(PAGE, balance(100)).toCompletableFuture();
        UUID expiring = assertInstanceOf(PageOpen.class, wire.readOne()).instanceId();
        wire.advance(11);
        assertEquals(UiOpenResult.Status.TIMED_OUT, timedOut.join().status());
        assertEquals(expiring, assertInstanceOf(PageClose.class, wire.readOne()).instanceId());

        var pending = wire.server.open(PAGE, balance(100)).toCompletableFuture();
        wire.readOne();
        wire.server.close();
        assertEquals(UiOpenResult.Status.CLOSED, pending.join().status());
        assertInstanceOf(Terminate.class, wire.readOne());
        assertFalse(wire.server.available());
        assertTrue(wire.server.pageId(id).isEmpty());
    }

    @Test
    void serverHandshakeDetectsIncompatibleProtocolAndCapabilities() {
        var wire = new Wire();
        wire.server.start();
        var first = UiProtocolChannel.acceptInitial(new UiProtocolFraming.Reassembler(),
                wire.toClient.remove(0), wire.time.get()).orElseThrow();
        wire.client = new UiProtocolChannel(first, wire.server::receive);
        wire.client.send(new Capabilities(CAPABILITIES));
        assertTrue(wire.server.available());
        UiPageDefinition container = UiPageDefinition.builder(EXCHANGE).type(UiPageType.CONTAINER)
                .root("root", "text", text -> text.text("Container")).build();
        assertEquals(Optional.of(UiErrorCode.CONTAINER_UNAVAILABLE),
                wire.server.open(container, Map.of()).toCompletableFuture().join().error());
        assertTrue(wire.toClient.isEmpty());

        var incompatible = new Wire();
        incompatible.client.send(new Hello(new UiProtocolCapabilities(Set.of(new UiVersion(2, 0, 0)),
                CAPABILITIES.schemaVersions(), Map.of(), CAPABILITIES.pageTypes(), CAPABILITIES.maxMessageBytes())));
        assertEquals(UiErrorCode.PROTOCOL_INCOMPATIBLE, assertInstanceOf(Error.class, incompatible.readOne()).code());
        assertEquals(Optional.of(UiErrorCode.PROTOCOL_INCOMPATIBLE),
                incompatible.server.open(PAGE, balance(100)).toCompletableFuture().join().error());
    }

    @Test
    void checksPermissionsAndSchemaAndExecutesAnAcceptedRequestOnlyOnce() throws Exception {
        var wire = new Wire();
        wire.startFromClient();
        UUID id = wire.open();
        var allowed = new AtomicBoolean();
        var charges = new AtomicInteger();
        try (var registration = wire.server.registerAction(BUY, player -> allowed.get(), context -> {
            assertEquals(wire.player, context.playerId());
            assertEquals(balance(100), context.authoritativeData());
            assertEquals(0, context.revision());
            charges.incrementAndGet();
            return UiServerConnection.ActionOutcome.success(balance(95));
        })) {
            UUID deniedId = UUID.randomUUID();
            wire.client.send(action(id, deniedId, 0, 1));
            assertEquals(Optional.of(UiErrorCode.PERMISSION_DENIED),
                    assertInstanceOf(ActionResult.class, wire.readOne()).error());
            allowed.set(true);
            wire.client.send(action(id, deniedId, 0, 1));
            assertEquals(Optional.of(UiErrorCode.PERMISSION_DENIED),
                    assertInstanceOf(ActionResult.class, wire.readOne()).error());
            wire.client.send(action(id, UUID.randomUUID(), 0, 99));
            assertEquals(Optional.of(UiErrorCode.ACTION_INVALID), assertInstanceOf(ActionResult.class, wire.readOne()).error());
            UUID requestId = UUID.randomUUID();
            wire.client.send(action(id, requestId, 0, 1));
            DataUpdate patch = assertInstanceOf(DataUpdate.class, wire.readOne());
            assertEquals(0, patch.baseRevision());
            assertEquals(1, patch.revision());
            assertEquals(balance(95), patch.updates());
            ActionResult receipt = assertInstanceOf(ActionResult.class, wire.readOne());
            assertEquals(UiActionState.SUCCEEDED, receipt.state());
            wire.client.send(action(id, requestId, 0, 1));
            assertEquals(receipt, wire.readOne());
            assertEquals(1, charges.get());

            wire.client.send(action(id, UUID.randomUUID(), 0, 1));
            StateSnapshot snapshot = assertInstanceOf(StateSnapshot.class, wire.readOne());
            assertEquals(1, snapshot.revision());
            assertEquals(balance(95), snapshot.data());
            assertEquals(Optional.of(UiErrorCode.REVISION_MISMATCH),
                    assertInstanceOf(ActionResult.class, wire.readOne()).error());
        }
        wire.client.send(action(id, UUID.randomUUID(), 1, 1));
        assertEquals(Optional.of(UiErrorCode.ACTION_INVALID), assertInstanceOf(ActionResult.class, wire.readOne()).error());
        wire.client.send(new PageClose(id));
        wire.client.send(action(id, UUID.randomUUID(), 1, 1));
        assertEquals(Optional.of(UiErrorCode.SESSION_EXPIRED),
                assertInstanceOf(ActionResult.class, wire.readOne()).error());
        assertEquals(1, charges.get());
    }

    @Test
    void coalescesWholeFieldsAndUsesSnapshotsToRecover() {
        var wire = new Wire();
        wire.startFromClient();
        UUID id = wire.open();
        assertTrue(wire.server.update(id, balance(85)));
        assertTrue(wire.server.update(id, balance(65)));
        assertFalse(wire.server.update(id, Map.of("balance", new UiValue.Text("invalid"))));
        assertTrue(wire.toClient.isEmpty());
        wire.server.tick();
        var patch = assertInstanceOf(DataUpdate.class, wire.readOne());
        assertEquals(0, patch.baseRevision());
        assertEquals(2, patch.revision());
        assertEquals(balance(65), patch.updates());
        wire.client.send(new StateRequest(id));
        var snapshot = assertInstanceOf(StateSnapshot.class, wire.readOne());
        assertEquals(2, snapshot.revision());
        assertEquals(balance(65), snapshot.data());
        wire.server.closePages(EXCHANGE);
        assertEquals(id, assertInstanceOf(PageClose.class, wire.readOne()).instanceId());
        assertFalse(wire.server.update(id, balance(100)));
    }

    @Test
    void receiptCapacityNeverEvictsAcceptedTransactions() throws Exception {
        var wire = new Wire(new UiProtocolLimits(2, 0, 1, 16, 4, 4, 4, 0));
        wire.startFromClient();
        UUID page = wire.open();
        UUID first = UUID.randomUUID();
        var accepted = new AtomicInteger();
        try (var registration = wire.server.registerAction(BUY, ignored -> true, context -> {
            accepted.incrementAndGet();
            return UiServerConnection.ActionOutcome.success(Map.of());
        })) {
            wire.client.send(action(page, first, 0, 1));
            assertEquals(UiActionState.SUCCEEDED, assertInstanceOf(ActionResult.class, wire.readOne()).state());
            wire.client.send(action(page, UUID.randomUUID(), 0, 1));
            assertEquals(Optional.of(UiErrorCode.RESOURCE_LIMIT_EXCEEDED),
                    assertInstanceOf(ActionResult.class, wire.readOne()).error());
            wire.client.send(action(page, first, 0, 1));
            assertEquals(UiActionState.SUCCEEDED, assertInstanceOf(ActionResult.class, wire.readOne()).state());
            assertEquals(1, accepted.get());
        }
    }

    @Test
    void inputBudgetAppliesBeforeDecodingAndRecoversAfterWindow() {
        var wire = new Wire(new UiProtocolLimits(2, 0, 1, 2, 4, 4, 4, 0));
        for (int frame = 0; frame < 3; frame++) {
            wire.server.receive(new byte[0]);
        }
        assertEquals(2, wire.server.snapshot().invalidFrames());
        assertEquals(1, wire.server.snapshot().droppedFrames());
        wire.advance(1);
        wire.startFromClient();
    }

    @Test
    void reentrantActionCannotStartAnotherBusinessHandler() throws Exception {
        var wire = new Wire();
        wire.startFromClient();
        UUID page = wire.open();
        UUID outer = UUID.randomUUID();
        UUID nested = UUID.randomUUID();
        var accepted = new AtomicInteger();
        try (var registration = wire.server.registerAction(BUY, ignored -> true, context -> {
            accepted.incrementAndGet();
            wire.client.send(action(page, outer, 0, 1));
            wire.client.send(action(page, nested, 0, 1));
            return UiServerConnection.ActionOutcome.success(Map.of());
        })) {
            wire.client.send(action(page, outer, 0, 1));
            var busy = assertInstanceOf(ActionResult.class, wire.readOne());
            assertEquals(nested, busy.requestId());
            assertEquals(Optional.of(UiErrorCode.ACTION_BUSY), busy.error());
            assertEquals(UiActionState.SUCCEEDED, assertInstanceOf(ActionResult.class, wire.readOne()).state());
            assertTrue(wire.toClient.isEmpty());
            assertEquals(1, accepted.get());
        }
    }

    @Test
    void authorizationMustStillOwnAValidSessionAfterPermissionCallback() throws Exception {
        var wire = new Wire();
        wire.startFromClient();
        UUID page = wire.open();
        var accepted = new AtomicInteger();
        try (var registration = wire.server.registerAction(BUY, ignored -> {
            wire.time.addAndGet(Duration.ofMinutes(30).toNanos());
            return true;
        }, context -> {
            accepted.incrementAndGet();
            return UiServerConnection.ActionOutcome.success(Map.of());
        })) {
            wire.client.send(action(page, UUID.randomUUID(), 0, 1));
            assertEquals(Optional.of(UiErrorCode.SESSION_EXPIRED),
                    assertInstanceOf(ActionResult.class, wire.readOne()).error());
            wire.server.tick();
            assertEquals(page, assertInstanceOf(PageClose.class, wire.readOne()).instanceId());
            assertEquals(0, accepted.get());
        }
    }

    @Test
    void scopedActionsCanReuseAnIdAndRegistrationCapacityIsReleased() throws Exception {
        var limits = new UiProtocolLimits(2, 0, 8, 16, 8, 4, 4, 0, 2);
        var wire = new Wire(limits);
        wire.startFromClient();
        var otherId = UiId.parse("example:other");
        var otherPage = new UiPageDefinition(otherId, PAGE.schemaVersion(), PAGE.type(), PAGE.root(),
                PAGE.dataSchema(), PAGE.actions(), PAGE.options());
        UUID firstPage = wire.open();
        UUID secondPage = wire.open(otherPage);
        var called = new ArrayList<UiId>();
        try (var first = wire.server.registerAction(EXCHANGE, BUY, ignored -> true, context -> {
            called.add(context.page().id());
            return UiServerConnection.ActionOutcome.success(Map.of());
        })) {
            try (var second = wire.server.registerAction(otherId, BUY, ignored -> true, context -> {
                called.add(context.page().id());
                return UiServerConnection.ActionOutcome.success(Map.of());
            })) {
                assertThrows(IllegalStateException.class, () -> wire.server.registerAction(BUY, ignored -> true,
                        context -> UiServerConnection.ActionOutcome.success(Map.of())));
                wire.client.send(action(firstPage, UUID.randomUUID(), 0, 1));
                assertEquals(UiActionState.SUCCEEDED, assertInstanceOf(ActionResult.class, wire.readOne()).state());
                wire.client.send(action(secondPage, UUID.randomUUID(), 0, 1));
                assertEquals(UiActionState.SUCCEEDED, assertInstanceOf(ActionResult.class, wire.readOne()).state());
            }
            try (var replacement = wire.server.registerAction(otherId, BUY, ignored -> false,
                    context -> UiServerConnection.ActionOutcome.success(Map.of()))) {
                wire.client.send(action(secondPage, UUID.randomUUID(), 0, 1));
                assertEquals(Optional.of(UiErrorCode.PERMISSION_DENIED),
                        assertInstanceOf(ActionResult.class, wire.readOne()).error());
            }
        }
        assertEquals(List.of(EXCHANGE, otherId), called);
    }

    @Test
    void confirmationOnlyExecutesForTheExactChallengeAndCurrentPreview() throws Exception {
        var wire = new Wire();
        wire.startFromClient();
        UUID page = wire.open(confirmedPage());
        var price = new AtomicInteger(5);
        var executed = new AtomicInteger();
        try (var registration = wire.server.registerAction(EXCHANGE, BUY, ignored -> true,
                context -> new UiActionPrompt("Diamond", 1, "Costs " + price.get()), context -> {
                    executed.incrementAndGet();
                    return UiServerConnection.ActionOutcome.success(Map.of());
                })) {
            UUID first = UUID.randomUUID();
            wire.client.send(action(page, first, 0, 1));
            var challenge = assertInstanceOf(ActionChallenge.class, wire.readOne());
            assertEquals(0, executed.get());
            wire.client.send(new ActionConfirm(page, first, UUID.randomUUID(), true));
            assertTrue(wire.toClient.isEmpty());
            assertEquals(0, executed.get());
            price.set(6);
            wire.client.send(new ActionConfirm(page, first, challenge.token(), true));
            assertEquals(Optional.of(UiErrorCode.CONFIRMATION_EXPIRED),
                    assertInstanceOf(ActionResult.class, wire.readOne()).error());
            assertEquals(0, executed.get());

            UUID second = UUID.randomUUID();
            wire.client.send(action(page, second, 0, 1));
            var current = assertInstanceOf(ActionChallenge.class, wire.readOne());
            wire.client.send(new ActionConfirm(page, second, current.token(), true));
            var result = assertInstanceOf(ActionResult.class, wire.readOne());
            assertEquals(UiActionState.SUCCEEDED, result.state());
            wire.client.send(new ActionConfirm(page, second, current.token(), true));
            assertTrue(wire.toClient.isEmpty());
            wire.client.send(action(page, second, 0, 1));
            assertEquals(result, wire.readOne());
            assertEquals(1, executed.get());
        }
    }

    @Test
    void cancelledOrExpiredChallengesNeverInvokeBusinessHandler() throws Exception {
        var wire = new Wire();
        wire.startFromClient();
        UUID page = wire.open(confirmedPage());
        try (var registration = wire.server.registerAction(EXCHANGE, BUY, ignored -> true,
                context -> new UiActionPrompt("Diamond", 1, "Costs 5"), context -> {
                    fail("Cancelled or expired confirmation reached the business handler");
                    return UiServerConnection.ActionOutcome.success(Map.of());
                })) {
            UUID cancel = UUID.randomUUID();
            wire.client.send(action(page, cancel, 0, 1));
            var first = assertInstanceOf(ActionChallenge.class, wire.readOne());
            wire.client.send(new ActionConfirm(page, cancel, first.token(), false));
            assertEquals(UiActionState.CANCELLED, assertInstanceOf(ActionResult.class, wire.readOne()).state());
            wire.client.send(new ActionConfirm(page, cancel, first.token(), true));
            assertTrue(wire.toClient.isEmpty());

            UUID expire = UUID.randomUUID();
            wire.client.send(action(page, expire, 0, 1));
            var second = assertInstanceOf(ActionChallenge.class, wire.readOne());
            wire.advance(31);
            assertEquals(UiActionState.TIMED_OUT, assertInstanceOf(ActionResult.class, wire.readOne()).state());
            wire.client.send(new ActionConfirm(page, expire, second.token(), true));
            assertTrue(wire.toClient.isEmpty());
        }
    }

    @Test
    void interactionOpensOnlyTheBoundTargetOnceBeforeExpiry() {
        var wire = new Wire();
        wire.startFromClient();
        var dimension = UiId.parse("minecraft:overworld");
        var wanted = new UiInteractionTarget.Block(dimension, 8, 63, 12);
        var different = new UiInteractionTarget.Block(dimension, 9, 63, 12);
        var proof = new UiInteractionProof(1, UUID.randomUUID(), wanted);
        wire.client.send(new InteractionRequest(proof));
        assertEquals(Optional.of(UiErrorCode.INVALID_INTERACTION),
                wire.server.openForInteraction(PAGE, balance(100), ignored -> {}, () -> true, different)
                        .toCompletableFuture().join().error());
        var opened = wire.server.openForInteraction(PAGE, balance(100), ignored -> {}, () -> true, wanted)
                .toCompletableFuture();
        var request = assertInstanceOf(PageOpen.class, wire.readOne());
        assertEquals(Optional.of(proof), request.interaction());
        wire.client.send(new PageOpened(request.instanceId(), UiOpenResult.Status.DISPLAYED, Optional.empty()));
        assertEquals(UiOpenResult.Status.DISPLAYED, opened.join().status());
        wire.client.send(new InteractionRequest(proof));
        assertEquals(Optional.of(UiErrorCode.INVALID_INTERACTION),
                wire.server.openForInteraction(PAGE, balance(100), ignored -> {}, () -> true, wanted)
                        .toCompletableFuture().join().error());
        wire.client.send(new InteractionRequest(new UiInteractionProof(2, UUID.randomUUID(), wanted)));
        wire.advance(4);
        assertEquals(Optional.of(UiErrorCode.INVALID_INTERACTION),
                wire.server.openForInteraction(PAGE, balance(100), ignored -> {}, () -> true, wanted)
                        .toCompletableFuture().join().error());
    }

    @Test
    void suspendedPagesBlockActionsAndResumeFromCurrentAuthority() {
        var wire = new Wire();
        wire.startFromClient();
        var allowed = new AtomicBoolean(true);
        var opened = wire.server.open(PAGE, balance(100), ignored -> {}, allowed::get);
        UUID page = assertInstanceOf(PageOpen.class, wire.readOne()).instanceId();
        wire.client.send(new PageOpened(page, UiOpenResult.Status.DISPLAYED, Optional.empty()));
        assertEquals(UiOpenResult.Status.DISPLAYED, opened.toCompletableFuture().join().status());
        wire.client.send(new PageVisibility(page, true));
        assertTrue(wire.server.update(page, balance(75)));
        wire.server.tick();
        assertTrue(wire.toClient.isEmpty());
        wire.client.send(action(page, UUID.randomUUID(), 0, 1));
        assertEquals(Optional.of(UiErrorCode.SESSION_EXPIRED), assertInstanceOf(ActionResult.class, wire.readOne()).error());
        wire.client.send(new PageVisibility(page, false));
        var resumed = assertInstanceOf(PageResumed.class, wire.readOne());
        assertEquals(1, resumed.revision());
        assertEquals(balance(75), resumed.data());

        wire.client.send(new PageVisibility(page, true));
        allowed.set(false);
        wire.client.send(new PageVisibility(page, false));
        assertEquals(Optional.of(UiErrorCode.SESSION_EXPIRED),
                assertInstanceOf(PageResumed.class, wire.readOne()).error());
        assertEquals(page, assertInstanceOf(PageClose.class, wire.readOne()).instanceId());
    }

    private static UiPageDefinition confirmedPage() {
        return new UiPageDefinition(EXCHANGE, PAGE.schemaVersion(), PAGE.type(), PAGE.root(), PAGE.dataSchema(),
                Map.of(BUY, new UiActionDefinition(BUY, UiActionPolicy.CONFIRMED, PAGE.actions().get(BUY).payloadSchema())),
                PAGE.options());
    }

    private static Map<String, UiValue> balance(int value) {
        return Map.of("balance", new UiValue.Int(value));
    }

    private static ActionRequest action(UUID id, UUID request, long revision, int count) {
        return new ActionRequest(id, request, BUY, revision, Map.of("count", new UiValue.Int(count)));
    }

    private static final class Wire {
        final UUID player = UUID.randomUUID();
        final AtomicLong time = new AtomicLong();
        final List<byte[]> toClient = new ArrayList<>();
        final UiServerConnection server;
        UiProtocolChannel client;

        Wire() {
            this(UiProtocolLimits.DEFAULT);
        }

        Wire(UiProtocolLimits limits) {
            server = new UiServerConnection(player, toClient::add, CAPABILITIES, time::get, limits);
            client = new UiProtocolChannel(UUID.randomUUID(), server::receive);
        }

        void startFromClient() {
            client.send(new Hello(CAPABILITIES));
            assertInstanceOf(Capabilities.class, readOne());
            assertTrue(server.available());
        }

        UUID open() {
            return open(PAGE);
        }

        UUID open(UiPageDefinition definition) {
            var result = server.open(definition, balance(100)).toCompletableFuture();
            UUID instance = assertInstanceOf(PageOpen.class, readOne()).instanceId();
            client.send(new PageOpened(instance, UiOpenResult.Status.DISPLAYED, Optional.empty()));
            assertEquals(UiOpenResult.Status.DISPLAYED, result.join().status());
            return instance;
        }

        Payload readOne() {
            while (!toClient.isEmpty()) {
                var message = client.receive(toClient.remove(0), time.get());
                if (message.isPresent()) {
                    return message.orElseThrow().payload();
                }
            }
            throw new AssertionError("No message from server");
        }

        void advance(long seconds) {
            time.addAndGet(Duration.ofSeconds(seconds).toNanos());
            server.tick();
        }
    }
}
