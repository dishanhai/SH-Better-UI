// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext.network;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import static com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.DELTAS;
import static com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.EDITS;

/**
 * A server-thread-owned registry and authority for authenticated play connections.
 * The transport must supply the actual connection identity and current sender from the loader.
 */
public final class RichSyncServer<P> {
    public static final int MAX_DOCUMENTS = 128;
    public static final int MAX_CONNECTIONS = 1024;
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final long REFRESH_INTERVAL_NANOS = 100_000_000L;
    private static final int MAX_HELLOS_PER_WINDOW = 4;
    private static final int MAX_REQUESTS_PER_WINDOW = 32;
    private static final int MAX_CONTROLS_PER_WINDOW = 8;
    private static final int MAX_EDITS_PER_WINDOW = 8;

    /** The owner supplies an atomic edit predicate that validates current game rules and write authorization. */
    public record Access<P>(Predicate<P> canRead, Function<P, Map<UiId, Value>> read, BiPredicate<P, Edit> edit) {
        public Access {
            Objects.requireNonNull(canRead, "canRead");
            Objects.requireNonNull(read, "read");
        }
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    private final Map<UiId, Binding<P>> bindings = new HashMap<>();
    private final Map<Object, Session<P>> sessions = new IdentityHashMap<>();
    private final Consumer<RuntimeException> onFailure;
    private final LongSupplier clock;

    public RichSyncServer(Consumer<RuntimeException> onFailure) {
        this(onFailure, System::nanoTime);
    }

    RichSyncServer(Consumer<RuntimeException> onFailure, LongSupplier clock) {
        this.onFailure = Objects.requireNonNull(onFailure, "onFailure");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Registers a document until this lease closes or the server stops. */
    public Registration register(UiId document, Access<P> access) {
        RichSyncProtocol.checkId(document);
        var binding = new Binding<>(Objects.requireNonNull(access, "access"));
        if (bindings.size() >= MAX_DOCUMENTS || bindings.putIfAbsent(document, binding) != null) {
            throw new IllegalStateException("Document registry is full or ID is already registered");
        }
        return () -> {
            if (bindings.get(document) == binding) bindings.remove(document);
        };
    }

    public void receive(Object connection, P player, ClientMessage message, Consumer<ServerMessage> send) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(send, "send");
        long now = clock.getAsLong();
        if (message instanceof Hello hello) {
            negotiate(connection, hello, send, now);
            return;
        }
        var request = (WatchedRequest) message;
        var session = sessions.get(connection);
        if (session == null || !request.session().equals(session.epoch)) return;
        if (request instanceof Unsubscribe unsubscribe) {
            if (unsubscribe.watchId() >= session.latestWatch) {
                session.latestWatch = unsubscribe.watchId();
                session.watch = null;
                session.terminalFailure = null;
            }
            return;
        }
        if (!session.budget.accept(now)) {
            if (request instanceof Edit edit && edit.watchId() == session.latestWatch && session.watch != null
                    && edit.requestId() > session.watch.lastRequest) {
                rememberRequest(session.watch, edit);
            }
            return;
        }
        if (request instanceof Subscribe subscribe) {
            if (session.budget.allowControl()) subscribe(session, player, subscribe, send);
        } else if (request.watchId() == session.latestWatch) {
            var watch = session.watch;
            if (request instanceof Resync && session.budget.allowControl()) {
                if (watch != null) synchronize(session, watch, player, send, true);
                else resendFailure(session, send);
            } else if (request instanceof Edit edit && watch != null) {
                apply(session, watch, player, edit, send);
            }
        }
    }

    /** Rechecks the current reader and coalesces changed fields into one atomic delta, at most 10 times per second. */
    public boolean refresh(Object connection, P player, Consumer<ServerMessage> send) {
        var session = sessions.get(connection);
        if (session == null || session.watch == null) return false;
        var watch = session.watch;
        long now = clock.getAsLong();
        if (watch.refreshStarted && now - watch.lastRefresh < REFRESH_INTERVAL_NANOS) return false;
        watch.refreshStarted = true;
        watch.lastRefresh = now;
        return synchronize(session, watch, Objects.requireNonNull(player, "player"), send, false);
    }

