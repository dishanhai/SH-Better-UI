// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.automation.capability;

import com.dishanhai.sh_better_ui.kernel.api.RegistrationHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.automation.capability.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.dishanhai.sh_better_ui.kernel.api.automation.capability.CapabilityResult.Code.*;

/** Host-owned server registry. Never publish this registrar or bind operation to untrusted clients. */
public final class ServerCapabilityRegistry implements AutoCloseable {
    public interface ServerThread {
        boolean isOnThread();
        /** Must queue on the server thread, or throw before accepting work. */
        void execute(Runnable action);
    }
    @FunctionalInterface
    public interface PermissionChecker {
        /** Called on the server thread for discovery and again immediately before invocation. */
        boolean allows(CapabilityCaller caller, CapabilityDescriptor descriptor);
    }
    private record Key(UiId id, int version) {}
    private record TaskKey(UUID player, UUID task) {}
    private record Task(CapabilityReference reference, CapabilityRequest request,
                        CompletableFuture<CapabilityResult> result) {}

    private final ServerThread thread;
    private final PermissionChecker permissions;
    private final int taskCapacity;
    private final Map<Key, Entry> entries = new HashMap<>();
    private final Map<TaskKey, Task> tasks = new HashMap<>();
    private boolean closed;

    public ServerCapabilityRegistry(ServerThread thread, PermissionChecker permissions) {
        this(thread, permissions, 4096);
    }

    /** Task records are never evicted while the registry is alive; capacity exhaustion fails closed. */
    public ServerCapabilityRegistry(ServerThread thread, PermissionChecker permissions, int taskCapacity) {
        this.thread = Objects.requireNonNull(thread, "thread");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        if (taskCapacity < 1) throw new IllegalArgumentException("Task capacity must be positive");
        this.taskCapacity = taskCapacity;
    }

    /** The host supplies a server-owned scope. Closing it revokes the registration and pending outcomes. */
    public RegistrationHandle register(ScopeHandle scope, CapabilityDescriptor descriptor, CapabilityHandler handler) {
        assertThread();
        requireOpen();
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(handler, "handler");
        if (!scope.isOpen()) throw new IllegalStateException("Provider scope is closed");
        if (!scope.owner().equals(descriptor.provider())) throw new SecurityException("Provider does not own scope");
        Key key = key(descriptor);
        if (entries.containsKey(key)) throw new IllegalStateException("Capability provider conflict: " + descriptor.id());
        Entry entry = new Entry(scope, descriptor, handler);
        entries.put(key, entry);
        try {
            scope.track(entry);
        } catch (RuntimeException failure) {
            entry.close();
            throw failure;
        }
        return entry;
    }

    /** Identity and session scope must come from authenticated server context, not decoded client data. */
    public CapabilityDirectory bind(ScopeHandle session, CapabilityCaller caller) {
        assertThread();
        requireOpen();
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(caller, "caller");
        if (!session.isOpen()) throw new IllegalStateException("Session scope is closed");
        return new View(session, caller);
    }

    @Override
    public void close() {
        assertThread();
        if (closed) return;
        closed = true;
        for (Entry entry : List.copyOf(entries.values())) entry.close();
        tasks.clear();
    }

    private void assertThread() {
        if (!thread.isOnThread()) throw new IllegalStateException("Capability operation requires server thread");
    }
    private void requireOpen() {
        if (closed) throw new IllegalStateException("Capability registry is closed");
    }
    private static Key key(CapabilityDescriptor descriptor) {
        return new Key(descriptor.id(), descriptor.contractVersion());
    }
    private boolean allowed(CapabilityCaller caller, CapabilityDescriptor descriptor) {
        try { return permissions.allows(caller, descriptor); }
        catch (RuntimeException failure) { return false; }
    }

    private final class Entry implements RegistrationHandle {
        final ScopeHandle scope;
        final CapabilityReference reference;
        final CapabilityHandler handler;
        final Set<CompletableFuture<CapabilityResult>> pending = new HashSet<>();
        boolean active = true;

        Entry(ScopeHandle scope, CapabilityDescriptor descriptor, CapabilityHandler handler) {
            this.scope = scope;
            this.reference = new CapabilityReference(descriptor, UUID.randomUUID());
            this.handler = handler;
        }
        boolean available() { return active && scope.isOpen(); }
        @Override
        public Status status() {
            assertThread();
            return available() ? Status.ACTIVE : Status.REVOKED;
        }
        @Override
        public void close() {
            assertThread();
            if (!active) return;
            active = false;
            entries.remove(key(reference.descriptor()), this);
            for (var future : List.copyOf(pending)) future.complete(CapabilityResult.unknown(REVOKED));
            pending.clear();
        }
    }

    private final class View implements CapabilityDirectory {
        private final ScopeHandle session;
        private final CapabilityCaller caller;

        View(ScopeHandle session, CapabilityCaller caller) { this.session = session; this.caller = caller; }

