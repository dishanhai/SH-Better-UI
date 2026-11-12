// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext.network;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import static com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.DELTAS;
import static com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.EDITS;

/**
 * Client owner-thread state. The native bridge must enqueue receive calls on the owner thread at a safe update point
 * and drop deliveries from connections other than the one currently attached.
 */
public final class RichSyncClient {
    private static final int MAX_ATTEMPTS = 3;
    private static final long RESPONSE_NANOS = 2_000_000_000L;

    public enum Status { OFFLINE, CONNECTING, SYNCING, LIVE, DENIED, UNSUPPORTED, UNAVAILABLE }

    public record View(Status status, long revision, Map<UiId, Value> authoritative, Map<UiId, Value> displayed,
                       boolean writable, boolean pending, Outcome lastOutcome) {
        public View {
            Objects.requireNonNull(status, "status");
            authoritative = Map.copyOf(authoritative);
            displayed = Map.copyOf(displayed);
        }
    }

    private final LongSupplier clock;
    private final int capabilities;
    private Consumer<ClientMessage> send;
    private Hello hello;
    private int attempts;
    private long deadline;
    private UUID session;
    private int negotiated;
    private Status link = Status.OFFLINE;
    private long lastWatchId;
    private Watch watch;

    public RichSyncClient() {
        this(RichSyncProtocol.CAPABILITIES);
    }

    /** Selects local capability flags, e.g. DELTAS alone for a read-only consumer. */
    public RichSyncClient(int capabilities) {
        this(System::nanoTime, capabilities);
    }

