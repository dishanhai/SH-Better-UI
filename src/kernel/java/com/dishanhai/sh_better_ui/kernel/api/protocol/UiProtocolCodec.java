// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.protocol;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageCodec;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.*;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.Error;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/** Bounded binary message format shared by every transport adapter. */
public final class UiProtocolCodec {
    private static final int MAGIC = 0x53485550; // SHUP
    private static final int MAX_ASCII_BYTES = 256;

    private UiProtocolCodec() {}

    public static byte[] encode(UiProtocolMessage message) {
        if (!UiProtocol.VERSION.equals(message.version())) {
            throw new IllegalArgumentException("Unsupported page protocol version");
        }
        var bytes = new BoundedOutput();
        try (var output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            writeVersion(output, message.version());
            writeUuid(output, message.sessionId());
            output.writeLong(message.sequence());
            writePayload(output, message.payload());
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid page protocol message", exception);
        }
    }

    public static UiProtocolMessage decode(byte[] encoded) {
        return decode(encoded, null, 0).orElseThrow();
    }

    static Optional<UiProtocolMessage> decode(byte[] encoded, UUID expectedConnection, long lastSequence) {
        if (encoded.length > UiProtocol.MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("Page protocol message exceeds maximum size");
        }
        try (var input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) {
                throw invalid();
            }
            UiVersion version = readVersion(input);
            if (!version.equals(UiProtocol.VERSION)) {
                throw new IllegalArgumentException("Unsupported page protocol version");
            }
            UUID sessionId = readUuid(input);
            long sequence = input.readLong();
            if (sequence < 1) {
                throw invalid();
            }
            if (expectedConnection != null && (!expectedConnection.equals(sessionId) || sequence <= lastSequence)) {
                return Optional.empty();
            }
            Payload payload = readPayload(input);
            if (input.available() != 0) {
                throw invalid();
            }
            return Optional.of(new UiProtocolMessage(version, sessionId, sequence, payload));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid page protocol message", exception);
        }
    }

    private static void writePayload(DataOutputStream output, Payload payload) throws IOException {
        output.writeByte(payload.type().wireTag);
        if (payload instanceof Hello hello) {
            writeCapabilities(output, hello.capabilities());
        } else if (payload instanceof Capabilities reply) {
            writeCapabilities(output, reply.capabilities());
        } else if (payload instanceof PageOpen page) {
            writeUuid(output, page.instanceId());
            byte[] definition = UiPageCodec.encode(page.page());
            output.writeInt(definition.length);
            output.write(definition);
            UiPageCodec.writeValues(output, page.data());
            if (page.type() == Type.AUTHORIZED_PAGE_OPEN) {
                output.writeBoolean(page.highPriority());
                output.writeBoolean(page.interaction().isPresent());
                if (page.interaction().isPresent()) writeInteraction(output, page.interaction().orElseThrow());
            }
        } else if (payload instanceof PageOpened result) {
            writeUuid(output, result.instanceId());
            writeSymbol(output, result.status().name());
            writeError(output, result.error());
        } else if (payload instanceof PageClose close) {
            writeUuid(output, close.instanceId());
        } else if (payload instanceof DataUpdate update) {
            writeUuid(output, update.instanceId());
            output.writeLong(update.baseRevision());
            output.writeLong(update.revision());
            UiPageCodec.writeValues(output, update.updates());
        } else if (payload instanceof ActionRequest request) {
            writeUuid(output, request.instanceId());
            writeUuid(output, request.requestId());
            writeSymbol(output, request.actionId().toString());
            output.writeLong(request.revision());
            UiPageCodec.writeValues(output, request.data());
        } else if (payload instanceof ActionResult result) {
            writeUuid(output, result.instanceId());
            writeUuid(output, result.requestId());
            writeSymbol(output, result.state().name());
            writeError(output, result.error());
        } else if (payload instanceof HudUnmount unmount) {
            writeUuid(output, unmount.instanceId());
        } else if (payload instanceof Error error) {
            writeSymbol(output, error.code().name());
        } else if (payload instanceof StateRequest request) {
            writeUuid(output, request.instanceId());
        } else if (payload instanceof StateSnapshot snapshot) {
            writeUuid(output, snapshot.instanceId());
            output.writeLong(snapshot.revision());
            UiPageCodec.writeValues(output, snapshot.data());
        } else if (payload instanceof ActionChallenge challenge) {
            writeUuid(output, challenge.instanceId());
            writeUuid(output, challenge.requestId());
            writeUuid(output, challenge.token());
            writePreviewText(output, challenge.prompt().subject());
            output.writeLong(challenge.prompt().quantity());
            writePreviewText(output, challenge.prompt().consequence());
        } else if (payload instanceof ActionConfirm confirmation) {
            writeUuid(output, confirmation.instanceId());
            writeUuid(output, confirmation.requestId());
            writeUuid(output, confirmation.token());
            output.writeBoolean(confirmation.accepted());
        } else if (payload instanceof InteractionRequest interaction) {
            writeInteraction(output, interaction.proof());
        } else if (payload instanceof InteractionRejected rejected) {
            output.writeLong(rejected.attempt());
            writeUuid(output, rejected.token());
            writeSymbol(output, rejected.error().name());
        } else if (payload instanceof PageVisibility visibility) {
            writeUuid(output, visibility.instanceId());
            output.writeBoolean(visibility.suspended());
        } else if (payload instanceof PageResumed resumed) {
            writeUuid(output, resumed.instanceId());
            output.writeLong(resumed.revision());
            writeError(output, resumed.error());
            UiPageCodec.writeValues(output, resumed.data());
        } else if (!(payload instanceof Terminate)) {
            throw invalid();
        }
    }

    private static Payload readPayload(DataInputStream input) throws IOException {
        Type type = Type.fromWireTag(input.readUnsignedByte());
        return switch (type) {
            case HELLO -> new Hello(readCapabilities(input));
            case CAPABILITIES -> new Capabilities(readCapabilities(input));
            case PAGE_OPEN_REQUEST, HUD_MOUNT, NOTIFICATION_PUSH, AUTHORIZED_PAGE_OPEN -> readOpen(input, type);
            case PAGE_OPEN_RESULT -> new PageOpened(readUuid(input), UiOpenResult.Status.valueOf(readSymbol(input)),
                    readError(input));
            case PAGE_CLOSE -> new PageClose(readUuid(input));
            case DATA_UPDATE -> new DataUpdate(readUuid(input), input.readLong(), input.readLong(),
                    UiPageCodec.readValues(input));
            case ACTION_REQUEST -> new ActionRequest(readUuid(input), readUuid(input), UiId.parse(readSymbol(input)),
                    input.readLong(), UiPageCodec.readValues(input));
            case ACTION_RESULT -> new ActionResult(readUuid(input), readUuid(input), UiActionState.valueOf(readSymbol(input)),
                    readError(input));
            case HUD_UNMOUNT -> new HudUnmount(readUuid(input));
            case SESSION_TERMINATE -> new Terminate();
            case ERROR -> new Error(UiErrorCode.valueOf(readSymbol(input)));
            case STATE_REQUEST -> new StateRequest(readUuid(input));
            case STATE_SNAPSHOT -> new StateSnapshot(readUuid(input), input.readLong(), UiPageCodec.readValues(input));
            case ACTION_CHALLENGE -> new ActionChallenge(readUuid(input), readUuid(input), readUuid(input),
                    new UiActionPrompt(readPreviewText(input), input.readLong(), readPreviewText(input)));
            case ACTION_CONFIRM -> new ActionConfirm(readUuid(input), readUuid(input), readUuid(input), readFlag(input));
            case INTERACTION_REQUEST -> new InteractionRequest(readInteraction(input));
            case INTERACTION_REJECTED -> new InteractionRejected(input.readLong(), readUuid(input),
                    UiErrorCode.valueOf(readSymbol(input)));
            case PAGE_VISIBILITY -> new PageVisibility(readUuid(input), readFlag(input));
            case PAGE_RESUMED -> readResumed(input);
        };
    }

    private static PageOpen readOpen(DataInputStream input, Type type) throws IOException {
        UUID instanceId = readUuid(input);
        int size = input.readInt();
        if (size <= 0 || size > input.available()) {
            throw invalid();
        }
        var page = UiPageCodec.decode(input.readNBytes(size));
        var data = UiPageCodec.readValues(input);
        var open = type == Type.AUTHORIZED_PAGE_OPEN
                ? readAuthorizedOpen(input, instanceId, page, data) : new PageOpen(instanceId, page, data);
        if (type != open.type()) {
            throw invalid();
        }
        return open;
    }

    private static PageOpen readAuthorizedOpen(DataInputStream input, UUID instanceId,
                                               com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition page,
                                               Map<String, com.dishanhai.sh_better_ui.kernel.api.page.UiValue> data)
            throws IOException {
        boolean highPriority = readFlag(input);
        Optional<UiInteractionProof> interaction = readFlag(input)
                ? Optional.of(readInteraction(input)) : Optional.empty();
        return new PageOpen(instanceId, page, data, interaction, highPriority);
    }

    private static PageResumed readResumed(DataInputStream input) throws IOException {
        UUID instance = readUuid(input);
        long revision = input.readLong();
        Optional<UiErrorCode> error = readError(input);
        return new PageResumed(instance, revision, UiPageCodec.readValues(input), error);
    }

    private static boolean readFlag(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> false;
            case 1 -> true;
            default -> throw invalid();
        };
    }

    private static void writePreviewText(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeShort(encoded.length);
        output.write(encoded);
    }

    private static String readPreviewText(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        if (length < 1 || length > 768 || length > input.available()) throw invalid();
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(input.readNBytes(length))).toString();
    }

    private static void writeInteraction(DataOutputStream output, UiInteractionProof proof) throws IOException {
        output.writeLong(proof.attempt());
        writeUuid(output, proof.token());
        if (proof.target() instanceof UiInteractionTarget.Block block) {
            output.writeByte(1);
            writeSymbol(output, block.dimension().toString());
            output.writeInt(block.x());
            output.writeInt(block.y());
            output.writeInt(block.z());
        } else if (proof.target() instanceof UiInteractionTarget.Entity entity) {
            output.writeByte(2);
            writeSymbol(output, entity.dimension().toString());
            writeUuid(output, entity.entityId());
        } else if (proof.target() instanceof UiInteractionTarget.Page page) {
            output.writeByte(3);
            writeSymbol(output, page.pageId().toString());
        } else {
            throw invalid();
        }
    }

    private static UiInteractionProof readInteraction(DataInputStream input) throws IOException {
        long attempt = input.readLong();
        UUID token = readUuid(input);
        UiInteractionTarget target = switch (input.readUnsignedByte()) {
            case 1 -> new UiInteractionTarget.Block(UiId.parse(readSymbol(input)),
                    input.readInt(), input.readInt(), input.readInt());
            case 2 -> new UiInteractionTarget.Entity(UiId.parse(readSymbol(input)), readUuid(input));
            case 3 -> new UiInteractionTarget.Page(UiId.parse(readSymbol(input)));
            default -> throw invalid();
        };
        return new UiInteractionProof(attempt, token, target);
    }

    private static void writeCapabilities(DataOutputStream output, UiProtocolCapabilities capabilities)
            throws IOException {
        writeVersions(output, capabilities.protocolVersions());
        writeVersions(output, capabilities.schemaVersions());
        output.writeByte(capabilities.capabilities().size());
        for (Map.Entry<UiId, UiVersion> entry : new TreeMap<>(capabilities.capabilities()).entrySet()) {
            writeSymbol(output, entry.getKey().toString());
            writeVersion(output, entry.getValue());
        }
        output.writeByte(capabilities.pageTypes().size());
        for (UiPageType type : new TreeSet<>(capabilities.pageTypes())) {
            writeSymbol(output, type.name());
        }
        output.writeInt(capabilities.maxMessageBytes());
    }

    private static UiProtocolCapabilities readCapabilities(DataInputStream input) throws IOException {
        Set<UiVersion> protocol = readVersions(input);
        Set<UiVersion> schema = readVersions(input);
        Map<UiId, UiVersion> capabilities = new HashMap<>();
        int count = input.readUnsignedByte();
        if (count > 128) {
            throw invalid();
        }
        for (int index = 0; index < count; index++) {
            UiId id = UiId.parse(readSymbol(input));
            if (capabilities.putIfAbsent(id, readVersion(input)) != null) {
                throw invalid();
            }
        }
        Set<UiPageType> pageTypes = new HashSet<>();
        count = input.readUnsignedByte();
        if (count > UiPageType.values().length) {
            throw invalid();
        }
        for (int index = 0; index < count; index++) {
            if (!pageTypes.add(UiPageType.valueOf(readSymbol(input)))) {
                throw invalid();
            }
        }
        return new UiProtocolCapabilities(protocol, schema, capabilities, pageTypes, input.readInt());
    }

    private static void writeVersions(DataOutputStream output, Set<UiVersion> versions) throws IOException {
        output.writeByte(versions.size());
        for (UiVersion version : new TreeSet<>(versions)) {
            writeVersion(output, version);
        }
    }

    private static Set<UiVersion> readVersions(DataInputStream input) throws IOException {
        int count = input.readUnsignedByte();
        if (count == 0 || count > 16) {
            throw invalid();
        }
        Set<UiVersion> versions = new HashSet<>();
        for (int index = 0; index < count; index++) {
            if (!versions.add(readVersion(input))) {
                throw invalid();
            }
        }
        return versions;
    }

    private static void writeVersion(DataOutputStream output, UiVersion version) throws IOException {
        output.writeInt(version.major());
        output.writeInt(version.minor());
        output.writeInt(version.patch());
    }

    private static UiVersion readVersion(DataInputStream input) throws IOException {
        return new UiVersion(input.readInt(), input.readInt(), input.readInt());
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeError(DataOutputStream output, Optional<UiErrorCode> value) throws IOException {
        output.writeByte(value.isPresent() ? 1 : 0);
        if (value.isPresent()) {
            writeSymbol(output, value.orElseThrow().name());
        }
    }

    private static Optional<UiErrorCode> readError(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> Optional.empty();
            case 1 -> Optional.of(UiErrorCode.valueOf(readSymbol(input)));
            default -> throw invalid();
        };
    }

    private static void writeSymbol(DataOutputStream output, String value) throws IOException {
        if (value.isEmpty() || value.length() > MAX_ASCII_BYTES
                || value.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw invalid();
        }
        output.writeShort(value.length());
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static String readSymbol(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        if (length == 0 || length > MAX_ASCII_BYTES || length > input.available()) {
            throw invalid();
        }
        byte[] bytes = input.readNBytes(length);
        for (byte value : bytes) {
            if (value < 33 || value > 126) {
                throw invalid();
            }
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static IOException invalid() {
        return new IOException("Malformed page protocol field");
    }

    private static final class BoundedOutput extends ByteArrayOutputStream {
        @Override
        public synchronized void write(int value) {
            requireCapacity(1);
            super.write(value);
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            requireCapacity(length);
            super.write(bytes, offset, length);
        }

        private void requireCapacity(int count) {
            if (count > UiProtocol.MAX_MESSAGE_BYTES - size()) {
                throw new IllegalArgumentException("Page protocol message exceeds maximum size");
            }
        }
    }
}