    public void disconnect(Object connection) {
        var session = sessions.remove(connection);
        if (session != null) session.watch = null;
    }

    public void clear() {
        sessions.values().forEach(session -> session.watch = null);
        sessions.clear();
        bindings.clear();
    }

    private void negotiate(Object connection, Hello hello, Consumer<ServerMessage> send, long now) {
        var session = sessions.get(connection);
        if (session == null) {
            if (sessions.size() >= MAX_CONNECTIONS) return;
            session = new Session<>(now);
            sessions.put(connection, session);
        }
        if (!session.budget.allowHello(now)) return;
        if (session.hello != null) {
            if (session.hello.equals(hello)) send.accept(welcome(session));
            return;
        }
        if (hello.minVersion() > RichSyncProtocol.VERSION || hello.maxVersion() < RichSyncProtocol.VERSION
                || (hello.capabilities() & DELTAS) == 0) {
            send.accept(new Unsupported(hello.nonce()));
            return;
        }
        session.hello = hello;
        session.epoch = UUID.randomUUID();
        session.capabilities = hello.capabilities() & RichSyncProtocol.CAPABILITIES;
        send.accept(welcome(session));
    }

    private static Welcome welcome(Session<?> session) {
        return new Welcome(session.hello.nonce(), session.epoch, RichSyncProtocol.VERSION, session.capabilities);
    }

    private void subscribe(Session<P> session, P player, Subscribe request, Consumer<ServerMessage> send) {
        long watchId = request.watchId();
        if (watchId < session.latestWatch) return;
        if (watchId == session.latestWatch) {
            if (session.watch == null) resendFailure(session, send);
            else if (session.watch.document.equals(request.document())) synchronize(session, session.watch, player, send, true);
            return;
        }
        session.latestWatch = watchId;
        session.terminalFailure = null;
        var binding = bindings.get(request.document());
        if (binding == null) {
            fail(session, Outcome.DENIED, send);
            return;
        }
        var watch = new Subscription<>(request.document(), binding);
        session.watch = watch;
        synchronize(session, watch, player, send, true);
    }

    private boolean synchronize(Session<P> session, Subscription<P> watch, P player, Consumer<ServerMessage> send,
                                boolean full) {
        var values = read(session, watch, player, send);
        if (values == null) return false;
        long base = watch.revision;
        Map<UiId, Value> previous = watch.values;
        if (base == 0 || !values.equals(previous)) {
            watch.revision++;
            watch.values = values;
        }
        if (full) {
            send.accept(new Snapshot(session.epoch, session.latestWatch, watch.revision, writable(session, watch), values));
        } else if (watch.revision != base) {
            var changed = new HashMap<>(values);
            changed.entrySet().removeIf(field -> field.getValue().equals(previous.get(field.getKey())));
            var removed = new HashSet<>(previous.keySet());
            removed.removeAll(values.keySet());
            send.accept(new Patch(session.epoch, session.latestWatch, base, watch.revision, changed, removed));
        }
        return session.watch == watch;
    }

    private Map<UiId, Value> read(Session<P> session, Subscription<P> watch, P player, Consumer<ServerMessage> send) {
        if (!authorize(session, watch, player, send)) return null;
        Map<UiId, Value> values;
        try {
            values = RichSyncProtocol.copyValues(watch.binding.access().read().apply(player));
        } catch (RuntimeException error) {
            onFailure.accept(error);
            if (session.watch == watch) fail(session, Outcome.FAILED, send);
            return null;
        }
        if (session.watch != watch) return null;
        if (bindings.get(watch.document) == watch.binding) return values;
        fail(session, Outcome.DENIED, send);
        return null;
    }

    private boolean authorize(Session<P> session, Subscription<P> watch, P player, Consumer<ServerMessage> send) {
        boolean allowed = false;
        Outcome failure = Outcome.DENIED;
        try {
            allowed = bindings.get(watch.document) == watch.binding && watch.binding.access().canRead().test(player);
        } catch (RuntimeException error) {
            onFailure.accept(error);
            failure = Outcome.FAILED;
        }
        if (session.watch != watch) return false;
        if (allowed && bindings.get(watch.document) == watch.binding) return true;
        fail(session, failure, send);
        return false;
    }