    RichSyncClient(LongSupplier clock, int capabilities) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if ((capabilities & DELTAS) == 0 || (capabilities & ~RichSyncProtocol.CAPABILITIES) != 0) {
            throw new IllegalArgumentException("Unsupported local rich text capabilities");
        }
        this.capabilities = capabilities;
    }

    /** Opens a fresh negotiated link; the previous native link must have been disconnected first. */
    public void connect(Consumer<ClientMessage> transport) {
        if (send != null) throw new IllegalStateException("Disconnect the existing transport first");
        send = Objects.requireNonNull(transport, "transport");
        hello = new Hello(UUID.randomUUID(), RichSyncProtocol.VERSION, RichSyncProtocol.VERSION, capabilities);
        attempts = 0;
        link = Status.CONNECTING;
        if (watch != null) watch.reset(link, true);
        if (link == Status.CONNECTING && send != null && hello != null) transmitHello();
    }

    /** Retains the last authorized snapshot for the open watch as read-only offline content. */
    public void disconnect() {
        send = null;
        hello = null;
        session = null;
        negotiated = 0;
        lastWatchId = 0;
        link = Status.OFFLINE;
        if (watch != null) watch.reset(link, false);
    }

    /** Opens the single watch. Close it with the owning screen or scope. */
    public Watch watch(UiId document, Consumer<View> onChange) {
        if (watch != null) throw new IllegalStateException("Only one rich text document can be watched at a time");
        RichSyncProtocol.checkId(document);
        var attached = new Watch(document, Objects.requireNonNull(onChange, "onChange"));
        watch = attached;
        if (session != null) attached.start();
        else attached.publish(link);
        return attached;
    }

    public void receive(ServerMessage message) {
        Objects.requireNonNull(message, "message");
        if (message instanceof Welcome welcome) {
            acceptWelcome(welcome);
        } else if (message instanceof Unsupported unsupported) {
            if (hello != null && hello.nonce().equals(unsupported.nonce())) rejectLink(Status.UNSUPPORTED);
        } else if (message instanceof WatchedResponse response && watch != null
                && response.watchId() == watch.id && response.session().equals(session)) {
            watch.accept(response);
        }
    }

    /** Advances bounded handshake/snapshot retries and edit timeouts. Edits are never retried automatically. */
    public void tick() {
        long now = clock.getAsLong();
        if (hello != null && now - deadline >= 0) {
            if (attempts < MAX_ATTEMPTS) transmitHello();
            else rejectLink(Status.UNAVAILABLE);
        }
        if (watch != null) watch.tick(now);
    }

    private void transmitHello() {
        attempts++;
        deadline = clock.getAsLong() + RESPONSE_NANOS;
        send.accept(hello);
    }

    private void acceptWelcome(Welcome welcome) {
        if (hello == null || !hello.nonce().equals(welcome.nonce())) return;
        if (welcome.version() != RichSyncProtocol.VERSION || (welcome.capabilities() & ~capabilities) != 0) {
            rejectLink(Status.UNSUPPORTED);
            return;
        }
        session = welcome.session();
        negotiated = welcome.capabilities();
        hello = null;
        link = Status.SYNCING;
        if (watch != null) watch.start();
    }

    private void rejectLink(Status status) {
        hello = null;
        link = status;
        if (watch != null) watch.reset(status, true);
    }

    public final class Watch implements AutoCloseable {
        private final UiId document;
        private final Consumer<View> onChange;
        private View view;
        private long id;
        private long revision;
        private Map<UiId, Value> values = Map.of();
        private boolean writable;
        private Pending pending;
        private Outcome lastOutcome;
        private long lastRequest;
        private long minimumRevision;
        private int syncAttempts;
        private long syncDeadline;

        private Watch(UiId document, Consumer<View> onChange) {
            this.document = document;
            this.onChange = onChange;
        }

        public View view() { return view; }

        /** Proposes a change to a known same-type field. Only one request may be pending. */
        public boolean edit(UiId key, Value value, boolean predict) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (watch != this || !view.writable() || pending != null) return false;
            Value current = values.get(key);
            if (current == null || current.getClass() != value.getClass()) return false;
            var request = new Edit(session, id, ++lastRequest, revision, key, value);
            pending = new Pending(request, predict, clock.getAsLong() + RESPONSE_NANOS);
            lastOutcome = null;
            publish(Status.LIVE);
            if (watch == this && pending != null && pending.edit() == request) send.accept(request);
            return true;
        }

        @Override
        public void close() {
            if (watch != this) return;
            watch = null;
            long closingId = id;
            id = 0;
            pending = null;
            values = Map.of();
            view = new View(Status.OFFLINE, 0, values, values, false, false, null);
            if (session != null && closingId != 0) send.accept(new Unsubscribe(session, closingId));
        }

        private void start() {
            id = ++lastWatchId;
            revision = 0;
            values = Map.of();
            writable = false;
            lastRequest = 0;
            lastOutcome = null;
            pending = null;
            minimumRevision = 0;
            syncAttempts = 0;
            requestSnapshot();
        }

        private void reset(Status status, boolean clear) {
            id = 0;
            writable = false;
            pending = null;
            if (clear) {
                revision = 0;
                values = Map.of();
                lastOutcome = null;
            }
            publish(status);
        }

        private void accept(WatchedResponse response) {
            if (response instanceof Failure failure) {
                revision = 0;
                values = Map.of();
                pending = null;
                writable = false;
                id = 0;
                lastOutcome = failure.outcome();
                publish(failure.outcome() == Outcome.DENIED ? Status.DENIED : Status.UNAVAILABLE);
            } else if (response instanceof Snapshot snapshot) {
                acceptSnapshot(snapshot);
            } else if (response instanceof Patch patch) {
                acceptPatch(patch);
            } else if (response instanceof Result result) {
                acceptResult(result);
            }
        }

        private void acceptSnapshot(Snapshot snapshot) {
            if (snapshot.revision() < Math.max(revision, minimumRevision)
                    || (snapshot.revision() == revision && view.status() == Status.LIVE)) return;
            revision = snapshot.revision();
            minimumRevision = 0;
            values = snapshot.values();
            writable = snapshot.writable() && (negotiated & EDITS) != 0;
            publish(Status.LIVE);
        }

        private void acceptPatch(Patch patch) {
            if (patch.revision() <= revision) return;
            if (view.status() == Status.SYNCING) {
                minimumRevision = Math.max(minimumRevision, patch.revision());
                return;
            }
            if (patch.baseRevision() != revision || !values.keySet().containsAll(patch.removed())) {
                resync(patch.revision());
                return;
            }
            var updated = new HashMap<>(values);
            patch.removed().forEach(updated::remove);
            updated.putAll(patch.changed());
            if (updated.size() > RichSyncProtocol.MAX_FIELDS) {
                resync(patch.revision());
                return;
            }
            values = RichSyncProtocol.copyValues(updated);
            revision = patch.revision();
            publish(Status.LIVE);
        }

        private void acceptResult(Result result) {
            if (result.requestId() != lastRequest) return;
            if (pending == null) {
                if (result.revision() > revision) resync(result.revision());
                return;
            }
            pending = null;
            lastOutcome = result.outcome();
            if (result.revision() > revision) resync(result.revision());
            else publish(view.status());
        }

        private void tick(long now) {
            if (pending != null && now - pending.deadline() >= 0) {
                pending = null;
                lastOutcome = Outcome.TIMED_OUT;
                resync(revision);
            }
            if (watch == this && view.status() == Status.SYNCING && now - syncDeadline >= 0) {
                if (syncAttempts < MAX_ATTEMPTS) requestSnapshot();
                else {
                    long expired = id;
                    var epoch = session;
                    id = 0;
                    revision = 0;
                    values = Map.of();
                    pending = null;
                    writable = false;
                    lastOutcome = Outcome.TIMED_OUT;
                    publish(Status.UNAVAILABLE);
                    if (send != null && epoch.equals(session)) send.accept(new Unsubscribe(epoch, expired));
                }
            }
        }

        private void resync(long requiredRevision) {
            minimumRevision = Math.max(minimumRevision, requiredRevision);
            if (view.status() == Status.SYNCING) {
                publish(Status.SYNCING);
                return;
            }
            syncAttempts = 0;
            requestSnapshot();
        }

        private void requestSnapshot() {
            WatchedRequest request = revision == 0 ? new Subscribe(session, id, document) : new Resync(session, id);
            syncAttempts++;
            syncDeadline = clock.getAsLong() + RESPONSE_NANOS;
            publish(Status.SYNCING);
            if (watch == this && id == request.watchId() && request.session().equals(session)) send.accept(request);
        }

        private void publish(Status status) {
            Map<UiId, Value> display = values;
            if (pending != null && pending.predict() && values.containsKey(pending.edit().key())) {
                display = new HashMap<>(values);
                display.put(pending.edit().key(), pending.edit().value());
            }
            var next = new View(status, revision, values, display, writable && status == Status.LIVE,
                    pending != null, lastOutcome);
            if (next.equals(view)) return;
            view = next;
            onChange.accept(next);
        }
    }

    private record Pending(Edit edit, boolean predict, long deadline) {}
}
