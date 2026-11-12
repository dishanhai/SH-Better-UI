// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.protocol;

import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolFraming.Reassembler;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.Hello;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocolMessage.Payload;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Connection-bound sequencing, framing and message-size enforcement. Call from one serial executor. */
public final class UiProtocolChannel {
    private static final int TRACE_CAPACITY = 64;
    private final UUID connectionId;
    private final Consumer<byte[]> sendFrame;
    private final Reassembler reassembler = new Reassembler();
    private long sent;
    private long received;
    private int maxMessageBytes = UiProtocol.MAX_MESSAGE_BYTES;
    private ArrayDeque<UiProtocolTrace> traces;

    public UiProtocolChannel(UUID connectionId, Consumer<byte[]> sendFrame) {
        this.connectionId = Objects.requireNonNull(connectionId, "connectionId");
        this.sendFrame = Objects.requireNonNull(sendFrame, "sendFrame");
    }

    public UiProtocolChannel(UiProtocolMessage initialHello, Consumer<byte[]> sendFrame) {
        this(initialHello.sessionId(), sendFrame);
        if (!isInitialHello(initialHello)) {
            throw new IllegalArgumentException("Initial page protocol message must be HELLO");
        }
        received = initialHello.sequence();
    }

    public static Optional<UiProtocolMessage> acceptInitial(Reassembler reassembler, byte[] frame, long nowNanos) {
        return reassembler.accept(frame, nowNanos).map(UiProtocolCodec::decode)
                .filter(UiProtocolChannel::isInitialHello);
    }

    private static boolean isInitialHello(UiProtocolMessage message) {
        return message.version().equals(UiProtocol.VERSION) && message.sequence() == 1
                && message.payload() instanceof Hello;
    }

    public void limitMessages(int maxBytes) {
        if (maxBytes < 1024 || maxBytes > maxMessageBytes) {
            throw new IllegalArgumentException("Negotiated message limit can only decrease");
        }
        maxMessageBytes = maxBytes;
    }

    public UUID sessionId() {
        return connectionId;
    }

    public void send(Payload payload) {
        UiProtocolMessage message = next(payload);
        byte[] encoded = encode(message);
        sent = message.sequence();
        trace(UiProtocolTrace.Direction.OUTBOUND, message, encoded.length);
        for (byte[] frame : UiProtocolFraming.split(encoded, UUID.randomUUID())) {
            sendFrame.accept(frame);
        }
    }

    public boolean canSend(Payload payload) {
        try {
            encode(next(payload));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private UiProtocolMessage next(Payload payload) {
        return new UiProtocolMessage(UiProtocol.VERSION, connectionId, sent + 1, payload);
    }

    private byte[] encode(UiProtocolMessage message) {
        byte[] encoded = UiProtocolCodec.encode(message);
        if (encoded.length > maxMessageBytes) {
            throw new IllegalArgumentException("Page message exceeds negotiated limit");
        }
        return encoded;
    }

    public Optional<UiProtocolMessage> receive(byte[] frame, long nowNanos) {
        var encoded = reassembler.accept(frame, nowNanos);
        if (encoded.isEmpty()) {
            return Optional.empty();
        }
        byte[] bytes = encoded.orElseThrow();
        if (bytes.length > maxMessageBytes) {
            throw new IllegalArgumentException("Page message exceeds negotiated limit");
        }
        var message = UiProtocolCodec.decode(bytes, connectionId, received);
        message.ifPresent(value -> {
            received = value.sequence();
            trace(UiProtocolTrace.Direction.INBOUND, value, bytes.length);
        });
        return message;
    }

    public void expireFragments(long nowNanos) {
        reassembler.expire(nowNanos);
    }

    public void setTracing(boolean enabled) {
        if (enabled && traces == null) {
            traces = new ArrayDeque<>(TRACE_CAPACITY);
        } else if (!enabled) {
            traces = null;
        }
    }

    public List<UiProtocolTrace> traceSnapshot() {
        return traces == null ? List.of() : List.copyOf(traces);
    }

    private void trace(UiProtocolTrace.Direction direction, UiProtocolMessage message, int bytes) {
        if (traces != null) {
            if (traces.size() == TRACE_CAPACITY) {
                traces.removeFirst();
            }
            traces.addLast(UiProtocolTrace.capture(direction, message, bytes));
        }
    }
}
