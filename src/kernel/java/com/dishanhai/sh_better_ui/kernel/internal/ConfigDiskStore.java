package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ConfigPersistenceSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

final class ConfigDiskStore implements AutoCloseable {
    private static final int MAX_FILE_BYTES = 1_048_576;
    record Stored(int version, String type, String value) {}
    record Prepared(Map<String, Properties> states, Map<String, byte[]> bytes) {}
    private record Waiter(long sequence, CompletableFuture<Void> future) {}

    private final Path directory;
    private final BiConsumer<String, Throwable> failures;
    private final java.util.concurrent.ExecutorService writer = Executors.newSingleThreadExecutor(action -> {
        var thread = new Thread(action, "SH-Better-UI config writer");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Properties> states = new LinkedHashMap<>();
    private final Map<String, byte[]> pending = new LinkedHashMap<>();
    private final Map<String, Throwable> failed = new LinkedHashMap<>();
    private final Set<String> rejected = ConcurrentHashMap.newKeySet();
    private final ArrayList<Waiter> waiters = new ArrayList<>();
    private long submitted;
    private long completed;
    private boolean writing;
    private boolean closed;

    ConfigDiskStore(Path directory, BiConsumer<String, Throwable> failures) {
        this.directory = directory.toAbsolutePath().normalize();
        this.failures = failures;
    }

    synchronized Stored read(UiId id) {
        requireOpen();
        var values = state(id.namespace());
        String version = values.getProperty(id + ".version");
        String type = values.getProperty(id + ".type");
        String value = values.getProperty(id + ".value");
        if (version == null && type == null && value == null) return null;
        if (version == null || type == null || value == null) {
            throw new IllegalArgumentException("Incomplete persisted configuration: " + id);
        }
        int parsed = Integer.parseInt(version);
        if (parsed < 1) throw new IllegalArgumentException("Invalid persisted configuration version: " + id);
        return new Stored(parsed, type, value);
    }

    void reject(UiId id, RuntimeException failure) {
        rejected.add(id.namespace());
        failures.accept("Loading UI configuration " + id, failure);
    }

    synchronized Prepared prepare(Map<UiId, Stored> changes) {
        requireOpen();
        var next = new LinkedHashMap<String, Properties>();
        for (var entry : changes.entrySet()) {
            var id = entry.getKey();
            var values = next.computeIfAbsent(id.namespace(), namespace -> copy(state(namespace)));
            var value = entry.getValue();
            if (value == null) {
                values.remove(id + ".version");
                values.remove(id + ".type");
                values.remove(id + ".value");
            } else {
                values.setProperty(id + ".version", Integer.toString(value.version()));
                values.setProperty(id + ".type", value.type());
                values.setProperty(id + ".value", value.value());
            }
        }
        var bytes = new LinkedHashMap<String, byte[]>();
        next.forEach((namespace, values) -> bytes.put(namespace, serialize(values)));
        return new Prepared(Map.copyOf(next), Map.copyOf(bytes));
    }

    private static byte[] serialize(Properties values) {
        try {
            var output = new ByteArrayOutputStream();
            values.store(output, "SH-Better-UI module configuration");
            if (output.size() > MAX_FILE_BYTES) throw new IllegalArgumentException("Configuration file too large");
            return output.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    synchronized void apply(Prepared prepared) {
        requireOpen();
        if (prepared.bytes().isEmpty()) return;
        states.putAll(prepared.states());
        pending.putAll(prepared.bytes());
        submitted++;
        schedule();
    }

    private void schedule() {
        if (!writing && !pending.isEmpty()) {
            writing = true;
            writer.execute(this::drain);
        }
    }

    synchronized CompletionStage<Void> flush() {
        if (!failed.isEmpty() && !writer.isShutdown()) {
            failed.keySet().forEach(namespace -> pending.put(namespace, serialize(states.get(namespace))));
            submitted++;
            schedule();
        }
        var result = new CompletableFuture<Void>();
        if (!writing && pending.isEmpty()) {
            complete(result);
        } else {
            waiters.add(new Waiter(submitted, result));
        }
        return result.minimalCompletionStage();
    }

    synchronized ConfigPersistenceSnapshot snapshot() {
        String error = failed.isEmpty() ? "" : failed.entrySet().iterator().next().getKey() + ": "
                + failed.entrySet().iterator().next().getValue();
        return new ConfigPersistenceSnapshot(true, submitted, completed, writing, error);
    }

    private void drain() {
        while (true) {
            Map<String, byte[]> batch;
            long sequence;
            synchronized (this) {
                if (pending.isEmpty()) {
                    writing = false;
                    return;
                }
                batch = new LinkedHashMap<>(pending);
                pending.clear();
                sequence = submitted;
            }
            for (var entry : batch.entrySet()) {
                try {
                    write(entry.getKey(), entry.getValue());
                    synchronized (this) { failed.remove(entry.getKey()); }
                } catch (IOException | RuntimeException failure) {
                    synchronized (this) { failed.put(entry.getKey(), failure); }
                    failures.accept("Persisting UI configuration " + entry.getKey(), failure);
                }
            }
            var ready = new ArrayList<CompletableFuture<Void>>();
            Throwable failure;
            synchronized (this) {
                if (failed.isEmpty()) completed = sequence;
                failure = failed.isEmpty() ? null : failed.values().iterator().next();
                for (int i = 0; i < waiters.size();) {
                    var waiter = waiters.get(i);
                    if (waiter.sequence() <= sequence) {
                        waiters.remove(i);
                        ready.add(waiter.future());
                    } else {
                        i++;
                    }
                }
            }
            for (var future : ready) {
                if (failure == null) future.complete(null); else future.completeExceptionally(failure);
            }
        }
    }

    private void complete(CompletableFuture<Void> future) {
        if (failed.isEmpty()) future.complete(null);
        else future.completeExceptionally(failed.values().iterator().next());
    }

    private Properties state(String namespace) {
        return states.computeIfAbsent(namespace, name -> {
            Path file = directory.resolve(name + ".properties");
            var values = new Properties();
            try {
                if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("Configuration file too large: " + file);
                byte[] bytes;
                try (var input = Files.newInputStream(file)) {
                    bytes = input.readNBytes(MAX_FILE_BYTES + 1);
                }
                if (bytes.length > MAX_FILE_BYTES) throw new IOException("Configuration file too large: " + file);
                values.load(new ByteArrayInputStream(bytes));
                return values;
            } catch (NoSuchFileException missing) {
                return values;
            } catch (IOException | IllegalArgumentException failure) {
                rejected.add(name);
                failures.accept("Loading UI configuration " + name, failure);
                return new Properties();
            }
        });
    }

    private void write(String namespace, byte[] bytes) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(namespace + ".properties");
        Path temporary = Files.createTempFile(directory, namespace + "-", ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            if (rejected.contains(namespace)) preserveRejected(target, namespace);
            Files.move(temporary, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void preserveRejected(Path target, String namespace) throws IOException {
        Path backup = Files.createTempFile(directory, namespace + "-", ".properties.corrupt");
        boolean preserved = false;
        try {
            if (!Files.readAttributes(target, BasicFileAttributes.class).isRegularFile()) {
                throw new IOException("Configuration is not a regular file: " + target);
            }
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            preserved = true;
            rejected.remove(namespace);
        } catch (NoSuchFileException missing) {
            rejected.remove(namespace);
        } finally {
            if (!preserved) Files.deleteIfExists(backup);
        }
    }

    private static Properties copy(Properties source) {
        var result = new Properties();
        result.putAll(source);
        return result;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Configuration persistence is closed");
    }

    @Override public void close() {
        CompletionStage<Void> remaining;
        synchronized (this) {
            if (closed) return;
            closed = true;
            remaining = flush();
        }
        writer.shutdown();
        try {
            remaining.toCompletableFuture().get(10, TimeUnit.SECONDS);
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out closing UI configuration writer");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            failures.accept("Closing UI configuration persistence", failure);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            failures.accept("Flushing UI configuration persistence", failure);
        }
    }
}
