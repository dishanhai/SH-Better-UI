// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.automation.capability;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.RegistrationHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.automation.capability.*;
import com.dishanhai.sh_better_ui.kernel.internal.ServerAutomationScopes;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.dishanhai.sh_better_ui.kernel.api.automation.capability.CapabilityResult.Code.*;
import static com.dishanhai.sh_better_ui.kernel.api.automation.capability.CapabilityResult.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerCapabilityRegistryTest {
    private static final UiId PROVIDER = UiId.parse("example:provider");
    private static final UiId ID = UiId.parse("example:inventory/count");
    private static final CapabilitySchema EMPTY = new CapabilitySchema(Map.of());
    private static final CapabilitySchema COUNT = new CapabilitySchema(Map.of("count",
            new CapabilitySchema.Field(CapabilitySchema.Type.INTEGER, true)));

    @Test
    void scopeCloseRevokesOldReferenceAndDuplicateProvidersFailExplicitly() {
        try (var fixture = new Fixture()) {
            var handle = fixture.register((caller, request) -> CompletableFuture.completedFuture(ok()));
            var ref = fixture.reference();
            assertEquals(List.of(descriptor()), fixture.view.discover());
            assertThrows(IllegalStateException.class, () -> fixture.register((caller, request) -> null));
            fixture.scopes.closeModule(PROVIDER);
            assertEquals(RegistrationHandle.Status.REVOKED, handle.status());
            assertTrue(fixture.view.discover().isEmpty());
            assertEquals(UNAVAILABLE, fixture.invoke(ref, UUID.randomUUID()).code());
            fixture.provider = fixture.scopes.openModule(module(PROVIDER));
            fixture.register((caller, request) -> CompletableFuture.completedFuture(ok()));
            assertNotEquals(ref.registrationId(), fixture.reference().registrationId());
            assertEquals(UNAVAILABLE, fixture.invoke(ref, UUID.randomUUID()).code());
            handle.close();
            assertTrue(fixture.view.find(ID, 1).isPresent());
        }
    }

    @Test
    void permissionIsCheckedAgainAndRequestsCannotSupplyPlayerIdentity() {
        try (var fixture = new Fixture()) {
            var observed = new AtomicReference<CapabilityCaller>();
            var calls = new AtomicInteger();
            fixture.register((caller, request) -> {
                observed.set(caller);
                calls.incrementAndGet();
                return CompletableFuture.completedFuture(ok());
            });
            var ref = fixture.reference();
            fixture.allowed.set(false);
            assertTrue(fixture.view.discover().isEmpty());
            assertEquals(PERMISSION_DENIED, fixture.invoke(ref, UUID.randomUUID()).code());
            assertEquals(0, calls.get());
            fixture.allowed.set(true);
            var forged = new CapabilityRequest(UUID.randomUUID(), Map.of("player_id", new CapabilityValue.Text("other")));
            assertEquals(INVALID_PARAMETERS, fixture.view.invoke(ref, forged).toCompletableFuture().join().code());
            assertEquals(SUCCESS, fixture.invoke(ref, UUID.randomUUID()).status());
            assertEquals(fixture.caller, observed.get());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void providerIdentityCannotBeForgedAndCompetingProvidersCannotBeSelectedArbitrarily() {
        try (var fixture = new Fixture()) {
            var foreignId = UiId.parse("other:provider");
            var foreignScope = fixture.scopes.openModule(module(foreignId));
            assertThrows(SecurityException.class, () -> fixture.registry.register(foreignScope, descriptor(),
                    (caller, request) -> CompletableFuture.completedFuture(ok())));
            fixture.register((caller, request) -> CompletableFuture.completedFuture(ok()));
            var competing = new CapabilityDescriptor(ID, 1, foreignId, new UiVersion(2, 0, 0), EMPTY, COUNT,
                    CapabilityDescriptor.Access.WRITE, UiId.parse("other:write"), CapabilityDescriptor.Deduplication.NONE);
            assertThrows(IllegalStateException.class, () -> fixture.registry.register(foreignScope, competing,
                    (caller, request) -> CompletableFuture.completedFuture(ok())));
            assertEquals(PROVIDER, fixture.reference().descriptor().provider());
        }
    }

    @Test
    void deduplicationCoversPendingCompletedAndReconnectWithoutExposingMutableFuture() {
        try (var fixture = new Fixture()) {
            var calls = new AtomicInteger();
            var providerResult = new CompletableFuture<CapabilityResult>();
            var task = UUID.randomUUID();
            fixture.register((caller, request) -> {
                assertEquals(task, request.taskId());
                calls.incrementAndGet();
                return providerResult;
            });
            var reference = fixture.reference();
            var request = new CapabilityRequest(task, Map.of());
            var first = fixture.view.invoke(reference, request).toCompletableFuture();
            first.complete(CapabilityResult.notExecuted(PROVIDER_REJECTED));
            var second = fixture.view.invoke(reference, request).toCompletableFuture();
            assertFalse(second.isDone());
            providerResult.complete(ok());
            assertEquals(SUCCESS, second.join().status());
            fixture.scopes.closeSession(fixture.caller.playerId());
            var reconnected = fixture.registry.bind(fixture.scopes.openSession(fixture.caller.playerId()), fixture.caller);
            assertEquals(SUCCESS, reconnected.invoke(reference, request).toCompletableFuture().join().status());
            assertEquals(1, calls.get());
            assertEquals(SESSION_CLOSED, fixture.invoke(reference, task).code());
        }
    }

    @Test
    void mismatchedTasksAndFullDedupStoreFailBeforeExecution() {
        try (var fixture = new Fixture(1)) {
            var calls = new AtomicInteger();
            fixture.register((caller, request) -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(ok()); });
            var task = UUID.randomUUID();
            assertEquals(TASK_ID_REQUIRED, fixture.invoke(fixture.reference(), null).code());
            assertEquals(SUCCESS, fixture.invoke(fixture.reference(), task).status());
            assertEquals(DEDUP_CAPACITY, fixture.invoke(fixture.reference(), UUID.randomUUID()).code());
            var other = new CapabilityDescriptor(UiId.parse("example:other"), 1, PROVIDER, new UiVersion(1, 0, 0),
                    EMPTY, COUNT, CapabilityDescriptor.Access.WRITE, UiId.parse("example:write"),
                    CapabilityDescriptor.Deduplication.TASK_ID_REQUIRED);
            fixture.registry.register(fixture.provider, other, (caller, request) -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(ok()); });
            var otherRef = fixture.view.find(other.id(), 1).orElseThrow();
            assertEquals(TASK_CONFLICT, fixture.invoke(otherRef, task).code());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void workerSubmissionsAndAsyncCompletionReturnToServerThread() throws Exception {
        try (var fixture = new Fixture()) {
            var providerResult = new CompletableFuture<CapabilityResult>();
            var calls = new AtomicInteger();
            fixture.register((caller, request) -> {
                assertTrue(fixture.thread.isOnThread());
                calls.incrementAndGet();
                return providerResult;
            });
            var reference = fixture.reference();
            var submitted = new AtomicReference<CompletableFuture<CapabilityResult>>();
            runWorker(() -> submitted.set(fixture.view.invoke(reference, new CapabilityRequest(UUID.randomUUID(), Map.of())).toCompletableFuture()));
            assertEquals(0, calls.get());
            fixture.thread.drain();
            assertEquals(1, calls.get());
            runWorker(() -> providerResult.complete(ok()));
            assertFalse(submitted.get().isDone());
            var completedOn = new AtomicReference<Thread>();
            submitted.get().thenAccept(result -> completedOn.set(Thread.currentThread()));
            fixture.thread.drain();
            assertEquals(SUCCESS, submitted.get().join().status());
            assertSame(Thread.currentThread(), completedOn.get());
            runWorker(() -> assertThrows(IllegalStateException.class, fixture.view::discover));
        }
    }

    @Test
    void revocationDuringExecutionAndProviderFailuresAreUnknownNotSafeToRetry() {
        try (var fixture = new Fixture()) {
            var providerResult = new CompletableFuture<CapabilityResult>();
            var registration = fixture.register((caller, request) -> providerResult);
            var response = fixture.view.invoke(fixture.reference(), new CapabilityRequest(UUID.randomUUID(), Map.of())).toCompletableFuture();
            registration.close();
            assertEquals(UNKNOWN, response.join().status());
            assertEquals(REVOKED, response.join().code());
            providerResult.complete(ok());
            assertEquals(UNKNOWN, response.join().status());
            registration = fixture.register((caller, request) -> { throw new IllegalStateException("may have mutated"); });
            assertEquals(UNKNOWN, fixture.invoke(fixture.reference(), UUID.randomUUID()).status());
            registration.close();
            fixture.register((caller, request) -> CompletableFuture.completedFuture(CapabilityResult.success(Map.of())));
            assertEquals(INVALID_RESULT, fixture.invoke(fixture.reference(), UUID.randomUUID()).code());
        }
    }

    @Test
    void queuedCallsRecheckSessionLifetimeBeforeCallingProvider() throws Exception {
        try (var fixture = new Fixture()) {
            var calls = new AtomicInteger();
            fixture.register((caller, request) -> {
                calls.incrementAndGet();
                return CompletableFuture.completedFuture(ok());
            });
            var reference = fixture.reference();
            var result = new AtomicReference<CompletableFuture<CapabilityResult>>();
            runWorker(() -> result.set(fixture.view.invoke(reference,
                    new CapabilityRequest(UUID.randomUUID(), Map.of())).toCompletableFuture()));
            fixture.scopes.closeSession(fixture.caller.playerId());
            fixture.thread.drain();
            assertEquals(SESSION_CLOSED, result.get().join().code());
            assertEquals(NOT_EXECUTED, result.get().join().status());
            assertEquals(0, calls.get());
        }
    }

    @Test
    void executorRejectionDistinguishesUnstartedCallFromExecutedUnknownOutcome() throws Exception {
        try (var fixture = new Fixture()) {
            var pending = new CompletableFuture<CapabilityResult>();
            var calls = new AtomicInteger();
            fixture.register((caller, request) -> { calls.incrementAndGet(); return pending; });
            var reference = fixture.reference();
            fixture.thread.reject = true;
            var result = new AtomicReference<CompletableFuture<CapabilityResult>>();
            runWorker(() -> result.set(fixture.view.invoke(reference,
                    new CapabilityRequest(UUID.randomUUID(), Map.of())).toCompletableFuture()));
            assertEquals(NOT_EXECUTED, result.get().join().status());
            assertEquals(SERVER_UNAVAILABLE, result.get().join().code());
            assertEquals(0, calls.get());
            fixture.thread.reject = false;
            var executed = fixture.view.invoke(reference, new CapabilityRequest(UUID.randomUUID(), Map.of())).toCompletableFuture();
            fixture.thread.reject = true;
            runWorker(() -> pending.complete(ok()));
            assertEquals(UNKNOWN, executed.join().status());
            assertEquals(SERVER_UNAVAILABLE, executed.join().code());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void nonDeduplicatingWritesDescribeProviderLimitationsHonestly() {
        try (var fixture = new Fixture()) {
            var base = descriptor();
            var unsafe = new CapabilityDescriptor(base.id(), 1, PROVIDER, base.providerVersion(), EMPTY, COUNT,
                    CapabilityDescriptor.Access.WRITE, base.permission(), CapabilityDescriptor.Deduplication.NONE);
            var calls = new AtomicInteger();
            fixture.registry.register(fixture.provider, unsafe, (caller, request) -> {
                calls.incrementAndGet();
                return CompletableFuture.completedFuture(CapabilityResult.unknown(PROVIDER_FAILURE));
            });
            var task = UUID.randomUUID();
            assertEquals(UNKNOWN, fixture.invoke(fixture.reference(), task).status());
            assertEquals(UNKNOWN, fixture.invoke(fixture.reference(), task).status());
            assertEquals(2, calls.get());
        }
    }

    @Test
    void boundedValuesAndSchemasRejectMalformedData() {
        assertThrows(IllegalArgumentException.class, () -> new CapabilityValue.Decimal(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new CapabilityValue.Text("x".repeat(8193)));
        assertThrows(IllegalArgumentException.class, () -> new CapabilityValue.Fields(Map.of("bad-field", new CapabilityValue.Flag(true))));
        assertThrows(IllegalArgumentException.class, () -> COUNT.validate(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> COUNT.validate(Map.of("count", new CapabilityValue.Text("1"))));
        CapabilityValue nested = new CapabilityValue.Flag(true);
        for (int i = 0; i < 7; i++) nested = new CapabilityValue.Sequence(List.of(nested));
        CapabilityValue tooDeep = nested;
        assertThrows(IllegalArgumentException.class, () -> new CapabilityRequest(null, Map.of("nested", new CapabilityValue.Sequence(List.of(tooDeep)))));
        var values = new java.util.HashMap<String, CapabilityValue>();
        values.put("count", new CapabilityValue.IntegerValue(1));
        var request = new CapabilityRequest(null, values);
        values.clear();
        assertEquals(1, request.parameters().size());
        assertThrows(UnsupportedOperationException.class, request.parameters()::clear);
    }

    private static CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(ID, 1, PROVIDER, new UiVersion(1, 0, 0), EMPTY, COUNT,
                CapabilityDescriptor.Access.WRITE, UiId.parse("example:write"), CapabilityDescriptor.Deduplication.TASK_ID_REQUIRED);
    }
    private static CapabilityResult ok() { return CapabilityResult.success(Map.of("count", new CapabilityValue.IntegerValue(1))); }
    private static ModuleDescriptor module(UiId id) {
        return ModuleDescriptor.builder().id(id).version(new UiVersion(1, 0, 0)).build();
    }
    private static void runWorker(Runnable action) throws Exception {
        var failure = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } });
        worker.start();
        worker.join(5000);
        assertFalse(worker.isAlive());
        if (failure.get() != null) fail(failure.get());
    }
    private static final class TestThread implements ServerCapabilityRegistry.ServerThread {
        final Thread owner = Thread.currentThread();
        final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
        volatile boolean reject;
        @Override public boolean isOnThread() { return Thread.currentThread() == owner; }
        @Override public void execute(Runnable action) {
            if (reject) throw new java.util.concurrent.RejectedExecutionException();
            queue.add(action);
        }
        void drain() { Runnable action; while ((action = queue.poll()) != null) action.run(); }
    }
    private static final class Fixture implements AutoCloseable {
        final TestThread thread = new TestThread();
        final AtomicBoolean allowed = new AtomicBoolean(true);
        final ServerAutomationScopes scopes = new ServerAutomationScopes(failure -> fail(failure));
        final CapabilityCaller caller = new CapabilityCaller(UUID.randomUUID());
        final ServerCapabilityRegistry registry;
        final CapabilityDirectory view;
        ScopeHandle provider = scopes.openModule(module(PROVIDER));
        Fixture() { this(4096); }
        Fixture(int capacity) {
            registry = new ServerCapabilityRegistry(thread, (who, what) -> allowed.get(), capacity);
            view = registry.bind(scopes.openSession(caller.playerId()), caller);
        }
        RegistrationHandle register(CapabilityHandler handler) { return registry.register(provider, descriptor(), handler); }
        CapabilityReference reference() { return view.find(ID, 1).orElseThrow(); }
        CapabilityResult invoke(CapabilityReference ref, UUID task) {
            return view.invoke(ref, new CapabilityRequest(task, Map.of())).toCompletableFuture().join();
        }
        @Override public void close() { registry.close(); scopes.close(); }
    }
}
