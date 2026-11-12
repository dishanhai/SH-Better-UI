// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext.network;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncClient.Status;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.*;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.Number;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RichSyncStateTest {
    private static final UiId DOCUMENT = UiId.parse("demo:device");
    private static final UiId ENERGY = UiId.parse("demo:energy");
    private static final UiId DETAIL = UiId.parse("demo:detail");
    private static final Object PLAYER = new Object();
    private static final long SECOND = 1_000_000_000L;

    @Test
    void verifiesReadAuthorizationBeforeAccessAndClearsRevokedValues() {
        var rig = new Rig();
        rig.canRead = false;
        var denied = rig.open();
        assertEquals(Status.DENIED, denied.view().status());
        assertEquals(0, rig.reads);
        denied.close();
        rig.canRead = true;
        var open = rig.open();
        assertEquals(Status.LIVE, open.view().status());
        assertEquals(new Number(10), open.view().authoritative().get(ENERGY));
        rig.canRead = false;
        rig.refresh();
        rig.deliverResponses();
        assertEquals(Status.DENIED, open.view().status());
        assertTrue(open.view().displayed().isEmpty());
        assertFalse(open.edit(ENERGY, new Number(11), true));
        assertEquals(1, rig.reads);
    }

    @Test
    void correctsPredictionAndReplaysOnlyTheExactCachedReply() {
        var rig = new Rig();
        var open = rig.open();
        assertTrue(open.edit(ENERGY, new Number(99), true));
        assertFalse(open.edit(ENERGY, new Number(60), false));
        assertTrue(open.view().pending());
        assertEquals(new Number(10), open.view().authoritative().get(ENERGY));
        assertEquals(new Number(99), open.view().displayed().get(ENERGY));
        var request = (Edit) rig.requests.remove();
        rig.server.receive(new Object(), PLAYER, request, rig.responses::add);
        rig.server.receive(rig.connection, PLAYER, new Edit(UUID.randomUUID(), request.watchId(), request.requestId(),
                request.revision(), request.key(), request.value()), rig.responses::add);
        assertEquals(0, rig.writes);
        rig.receive(request);
        assertEquals(1, rig.writes);
        rig.client.receive(assertInstanceOf(Patch.class, rig.responses.remove()));
        var reply = assertInstanceOf(Result.class, rig.responses.remove());
        rig.client.receive(reply);
        assertEquals(new Number(80), open.view().displayed().get(ENERGY));
        assertEquals(Outcome.ACCEPTED, open.view().lastOutcome());
        assertFalse(open.view().pending());
        rig.receive(request);
        assertEquals(reply, rig.responses.remove());
        rig.receive(new Edit(request.session(), request.watchId(), request.requestId(), 2, ENERGY, new Number(0)));
        assertTrue(rig.responses.isEmpty());
        assertEquals(1, rig.writes);
    }

    @Test
    void rechecksWritePermissionAndRevisionAndRejectsUnknownFieldsAndTypes() {
        var rig = new Rig();
        var open = rig.open();
        rig.canWrite = false;
        assertTrue(open.edit(ENERGY, new Number(12), true));
        rig.flush();
        assertEquals(Outcome.DENIED, open.view().lastOutcome());
        assertEquals(new Number(10), open.view().displayed().get(ENERGY));
        assertEquals(0, rig.writes);
        rig.canWrite = true;
        rig.energy = 15;
        assertTrue(open.edit(ENERGY, new Number(5), false));
        var stale = (Edit) rig.requests.remove();
        rig.receive(stale);
        rig.deliverResponses();
        assertEquals(Outcome.STALE, open.view().lastOutcome());
        assertEquals(new Number(15), open.view().displayed().get(ENERGY));
        rig.receive(new Edit(stale.session(), stale.watchId(), 3, 2, ENERGY, new Text("wrong type")));
        assertEquals(Outcome.INVALID, assertInstanceOf(Result.class, rig.responses.remove()).outcome());
        rig.receive(new Edit(stale.session(), stale.watchId(), 4, 2, DETAIL, new Text("unknown key")));
        assertEquals(Outcome.INVALID, assertInstanceOf(Result.class, rig.responses.remove()).outcome());
        assertEquals(0, rig.writes);
    }

    @Test
    void ignoresDuplicatesAndRecoversGapsWithoutApplyingPartialOrOldSnapshots() {
        var rig = new Rig();
        var open = rig.open();
        rig.energy = 25;
        rig.refresh();
        var missing = assertInstanceOf(Patch.class, rig.responses.remove());
        rig.energy = 40;
        rig.detail = "就緒";
        rig.refresh();
        var later = assertInstanceOf(Patch.class, rig.responses.remove());
        assertEquals(2, later.changed().size());
        rig.client.receive(later);
        assertEquals(Status.SYNCING, open.view().status());
        assertEquals(1, open.view().revision());
        rig.client.receive(missing);
        rig.client.receive(new Snapshot(missing.session(), missing.watchId(), 2, true, missing.changed()));
        assertEquals(1, open.view().revision());
        assertEquals(1, rig.requests.size());
        rig.flush();
        assertEquals(Status.LIVE, open.view().status());
        assertEquals(3, open.view().revision());
        assertEquals(Map.of(ENERGY, new Number(40), DETAIL, new Text("就緒")), open.view().displayed());
        rig.client.receive(later);
        assertEquals(3, open.view().revision());
        assertTrue(rig.requests.isEmpty());
        rig.detail = null;
        rig.energy = 50;
        rig.refresh();
        assertEquals(Set.of(DETAIL), assertInstanceOf(Patch.class, rig.responses.peek()).removed());
        rig.deliverResponses();
        assertEquals(Map.of(ENERGY, new Number(50)), open.view().displayed());
    }

    @Test
    void discardsAnEditWhenResultIsLostAndOnlyRequestsAStateRefresh() {
        var rig = new Rig();
        var open = rig.open();
        assertTrue(open.edit(ENERGY, new Number(99), true));
        rig.receive(rig.requests.remove());
        rig.responses.clear();
        rig.now.addAndGet(2 * SECOND);
        rig.client.tick();
        assertInstanceOf(Resync.class, rig.requests.peek());
        assertFalse(open.view().pending());
        assertEquals(Status.SYNCING, open.view().status());
        rig.flush();
        assertEquals(Status.LIVE, open.view().status());
        assertEquals(Outcome.TIMED_OUT, open.view().lastOutcome());
        assertEquals(new Number(80), open.view().displayed().get(ENERGY));
        assertEquals(1, rig.writes);
    }

    @Test
    void doesNotConfuseFastCloseReopenOrReconnectWithOldDelivery() {
        var rig = new Rig();
        rig.connect();
        var first = rig.client.watch(DOCUMENT, view -> {});
        rig.receive(rig.requests.remove());
        var late = assertInstanceOf(Snapshot.class, rig.responses.remove());
        first.close();
        var current = rig.client.watch(DOCUMENT, view -> {});
        rig.client.receive(late);
        assertEquals(Status.SYNCING, current.view().status());
        rig.flush();
        assertEquals(Status.LIVE, current.view().status());
        current.edit(ENERGY, new Number(45), true);
        var oldEdit = rig.requests.remove();
        rig.client.disconnect();
        rig.server.disconnect(rig.connection);
        assertEquals(Status.OFFLINE, current.view().status());
        assertEquals(new Number(10), current.view().displayed().get(ENERGY));
        assertFalse(current.view().pending());
        rig.connection = new Object();
        rig.energy = 55;
        rig.client.connect(rig.requests::add);
        assertEquals(Status.CONNECTING, current.view().status());
        assertTrue(current.view().displayed().isEmpty());
        rig.client.receive(late);
        rig.flush();
        assertEquals(Status.LIVE, current.view().status());
        assertEquals(new Number(55), current.view().displayed().get(ENERGY));
        rig.receive(oldEdit);
        assertEquals(0, rig.writes);
    }

    @Test
    void neverReadsOrAppliesEditsAfterUnsubscribeOrReplacedRegistration() {
        var rig = new Rig();
        var open = rig.open();
        open.close();
        rig.flush();
        assertFalse(rig.server.refresh(rig.connection, PLAYER, rig.responses::add));
        assertEquals(1, rig.reads);
        var next = rig.open();
        rig.registration.close();
        var registration = rig.server.register(DOCUMENT, rig.access);
        rig.registration.close();
        rig.refresh();
        rig.deliverResponses();
        assertEquals(Status.DENIED, next.view().status());
        assertEquals(2, rig.reads);
        next.close();
        assertEquals(Status.LIVE, rig.open().view().status());
        registration.close();
    }

    @Test
    void rateLimitsARequestStormAndDoesNotReapplyARateLimitedIDLater() {
        var rig = new Rig();
        var open = rig.open();
        open.edit(ENERGY, new Number(10), false);
        var first = (Edit) rig.requests.remove();
        Edit last = first;
        for (int index = 1; index <= 32; index++) {
            last = new Edit(first.session(), first.watchId(), index, 1, ENERGY, new Number(10));
            rig.receive(last);
        }
        assertEquals(8, rig.writes);
        var limited = assertInstanceOf(Result.class, rig.responses.getLast());
        assertEquals(Outcome.RATE_LIMITED, limited.outcome());
        assertEquals(31, limited.requestId());
        var dropped = last;
        rig.now.addAndGet(SECOND);
        rig.responses.clear();
        rig.receive(new Edit(first.session(), first.watchId(), limited.requestId(), 1, ENERGY, new Number(10)));
        assertTrue(rig.responses.isEmpty());
        assertEquals(8, rig.writes);
        rig.receive(dropped);
        assertEquals(8, rig.writes);
        assertTrue(rig.responses.isEmpty());
        for (int index = 0; index < 10_000; index++) {
            rig.receive(new Edit(first.session(), first.watchId(), 33 + index, 1, ENERGY, new Number(10)));
        }
        assertEquals(16, rig.writes);
        assertEquals(30, rig.responses.size());
    }

    @Test
    void negotiatesReadOnlyAndRejectsIncompatibleVersions() {
        var rig = new Rig(RichSyncProtocol.DELTAS);
        var unknown = new Hello(UUID.randomUUID(), 2, 3, RichSyncProtocol.CAPABILITIES);
        rig.receive(unknown);
        assertEquals(new Unsupported(unknown.nonce()), rig.responses.remove());
        var open = rig.open();
        assertEquals(Status.LIVE, open.view().status());
        assertFalse(open.edit(ENERGY, new Number(50), true));
        assertFalse(open.view().writable());
        var newConnection = new Object();
        var hello = new Hello(UUID.randomUUID(), 1, 1, RichSyncProtocol.DELTAS);
        rig.server.receive(newConnection, PLAYER, hello, rig.responses::add);
        var welcome = assertInstanceOf(Welcome.class, rig.responses.remove());
        rig.server.receive(newConnection, PLAYER, new Subscribe(welcome.session(), 1, DOCUMENT), rig.responses::add);
        assertFalse(assertInstanceOf(Snapshot.class, rig.responses.remove()).writable());
        rig.server.receive(newConnection, PLAYER,
                new Edit(welcome.session(), 1, 1, 1, ENERGY, new Number(80)), rig.responses::add);
        assertEquals(Outcome.DENIED, assertInstanceOf(Result.class, rig.responses.remove()).outcome());
        assertEquals(0, rig.writes);
    }

    @Test
    void boundsUnsupportedHellosAndRechecksReadAccessBeforeReplyReplay() {
        var rig = new Rig();
        for (int index = 0; index < 1_000; index++) {
            rig.receive(new Hello(new UUID(1, index), 2, 3, RichSyncProtocol.CAPABILITIES));
        }
        assertEquals(4, rig.responses.size());
        assertTrue(rig.responses.stream().allMatch(Unsupported.class::isInstance));
        rig.responses.clear();
        rig.now.addAndGet(SECOND);
        var open = rig.open();
        assertTrue(open.edit(ENERGY, new Number(50), false));
        var edit = (Edit) rig.requests.remove();
        rig.receive(edit);
        rig.deliverResponses();
        rig.canRead = false;
        rig.receive(edit);
        assertEquals(Outcome.DENIED, assertInstanceOf(Failure.class, rig.responses.peek()).outcome());
        rig.deliverResponses();
        assertEquals(Status.DENIED, open.view().status());
        assertEquals(1, rig.writes);
    }

    @Test
    void boundsHandshakeAndSnapshotRetriesAndTearsDownExpiredWatch() {
        var rig = new Rig();
        rig.client.connect(rig.requests::add);
        var open = rig.client.watch(DOCUMENT, view -> {});
        for (int index = 0; index < 5; index++) {
            rig.now.addAndGet(2 * SECOND);
            rig.client.tick();
        }
        assertEquals(3, rig.requests.size());
        assertEquals(1, rig.requests.stream().distinct().count());
        assertEquals(Status.UNAVAILABLE, open.view().status());
        rig.requests.clear();
        rig.client.disconnect();
        rig.connect();
        rig.receive(rig.requests.remove());
        assertInstanceOf(Snapshot.class, rig.responses.remove());
        for (int index = 0; index < 5; index++) {
            rig.now.addAndGet(2 * SECOND);
            rig.client.tick();
        }
        assertEquals(3, rig.requests.size());
        assertInstanceOf(Unsubscribe.class, rig.requests.getLast());
        rig.receive(rig.requests.removeLast());
        rig.flush();
        assertEquals(Status.UNAVAILABLE, open.view().status());
        assertFalse(rig.server.refresh(rig.connection, PLAYER, rig.responses::add));
    }

    @Test
    void rejectsAnInvalidResultingMapAndRepairsWithAFullSnapshot() {
        var rig = new Rig();
        var open = rig.open();
        open.edit(ENERGY, new Number(99), false);
        var edit = (Edit) rig.requests.remove();
        rig.client.receive(new Patch(edit.session(), edit.watchId(), 1, 2,
                Map.of(ENERGY, new Number(80)), Set.of(DETAIL)));
        assertEquals(Status.SYNCING, open.view().status());
        assertEquals(Map.of(ENERGY, new Number(10)), open.view().authoritative());
        rig.receive(edit);
        rig.deliverResponses();
        rig.flush();
        assertEquals(Status.LIVE, open.view().status());
        assertEquals(Map.of(ENERGY, new Number(80)), open.view().authoritative());
    }

    @Test
    void revokesOnProviderFailureAndDoesNotExposeTheException() {
        var rig = new Rig();
        var open = rig.open();
        rig.readFailure = new IllegalStateException("server private");
        rig.refresh();
        assertEquals(new Failure(assertInstanceOf(Failure.class, rig.responses.peek()).session(),
                1, Outcome.FAILED), rig.responses.peek());
        rig.deliverResponses();
        assertEquals(Status.UNAVAILABLE, open.view().status());
        assertEquals(Outcome.FAILED, open.view().lastOutcome());
        assertTrue(open.view().authoritative().isEmpty());
        assertEquals(1, rig.failures.size());
    }

    private static final class Rig {
        private final AtomicLong now = new AtomicLong();
        private final ArrayDeque<ClientMessage> requests = new ArrayDeque<>();
        private final ArrayDeque<ServerMessage> responses = new ArrayDeque<>();
        private final ArrayList<RuntimeException> failures = new ArrayList<>();
        private final RichSyncServer<Object> server = new RichSyncServer<>(failures::add, now::get);
        private final RichSyncClient client;
        private final RichSyncServer.Access<Object> access;
        private final RichSyncServer.Registration registration;
        private Object connection = new Object();
        private boolean connected;
        private boolean canRead = true;
        private boolean canWrite = true;
        private long energy = 10;
        private String detail;
        private int reads;
        private int writes;
        private RuntimeException readFailure;

        private Rig() { this(RichSyncProtocol.CAPABILITIES); }

        private Rig(int capabilities) {
            client = new RichSyncClient(now::get, capabilities);
            access = new RichSyncServer.Access<>(player -> canRead && player == PLAYER, player -> {
                reads++;
                if (readFailure != null) throw readFailure;
                if (detail == null) return Map.of(ENERGY, new Number(energy));
                return Map.of(ENERGY, new Number(energy), DETAIL, new Text(detail));
            }, (player, edit) -> {
                if (!canWrite || player != PLAYER || !edit.key().equals(ENERGY)) return false;
                writes++;
                energy = Math.min(80, Math.max(0, ((Number) edit.value()).number()));
                return true;
            });
            registration = server.register(DOCUMENT, access);
        }

        private void connect() {
            client.connect(requests::add);
            receive(requests.remove());
            client.receive(responses.remove());
            connected = true;
        }

        private RichSyncClient.Watch open() {
            if (!connected) connect();
            var watch = client.watch(DOCUMENT, view -> {});
            flush();
            return watch;
        }

        private void refresh() {
            now.addAndGet(SECOND / 10);
            server.refresh(connection, PLAYER, responses::add);
        }

        private void receive(ClientMessage request) {
            server.receive(connection, PLAYER, request, responses::add);
        }

        private void deliverResponses() {
            while (!responses.isEmpty()) client.receive(responses.remove());
        }

        private void flush() {
            while (!requests.isEmpty() || !responses.isEmpty()) {
                while (!requests.isEmpty()) receive(requests.remove());
                deliverResponses();
            }
        }
    }
}
