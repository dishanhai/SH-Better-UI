// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.protocol;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Application framing for plugin channels that cap each individual payload. */
public final class UiProtocolFraming {
    private static final int MAGIC = 0x53485546; // SHUF
    private static final int HEADER_BYTES = 4 + 16 + 4 + 2;
    private static final int FRAGMENT_BYTES = UiProtocol.MAX_FRAME_BYTES - HEADER_BYTES;
    private static final int MAX_IN_FLIGHT = 4;
    private static final int MAX_TRANSFERS = 256;
    private static final long TRANSFER_TTL = Duration.ofSeconds(5).toNanos();

    private UiProtocolFraming() {}

    public static List<byte[]> split(byte[] encoded, UUID transferId) {
        Objects.requireNonNull(transferId, "transferId");
        requireMessageSize(encoded.length);
        var frames = new ArrayList<byte[]>(fragmentCount(encoded.length));
        for (int index = 0; index < fragmentCount(encoded.length); index++) {
            int offset = index * FRAGMENT_BYTES;
            int length = Math.min(FRAGMENT_BYTES, encoded.length - offset);
            byte[] frame = ByteBuffer.allocate(HEADER_BYTES + length).putInt(MAGIC)
                    .putLong(transferId.getMostSignificantBits()).putLong(transferId.getLeastSignificantBits())
                    .putInt(encoded.length).putShort((short) index).put(encoded, offset, length).array();
            frames.add(frame);
        }
        return List.copyOf(frames);
    }

    /** Allocate one receiver per physical connection and call it from that connection's serial executor. */
    public static final class Reassembler {
        private final Map<UUID, Assembly> pending = new HashMap<>();
        private final Map<UUID, Long> completed = new HashMap<>();

        public Optional<byte[]> accept(byte[] frame, long nowNanos) {
            if (frame.length <= HEADER_BYTES || frame.length > UiProtocol.MAX_FRAME_BYTES) {
                throw invalid();
            }
            ByteBuffer input = ByteBuffer.wrap(frame);
            if (input.getInt() != MAGIC) {
                throw invalid();
            }
            UUID transferId = new UUID(input.getLong(), input.getLong());
            int size = input.getInt();
            int index = Short.toUnsignedInt(input.getShort());
            requireMessageSize(size);
            int count = fragmentCount(size);
            if (index >= count || input.remaining() != Math.min(FRAGMENT_BYTES, size - index * FRAGMENT_BYTES)) {
                throw invalid();
            }

            expire(nowNanos);
            if (completed.containsKey(transferId)) {
                return Optional.empty();
            }
            Assembly assembly = pending.get(transferId);
            if (assembly == null) {
                if (pending.size() >= MAX_IN_FLIGHT || pending.size() + completed.size() >= MAX_TRANSFERS) {
                    return Optional.empty();
                }
                assembly = new Assembly(size, count, nowNanos);
                pending.put(transferId, assembly);
            }
            if (assembly.encoded.length != size) {
                throw invalid();
            }
            if (assembly.received[index]) {
                return Optional.empty();
            }
            input.get(assembly.encoded, index * FRAGMENT_BYTES, input.remaining());
            assembly.received[index] = true;
            if (++assembly.fragments < count) {
                return Optional.empty();
            }
            pending.remove(transferId);
            completed.put(transferId, nowNanos);
            return Optional.of(assembly.encoded);
        }

        public void expire(long nowNanos) {
            completed.values().removeIf(time -> nowNanos - time >= TRANSFER_TTL);
            var iterator = pending.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (nowNanos - entry.getValue().started >= TRANSFER_TTL) {
                    completed.put(entry.getKey(), nowNanos);
                    iterator.remove();
                }
            }
        }
    }

    private static int fragmentCount(int size) {
        return (size - 1) / FRAGMENT_BYTES + 1;
    }

    private static void requireMessageSize(int size) {
        if (size <= 0 || size > UiProtocol.MAX_MESSAGE_BYTES) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Malformed page protocol fragment");
    }

    private static final class Assembly {
        final byte[] encoded;
        final boolean[] received;
        final long started;
        int fragments;

        Assembly(int size, int count, long started) {
            this.encoded = new byte[size];
            this.received = new boolean[count];
            this.started = started;
        }
    }
}
