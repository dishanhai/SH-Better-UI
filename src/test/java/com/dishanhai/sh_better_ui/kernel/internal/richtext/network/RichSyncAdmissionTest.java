// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.richtext.network;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RichSyncAdmissionTest {
    @Test
    void floodedPeerCannotFillAnotherPeersQueueAndFailuresReleaseSlots() {
        var flooded = new Connection(PacketFlow.SERVERBOUND);
        var other = new Connection(PacketFlow.SERVERBOUND);
        var work = new ArrayList<CompletableFuture<Void>>();
        Function<Runnable, CompletableFuture<Void>> queue = action -> {
            var future = new CompletableFuture<Void>();
            work.add(future);
            return future;
        };
        Runnable noop = () -> {};
        try {
            for (int index = 0; index < 32; index++) RichSyncAdmission.schedule(flooded, queue, noop);
            assertEquals(16, work.size());
            RichSyncAdmission.schedule(other, queue, noop);
            assertEquals(17, work.size());
            work.get(0).completeExceptionally(new IllegalStateException("Cancelled queued work"));
            assertThrows(IllegalStateException.class, () -> RichSyncAdmission.schedule(flooded, action -> {
                throw new IllegalStateException("Queue stopped");
            }, noop));
            RichSyncAdmission.schedule(flooded, queue, noop);
            assertEquals(18, work.size());
        } finally {
            work.forEach(item -> item.complete(null));
            RichSyncAdmission.forget(flooded);
            RichSyncAdmission.forget(other);
        }
    }
}
