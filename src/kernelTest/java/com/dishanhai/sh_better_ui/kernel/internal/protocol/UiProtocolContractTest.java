// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.protocol;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
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
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolCodec;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolFraming;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.*;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.Error;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolTrace;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class UiProtocolContractTest {
    private static final UUID SESSION = UUID.randomUUID();
    private static final UUID INSTANCE = UUID.randomUUID();
    private static final UUID REQUEST = UUID.randomUUID();
    private static final UiId EXCHANGE = UiId.parse("example:exchange");
    private static final UiId TEXT = UiId.parse("sh_better_ui:text");
    private static final Map<String, UiValue> DATA = Map.of("text", new UiValue.Text("兑换"));
    private static final UiProtocolCapabilities CAPS = UiProtocolCapabilities.standard(
            Map.of(TEXT, UiProtocol.VERSION), Set.of(UiPageType.SCREEN, UiPageType.HUD, UiPageType.NOTIFICATION));

    @Test
    void roundTripsEachTypedPayload() {
        UUID token = UUID.randomUUID();
        var interaction = new UiInteractionProof(1, token,
                new UiInteractionTarget.Block(UiId.parse("minecraft:overworld"), 1, 65, -2));
        var messages = List.of(
                new Hello(CAPS),
                new Capabilities(CAPS),
                new PageOpen(INSTANCE, page(UiPageType.SCREEN), DATA),
                new PageOpen(INSTANCE, page(UiPageType.HUD), DATA),
                new PageOpen(INSTANCE, page(UiPageType.NOTIFICATION), DATA),
                new PageOpened(INSTANCE, UiOpenResult.Status.DISPLAYED, Optional.empty()),
                new PageOpened(INSTANCE, UiOpenResult.Status.REJECTED, Optional.of(UiErrorCode.COMPONENT_MISSING)),
                new PageClose(INSTANCE),
                new HudUnmount(INSTANCE),
                new DataUpdate(INSTANCE, 3, 6, DATA),
                new StateRequest(INSTANCE),
                new StateSnapshot(INSTANCE, 6, DATA),
                new ActionRequest(INSTANCE, REQUEST, EXCHANGE, 6, Map.of()),
                new ActionResult(INSTANCE, REQUEST, UiActionState.SUCCEEDED, Optional.empty()),
                new ActionResult(INSTANCE, REQUEST, UiActionState.FAILED, Optional.of(UiErrorCode.PERMISSION_DENIED)),
                new ActionChallenge(INSTANCE, REQUEST, token, new UiActionPrompt("奖励", 1, "消耗积分")),
                new ActionConfirm(INSTANCE, REQUEST, token, true),
                new InteractionRequest(interaction),
                new PageOpen(INSTANCE, page(UiPageType.SCREEN), DATA, Optional.of(interaction), false),
                new PageOpen(INSTANCE, page(UiPageType.MODAL), DATA, Optional.empty(), true),
                new PageVisibility(INSTANCE, true),
                new PageResumed(INSTANCE, 6, DATA, Optional.empty()),
                new PageResumed(INSTANCE, 0, Map.of(), Optional.of(UiErrorCode.SESSION_EXPIRED)),
                new Error(UiErrorCode.SCHEMA_INVALID),
                new Terminate()
        );
        long sequence = 1;
        for (Payload payload : messages) {
            var envelope = new UiProtocolMessage(UiProtocol.VERSION, SESSION, sequence++, payload);
            byte[] encoded = UiProtocolCodec.encode(envelope);
            assertEquals(envelope, UiProtocolCodec.decode(encoded));
            assertArrayEquals(encoded, UiProtocolCodec.encode(UiProtocolCodec.decode(encoded)));
        }
    }

    @Test
    void codecRejectsTruncationTrailingDataWrongVersionAndOversize() {
        byte[] encoded = UiProtocolCodec.encode(new UiProtocolMessage(UiProtocol.VERSION, SESSION, 1, new Hello(CAPS)));
        for (int length = 0; length < encoded.length; length++) {
            byte[] truncated = Arrays.copyOf(encoded, length);
            assertThrows(IllegalArgumentException.class, () -> UiProtocolCodec.decode(truncated));
        }
        assertThrows(IllegalArgumentException.class, () -> UiProtocolCodec.decode(Arrays.copyOf(encoded, encoded.length + 1)));
        assertThrows(IllegalArgumentException.class, () -> UiProtocolCodec.decode(new byte[UiProtocol.MAX_MESSAGE_BYTES + 1]));
        var wrongVersion = new UiProtocolMessage(new UiVersion(2, 0, 0), SESSION, 1, new Hello(CAPS));
        assertThrows(IllegalArgumentException.class, () -> UiProtocolCodec.encode(wrongVersion));
    }

    @Test
    void negotiatesCommonSchemaAndSmallerLimit() {
        var older = new UiProtocolCapabilities(Set.of(UiProtocol.VERSION), Set.of(UiPageDefinition.SCHEMA_VERSION),
                Map.of(TEXT, new UiVersion(1, 2, 0)), Set.of(UiPageType.SCREEN), 4096);
        assertEquals(4096, CAPS.negotiate(older).orElseThrow().maxMessageBytes());
        assertTrue(older.supports(TEXT, new UiVersion(1, 1, 0)));
        assertFalse(older.supports(TEXT, new UiVersion(2, 0, 0)));
        var incompatible = new UiProtocolCapabilities(Set.of(new UiVersion(2, 0, 0)), older.schemaVersions(),
                Map.of(), older.pageTypes(), older.maxMessageBytes());
        assertTrue(CAPS.negotiate(incompatible).isEmpty());
    }

    @Test
    void framesAreBoundedAndReassembledWithMissingDuplicateAndReorderedFragments() {
        byte[] source = new byte[UiProtocol.MAX_MESSAGE_BYTES];
        for (int index = 0; index < source.length; index++) {
            source[index] = (byte) index;
        }
        List<byte[]> frames = UiProtocolFraming.split(source, UUID.randomUUID());
        assertTrue(frames.stream().allMatch(frame -> frame.length <= UiProtocol.MAX_FRAME_BYTES));
        var reassembler = new UiProtocolFraming.Reassembler();
        int last = frames.size() - 1;
        assertTrue(reassembler.accept(frames.get(last), 0).isEmpty());
        assertTrue(reassembler.accept(frames.get(last), 1).isEmpty());
        for (int index = last - 1; index > 0; index--) {
            assertTrue(reassembler.accept(frames.get(index), 2).isEmpty());
        }
        assertArrayEquals(source, reassembler.accept(frames.get(0), 3).orElseThrow());
        for (byte[] replay : frames) {
            assertTrue(reassembler.accept(replay, 4).isEmpty());
        }
        assertThrows(IllegalArgumentException.class,
                () -> reassembler.accept(Arrays.copyOf(frames.get(0), frames.get(0).length - 1), 5));

        var abandoned = UiProtocolFraming.split(source, UUID.randomUUID());
        assertTrue(reassembler.accept(abandoned.get(0), 6).isEmpty());
        long expired = Duration.ofSeconds(6).toNanos();
        reassembler.expire(expired);
        for (byte[] late : abandoned) {
            assertTrue(reassembler.accept(late, expired).isEmpty());
        }
    }

    @Test
    void channelBindsIdentityAndRejectsDuplicateAndOlderSequences() {
        var frames = new ArrayList<byte[]>();
        var sender = new UiProtocolChannel(SESSION, frames::add);
        sender.send(new Hello(CAPS));
        UiProtocolMessage initial = UiProtocolChannel.acceptInitial(new UiProtocolFraming.Reassembler(),
                frames.remove(0), 0).orElseThrow();
        var receiver = new UiProtocolChannel(initial, ignored -> {});
        sender.send(new StateRequest(INSTANCE));
        sender.send(new Terminate());
        assertInstanceOf(Terminate.class, receiver.receive(frames.get(1), 1).orElseThrow().payload());
        assertTrue(receiver.receive(frames.get(0), 2).isEmpty());
        assertTrue(receiver.receive(frames.get(1), 3).isEmpty());
        var otherFrames = new ArrayList<byte[]>();
        var other = new UiProtocolChannel(UUID.randomUUID(), otherFrames::add);
        other.send(new Terminate());
        assertTrue(receiver.receive(otherFrames.get(0), 4).isEmpty());
    }

    @Test
    void tracingKeepsOnlyBoundedMetadataAndCanBeCleared() {
        var frames = new ArrayList<byte[]>();
        var sender = new UiProtocolChannel(SESSION, frames::add);
        sender.send(new Hello(CAPS));
        var initial = UiProtocolChannel.acceptInitial(new UiProtocolFraming.Reassembler(),
                frames.remove(0), 0).orElseThrow();
        var receiver = new UiProtocolChannel(initial, ignored -> {});
        assertTrue(sender.traceSnapshot().isEmpty());
        sender.setTracing(true);
        receiver.setTracing(true);
        var request = new ActionRequest(INSTANCE, REQUEST, EXCHANGE, 0,
                Map.of("private", new UiValue.Text("private business payload")));
        for (int index = 0; index < 70; index++) {
            sender.send(request);
            assertTrue(receiver.receive(frames.remove(0), index + 1).isPresent());
        }
        List<UiProtocolTrace> outgoing = sender.traceSnapshot();
        assertEquals(64, outgoing.size());
        assertEquals(8, outgoing.get(0).sequence());
        UiProtocolTrace incoming = receiver.traceSnapshot().get(63);
        assertEquals(UiProtocolTrace.Direction.INBOUND, incoming.direction());
        assertEquals(UiProtocolMessage.Type.ACTION_REQUEST, incoming.type());
        assertEquals(Optional.of(INSTANCE), incoming.instanceId());
        assertEquals(Optional.of(REQUEST), incoming.requestId());
        assertFalse(incoming.toString().contains("private business payload"));
        receiver.setTracing(false);
        assertTrue(receiver.traceSnapshot().isEmpty());
    }

    private static UiPageDefinition page(UiPageType type) {
        return UiPageDefinition.builder(EXCHANGE).type(type)
                .root("root", "text", text -> text.bind("text", "text"))
                .field("text", UiValueSchema.text(32))
                .build();
    }
}
