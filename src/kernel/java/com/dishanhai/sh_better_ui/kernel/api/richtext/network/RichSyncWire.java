// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext.network;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/** Direction-specific wire codec. The envelope version is independent of negotiated feature flags. */
public final class RichSyncWire {
    public static final int VERSION = 1;
    public static final String CHANNEL_VERSION = Integer.toString(VERSION);
    public static final int MAX_CLIENT_BYTES = 1024;
    public static final int MAX_SERVER_BYTES = 28 * 1024;

    private RichSyncWire() {}

    public static byte[] encode(ClientMessage message) {
        return encode(output -> {
            if (message instanceof Hello hello) {
                output.writeByte(0);
                writeUuid(output, hello.nonce());
                output.writeByte(hello.minVersion());
                output.writeByte(hello.maxVersion());
                output.writeByte(hello.capabilities());
            } else if (message instanceof Subscribe subscribe) {
                writeWatch(output, 1, subscribe.session(), subscribe.watchId());
                writeId(output, subscribe.document());
            } else if (message instanceof Unsubscribe unsubscribe) {
                writeWatch(output, 2, unsubscribe.session(), unsubscribe.watchId());
            } else if (message instanceof Resync resync) {
                writeWatch(output, 3, resync.session(), resync.watchId());
            } else if (message instanceof Edit edit) {
                writeWatch(output, 4, edit.session(), edit.watchId());
                output.writeLong(edit.requestId());
                output.writeLong(edit.revision());
                writeId(output, edit.key());
                writeValue(output, edit.value());
            } else {
                throw new IllegalArgumentException("Unknown client message");
            }
        });
    }

    public static ClientMessage decodeClient(byte[] bytes) throws IOException {
        return decode(bytes, MAX_CLIENT_BYTES, input -> switch (input.readUnsignedByte()) {
            case 0 -> new Hello(readUuid(input), input.readUnsignedByte(), input.readUnsignedByte(),
                    input.readUnsignedByte());
            case 1 -> new Subscribe(readUuid(input), input.readLong(), readId(input));
            case 2 -> new Unsubscribe(readUuid(input), input.readLong());
            case 3 -> new Resync(readUuid(input), input.readLong());
            case 4 -> new Edit(readUuid(input), input.readLong(), input.readLong(), input.readLong(),
                    readId(input), readValue(input));
            default -> throw new IOException("Unknown client message tag");
        });
    }

    public static byte[] encode(ServerMessage message) {
        return encode(output -> {
            if (message instanceof Welcome welcome) {
                output.writeByte(0);
                writeUuid(output, welcome.nonce());
                writeUuid(output, welcome.session());
                output.writeByte(welcome.version());
                output.writeByte(welcome.capabilities());
            } else if (message instanceof Unsupported unsupported) {
                output.writeByte(1);
                writeUuid(output, unsupported.nonce());
            } else if (message instanceof Snapshot snapshot) {
                writeWatch(output, 2, snapshot.session(), snapshot.watchId());
                output.writeLong(snapshot.revision());
                output.writeBoolean(snapshot.writable());
                writeValues(output, snapshot.values());
            } else if (message instanceof Patch patch) {
                writeWatch(output, 3, patch.session(), patch.watchId());
                output.writeLong(patch.baseRevision());
                output.writeLong(patch.revision());
                writeValues(output, patch.changed());
                output.writeByte(patch.removed().size());
                for (UiId key : patch.removed()) writeId(output, key);
            } else if (message instanceof Result result) {
                writeWatch(output, 4, result.session(), result.watchId());
                output.writeLong(result.requestId());
                output.writeLong(result.revision());
                writeOutcome(output, result.outcome());
            } else if (message instanceof Failure failure) {
                writeWatch(output, 5, failure.session(), failure.watchId());
                writeOutcome(output, failure.outcome());
            } else {
                throw new IllegalArgumentException("Unknown server message");
            }
        });
    }

    public static ServerMessage decodeServer(byte[] bytes) throws IOException {
        return decode(bytes, MAX_SERVER_BYTES, input -> switch (input.readUnsignedByte()) {
            case 0 -> new Welcome(readUuid(input), readUuid(input), input.readUnsignedByte(), input.readUnsignedByte());
            case 1 -> new Unsupported(readUuid(input));
            case 2 -> new Snapshot(readUuid(input), input.readLong(), input.readLong(), readFlag(input), readValues(input));
            case 3 -> readPatch(input);
            case 4 -> new Result(readUuid(input), input.readLong(), input.readLong(), input.readLong(), readOutcome(input));
            case 5 -> new Failure(readUuid(input), input.readLong(), readOutcome(input));
            default -> throw new IOException("Unknown server message tag");
        });
    }

