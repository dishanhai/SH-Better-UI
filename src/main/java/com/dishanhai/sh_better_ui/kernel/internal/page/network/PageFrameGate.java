// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.page.network;

import com.dishanhai.sh_better_ui.kernel.api.protocol.UiProtocol;
import io.netty.util.AttributeKey;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

final class PageFrameGate {
    private static final AttributeKey<AtomicInteger> QUEUED = AttributeKey.valueOf("sh_better_ui:page_frames");
    private static final int LIMIT = 16;

    private PageFrameGate() {}

    static byte[] read(FriendlyByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length > UiProtocol.MAX_FRAME_BYTES) {
            buffer.skipBytes(length);
            return new byte[0];
        }
        var frame = new byte[length];
        buffer.readBytes(frame);
        return frame;
    }

    static void submit(Connection connection, byte[] frame, Function<Runnable, CompletableFuture<Void>> enqueue,
                       Consumer<byte[]> consume) {
        if (frame.length == 0) return;
        var attribute = connection.channel().attr(QUEUED);
        var queued = attribute.get();
        if (queued == null) {
            var fresh = new AtomicInteger();
            var previous = attribute.setIfAbsent(fresh);
            queued = previous == null ? fresh : previous;
        }
        final var pending = queued;
        if (pending.incrementAndGet() > LIMIT) {
            pending.decrementAndGet();
            return;
        }
        try {
            enqueue.apply(() -> consume.accept(frame)).whenComplete((ignored, failure) -> pending.decrementAndGet());
        } catch (RuntimeException schedulingFailure) {
            pending.decrementAndGet();
            throw schedulingFailure;
        }
    }
}
