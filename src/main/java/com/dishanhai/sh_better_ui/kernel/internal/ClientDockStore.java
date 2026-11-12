// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.dock.WindowPlacement;
import com.dishanhai.sh_better_ui.kernel.internal.dock.DockPersistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Owner-thread placements with serial, atomic I/O. The worker only receives detached data. */
final class ClientDockStore {
    private static final long RETRY_DELAY = TimeUnit.SECONDS.toNanos(1);
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(0, 1, 5, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), work -> new Thread(work, "SH-Better-UI dock layout writer"));
    private final Map<UiId, WindowPlacement> placements = new HashMap<>();
    private final Set<UiId> changed = new HashSet<>();
    private final LongSupplier clock;
    private CompletableFuture<Map<UiId, WindowPlacement>> loading;
    private CompletableFuture<Void> writing = CompletableFuture.completedFuture(null);
    private Path path;
    private boolean dirty, failedLoad;
    private long nextRetry;

    ClientDockStore() { this(System::nanoTime); }

    ClientDockStore(LongSupplier clock) {
        this.clock = clock;
        nextRetry = clock.getAsLong();
    }

    void start(Path config) {
        path = config.resolve("sh_better_ui-dock-layout.txt");
        var source = path;
        loading = CompletableFuture.supplyAsync(() -> {
            try {
                if (!Files.exists(source)) return Map.of();
                if (Files.size(source) > 256 * 1024) throw new IOException("Dock layout file exceeds 256 KiB");
                return DockPersistence.decode(Files.readString(source));
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        });
    }

    WindowPlacement get(UiId id) { return placements.get(id); }

    void set(UiId id, WindowPlacement placement) {
        if (loading != null) changed.add(id);
        if (placement == null) placements.remove(id);
        else placements.put(id, placement);
        dirty = true;
    }

    void update(BiConsumer<UiId, WindowPlacement> restore, Consumer<Throwable> report) {
        if (loading != null && loading.isDone()) {
            try {
                var loaded = loading.join();
                for (var entry : loaded.entrySet()) {
                    if (changed.contains(entry.getKey())) continue;
                    placements.put(entry.getKey(), entry.getValue());
                    restore.accept(entry.getKey(), entry.getValue());
                }
            } catch (RuntimeException failure) {
                failedLoad = true;
                report.accept(failure);
            } finally {
                loading = null;
                changed.clear();
            }
        }
        if (writing.isCompletedExceptionally()) {
            try {
                writing.join();
            } catch (RuntimeException failure) {
                writing = CompletableFuture.completedFuture(null);
                dirty = true;
                nextRetry = clock.getAsLong() + RETRY_DELAY;
                report.accept(failure);
            }
        }
        if (dirty && !failedLoad && loading == null && path != null && writing.isDone()
                && clock.getAsLong() - nextRetry >= 0) {
            dirty = false;
            var data = Map.copyOf(placements);
            var target = path;
            writing = CompletableFuture.runAsync(() -> write(target, data), writer);
        }
    }

    void finish() {
        if (!failedLoad && path != null && (dirty || !writing.isDone() || writing.isCompletedExceptionally())) {
            var current = Map.copyOf(placements);
            var changes = Set.copyOf(changed);
            var pending = loading;
            var target = path;
            var previousWrite = writing;
            boolean needsWrite = dirty;
            writer.execute(() -> {
                try {
                    if (!needsWrite && !previousWrite.isCompletedExceptionally()) return;
                    var finalState = new HashMap<>(pending == null ? Map.<UiId, WindowPlacement>of() : pending.join());
                    for (var id : changes) finalState.remove(id);
                    finalState.putAll(current);
                    write(target, finalState);
                } catch (RuntimeException failure) {
                    System.getLogger(ClientDockStore.class.getName()).log(System.Logger.Level.ERROR,
                            "Could not flush the dock layout", failure);
                }
            });
        }
        writer.shutdown();
    }

    private static void write(Path target, Map<UiId, WindowPlacement> data) {
        try {
            Files.createDirectories(target.getParent());
            var temporary = Files.createTempFile(target.getParent(), "shbui-dock-", ".tmp");
            try {
                Files.writeString(temporary, DockPersistence.encode(data));
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