    private static Patch readPatch(DataInputStream input) throws IOException {
        UUID session = readUuid(input);
        long watchId = input.readLong();
        long base = input.readLong();
        long revision = input.readLong();
        var changed = readValues(input);
        int count = readCount(input);
        var removed = new LinkedHashSet<UiId>(count);
        for (int index = 0; index < count; index++) {
            if (!removed.add(readId(input))) throw new IOException("Duplicate removed key");
        }
        return new Patch(session, watchId, base, revision, changed, removed);
    }

    private static void writeValues(DataOutputStream output, Map<UiId, Value> values) throws IOException {
        output.writeByte(values.size());
        for (var field : values.entrySet()) {
            writeId(output, field.getKey());
            writeValue(output, field.getValue());
        }
    }

    private static Map<UiId, Value> readValues(DataInputStream input) throws IOException {
        int count = readCount(input);
        var values = new LinkedHashMap<UiId, Value>(count);
        for (int index = 0; index < count; index++) {
            if (values.putIfAbsent(readId(input), readValue(input)) != null) throw new IOException("Duplicate field key");
        }
        return values;
    }

    private static int readCount(DataInputStream input) throws IOException {
        int count = input.readUnsignedByte();
        if (count > RichSyncProtocol.MAX_FIELDS) throw new IOException("Too many synchronized fields");
        return count;
    }

    private static void writeValue(DataOutputStream output, Value value) throws IOException {
        if (value instanceof Text text) {
            output.writeByte(0);
            writeString(output, text.text());
        } else if (value instanceof RichSyncProtocol.Number number) {
            output.writeByte(1);
            output.writeLong(number.number());
        } else {
            throw new IllegalArgumentException("Unknown value type");
        }
    }

    private static Value readValue(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> new Text(readString(input, RichSyncProtocol.MAX_TEXT_BYTES));
            case 1 -> new RichSyncProtocol.Number(input.readLong());
            default -> throw new IOException("Unknown value tag");
        };
    }

    private static boolean readFlag(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> false;
            case 1 -> true;
            default -> throw new IOException("Invalid boolean");
        };
    }

    private static Outcome readOutcome(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> Outcome.ACCEPTED;
            case 1 -> Outcome.DENIED;
            case 2 -> Outcome.STALE;
            case 3 -> Outcome.INVALID;
            case 4 -> Outcome.RATE_LIMITED;
            case 5 -> Outcome.FAILED;
            default -> throw new IOException("Unknown server outcome");
        };
    }

    private static void writeOutcome(DataOutputStream output, Outcome outcome) throws IOException {
        output.writeByte(switch (outcome) {
            case ACCEPTED -> 0;
            case DENIED -> 1;
            case STALE -> 2;
            case INVALID -> 3;
            case RATE_LIMITED -> 4;
            case FAILED -> 5;
            case TIMED_OUT -> throw new IllegalArgumentException("Timeout is local client state");
        });
    }

    private static void writeWatch(DataOutputStream output, int tag, UUID session, long watchId) throws IOException {
        output.writeByte(tag);
        writeUuid(output, session);
        output.writeLong(watchId);
    }

    private static void writeUuid(DataOutputStream output, UUID uuid) throws IOException {
        output.writeLong(uuid.getMostSignificantBits());
        output.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeId(DataOutputStream output, UiId id) throws IOException {
        writeString(output, id.toString());
    }

    private static UiId readId(DataInputStream input) throws IOException {
        return UiId.parse(readString(input, RichSyncProtocol.MAX_ID_BYTES));
    }

    private static void writeString(DataOutputStream output, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input, int limit) throws IOException {
        int length = input.readUnsignedShort();
        if (length > limit) throw new IOException("Text exceeds field limit");
        var bytes = new byte[length];
        input.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static byte[] encode(Encoder writer) {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(VERSION);
            writer.write(output);
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return bytes.toByteArray();
    }

    private static <T> T decode(byte[] bytes, int limit, Decoder<T> reader) throws IOException {
        if (bytes == null || bytes.length > limit) throw new IOException("Invalid payload size");
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readUnsignedByte() != VERSION) throw new IOException("Unsupported rich sync envelope");
            T message = reader.read(input);
            if (input.available() != 0) throw new IOException("Unexpected trailing payload bytes");
            return message;
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Invalid rich sync payload", malformed);
        }
    }

    @FunctionalInterface
    private interface Encoder { void write(DataOutputStream output) throws IOException; }

    @FunctionalInterface
    private interface Decoder<T> { T read(DataInputStream input) throws IOException; }
}