        @Override
        public List<CapabilityDescriptor> discover() {
            assertThread();
            if (closed || !session.isOpen()) return List.of();
            var result = new ArrayList<CapabilityDescriptor>();
            for (Entry entry : entries.values()) {
                if (entry.available() && allowed(caller, entry.reference.descriptor())) result.add(entry.reference.descriptor());
            }
            result.sort(Comparator.comparing(CapabilityDescriptor::id).thenComparingInt(CapabilityDescriptor::contractVersion));
            return List.copyOf(result);
        }

        @Override
        public Optional<CapabilityReference> find(UiId id, int contractVersion) {
            assertThread();
            Objects.requireNonNull(id, "id");
            if (closed || !session.isOpen()) return Optional.empty();
            Entry entry = entries.get(new Key(id, contractVersion));
            return entry != null && entry.available() && allowed(caller, entry.reference.descriptor())
                    ? Optional.of(entry.reference) : Optional.empty();
        }

        @Override
        public CompletionStage<CapabilityResult> invoke(CapabilityReference reference, CapabilityRequest request) {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(request, "request");
            var response = new CompletableFuture<CapabilityResult>();
            Runnable invoke = () -> {
                assertThread();
                invokeOnServer(reference, request).whenComplete((result, failure) -> {
                    if (failure != null) response.complete(CapabilityResult.unknown(PROVIDER_FAILURE));
                    else response.complete(result);
                });
            };
            if (thread.isOnThread()) invoke.run();
            else {
                try { thread.execute(invoke); }
                catch (RuntimeException failure) { response.complete(CapabilityResult.notExecuted(SERVER_UNAVAILABLE)); }
            }
            // Consumers cannot complete/cancel the shared deduplication record.
            return response.minimalCompletionStage();
        }

        private CompletableFuture<CapabilityResult> invokeOnServer(CapabilityReference reference, CapabilityRequest request) {
            if (closed) return rejected(UNAVAILABLE);
            if (!session.isOpen()) return rejected(SESSION_CLOSED);
            Entry entry = entries.get(key(reference.descriptor()));
            if (entry == null || !entry.available() || !entry.reference.equals(reference)) return rejected(UNAVAILABLE);
            CapabilityDescriptor descriptor = entry.reference.descriptor();
            if (!allowed(caller, descriptor)) return rejected(PERMISSION_DENIED);
            try { descriptor.parameters().validate(request.parameters()); }
            catch (IllegalArgumentException failure) { return rejected(INVALID_PARAMETERS); }
            boolean deduplicate = descriptor.deduplication() == CapabilityDescriptor.Deduplication.TASK_ID_REQUIRED;
            if (deduplicate && request.taskId() == null) return rejected(TASK_ID_REQUIRED);
            TaskKey taskKey = deduplicate ? new TaskKey(caller.playerId(), request.taskId()) : null;
            if (deduplicate) {
                Task previous = tasks.get(taskKey);
                if (previous != null) {
                    return previous.reference().equals(reference) && previous.request().equals(request)
                            ? previous.result() : rejected(TASK_CONFLICT);
                }
                if (tasks.size() >= taskCapacity) return rejected(DEDUP_CAPACITY);
            }
            var response = new CompletableFuture<CapabilityResult>();
            if (deduplicate) tasks.put(taskKey, new Task(reference, request, response));
            entry.pending.add(response);
            try {
                CompletionStage<CapabilityResult> stage = Objects.requireNonNull(
                        entry.handler.invoke(caller, request), "Provider returned null stage");
                stage.whenComplete((result, failure) -> completeOnServer(entry, response, result, failure));
            } catch (RuntimeException failure) {
                completeOnServer(entry, response, null, failure);
            }
            return response;
        }

        private void completeOnServer(Entry entry, CompletableFuture<CapabilityResult> response,
                                      CapabilityResult result, Throwable failure) {
            Runnable complete = () -> {
                assertThread();
                entry.pending.remove(response);
                if (response.isDone()) return;
                if (!entry.available()) response.complete(CapabilityResult.unknown(REVOKED));
                else if (failure != null || result == null) response.complete(CapabilityResult.unknown(PROVIDER_FAILURE));
                else {
                    try {
                        if (result.status() == CapabilityResult.Status.SUCCESS) entry.reference.descriptor().result().validate(result.values());
                        response.complete(result);
                    } catch (IllegalArgumentException invalid) {
                        response.complete(CapabilityResult.unknown(INVALID_RESULT));
                    }
                }
            };
            if (thread.isOnThread()) complete.run();
            else {
                try { thread.execute(complete); }
                catch (RuntimeException failureToSchedule) {
                    // No game state or registry collections are touched off-thread; execution may have happened.
                    response.complete(CapabilityResult.unknown(SERVER_UNAVAILABLE));
                }
            }
        }
    }

    private static CompletableFuture<CapabilityResult> rejected(CapabilityResult.Code code) {
        return CompletableFuture.completedFuture(CapabilityResult.notExecuted(code));
    }
}
