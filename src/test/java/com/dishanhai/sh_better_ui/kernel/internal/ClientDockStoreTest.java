// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockEdge;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockMode;
import com.dishanhai.sh_better_ui.kernel.api.dock.WindowPlacement;
import com.dishanhai.sh_better_ui.kernel.internal.dock.DockPersistence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ClientDockStoreTest {
    private static final UiId ID = UiId.parse("example:window");

    @Test
    void failedBackgroundWriteRetriesTheLatestPlacement(@TempDir Path config) throws Exception {
        verifyRecovery(config, false);
    }

    @Test
    void shutdownFlushesAFailedWriteBeforeTheRetryDelay(@TempDir Path config) throws Exception {
        verifyRecovery(config, true);
    }

    private static void verifyRecovery(Path config, boolean finish) throws Exception {
        var clock = new AtomicLong();
        var failures = new AtomicInteger();
        var initial = WindowPlacement.docked(DockEdge.LEFT, DockMode.EDGE_DOCKED, 160, 120);
        var latest = WindowPlacement.docked(DockEdge.RIGHT, DockMode.EDGE_DOCKED, 180, 120);
        var path = config.resolve("sh_better_ui-dock-layout.txt");
        Files.writeString(path, DockPersistence.encode(Map.of(ID, initial)));
        var store = new ClientDockStore(clock::get);
        boolean stopped = false;
        try {
            store.start(config);
            await(() -> {
                store.update((id, placement) -> {}, failure -> fail(failure));
                return initial.equals(store.get(ID));
            });
            Files.delete(path);
            Files.createDirectory(path);
            Files.writeString(path.resolve("blocked"), "blocked");
            store.set(ID, latest);
            await(() -> {
                store.update((id, placement) -> {}, failure -> failures.incrementAndGet());
                return failures.get() == 1;
            });
            Files.delete(path.resolve("blocked"));
            Files.delete(path);
            if (finish) {
                store.finish();
                stopped = true;
            } else {
                clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
            }
            await(() -> {
                if (!finish) store.update((id, placement) -> {}, failure -> fail(failure));
                return Files.isRegularFile(path) && latest.equals(DockPersistence.decode(Files.readString(path)).get(ID));
            });
        } finally {
            if (!stopped) store.finish();
        }
    }

    private static void await(Check condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.met()) {
            if (System.nanoTime() - deadline >= 0) fail("Dock storage did not settle");
            Thread.sleep(5);
        }
    }

    @FunctionalInterface
    private interface Check {
        boolean met() throws Exception;
    }
}