    private void apply(Session<P> session, Subscription<P> watch, P player, Edit request, Consumer<ServerMessage> send) {
        if (request.requestId() < watch.lastRequest || watch.revision == 0) return;
        if (request.requestId() == watch.lastRequest) {
            if (request.equals(watch.request) && watch.reply != null && authorize(session, watch, player, send)) {
                send.accept(watch.reply);
            }
            return;
        }
        rememberRequest(watch, request);
        if (!session.budget.allowEdit()) {
            if (authorize(session, watch, player, send)) reply(session, watch, request, Outcome.RATE_LIMITED, send);
            return;
        }
        if (!synchronize(session, watch, player, send, false) || watch.request != request) return;
        Outcome outcome = edit(session, watch, player, request);
        if (session.watch == watch && synchronize(session, watch, player, send, false) && watch.request == request) {
            reply(session, watch, request, outcome, send);
        }
    }

    private static void rememberRequest(Subscription<?> watch, Edit request) {
        watch.lastRequest = request.requestId();
        watch.request = request;
        watch.reply = null;
    }

    private static void reply(Session<?> session, Subscription<?> watch, Edit request, Outcome outcome,
                              Consumer<ServerMessage> send) {
        var result = new Result(session.epoch, request.watchId(), request.requestId(), watch.revision, outcome);
        watch.reply = result;
        send.accept(result);
    }

    private Outcome edit(Session<P> session, Subscription<P> watch, P player, Edit request) {
        if (!writable(session, watch)) return Outcome.DENIED;
        if (watch.revision != request.revision()) return Outcome.STALE;
        var current = watch.values.get(request.key());
        if (current == null || current.getClass() != request.value().getClass()) return Outcome.INVALID;
        try {
            return watch.binding.access().edit().test(player, request) ? Outcome.ACCEPTED : Outcome.DENIED;
        } catch (RuntimeException error) {
            onFailure.accept(error);
            return Outcome.FAILED;
        }
    }

    private static boolean writable(Session<?> session, Subscription<?> watch) {
        return (session.capabilities & EDITS) != 0 && watch.binding.access().edit() != null;
    }

    private static void fail(Session<?> session, Outcome outcome, Consumer<ServerMessage> send) {
        session.watch = null;
        session.terminalFailure = outcome;
        resendFailure(session, send);
    }

    private static void resendFailure(Session<?> session, Consumer<ServerMessage> send) {
        if (session.terminalFailure != null) {
            send.accept(new Failure(session.epoch, session.latestWatch, session.terminalFailure));
        }
    }

    private static final class Budget {
        private long start;
        private int hellos;
        private int total;
        private int controls;
        private int edits;

        private Budget(long now) { start = now; }

        private boolean accept(long now) {
            expire(now);
            if (total == MAX_REQUESTS_PER_WINDOW) return false;
            total++;
            return true;
        }

        private boolean allowHello(long now) {
            expire(now);
            if (hellos == MAX_HELLOS_PER_WINDOW) return false;
            hellos++;
            return true;
        }

        private void expire(long now) {
            if (now - start < WINDOW_NANOS) return;
            start = now;
            hellos = total = controls = edits = 0;
        }

        private boolean allowControl() {
            if (controls == MAX_CONTROLS_PER_WINDOW) return false;
            controls++;
            return true;
        }

        private boolean allowEdit() {
            if (edits == MAX_EDITS_PER_WINDOW) return false;
            edits++;
            return true;
        }
    }

    private record Binding<P>(Access<P> access) {}

    private static final class Session<P> {
        private Hello hello;
        private UUID epoch;
        private int capabilities;
        private final Budget budget;
        private long latestWatch;
        private Subscription<P> watch;
        private Outcome terminalFailure;

        private Session(long now) {
            budget = new Budget(now);
        }
    }

    private static final class Subscription<P> {
        private final UiId document;
        private final Binding<P> binding;
        private Map<UiId, Value> values = Map.of();
        private long revision;
        private boolean refreshStarted;
        private long lastRefresh;
        private long lastRequest;
        private Edit request;
        private Result reply;

        private Subscription(UiId document, Binding<P> binding) {
            this.document = document;
            this.binding = binding;
        }
    }
}
