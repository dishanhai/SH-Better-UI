// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext.network;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.*;
import com.dishanhai.sh_better_ui.kernel.api.richtext.network.RichSyncProtocol.Number;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RichSyncWireTest {
    private static final UUID NONCE = new UUID(1, 2);
    private static final UUID SESSION = new UUID(3, 4);
    private static final UiId ENERGY = UiId.parse("demo:energy");
    private static final UiId OLD = UiId.parse("demo:old");

    @Test
    void allDirectionsRoundTripAndRejectEveryTruncatedPrefix() throws IOException {
        var values = Map.<UiId, Value>of(ENERGY, new Text("能源🦊"), OLD, new Number(Long.MIN_VALUE));
        for (ClientMessage message : List.of(new Hello(NONCE, 1, 2, 255), new Subscribe(SESSION, 2, ENERGY),
                new Resync(SESSION, 2), new Edit(SESSION, 2, 1, 3, ENERGY, new Number(Long.MAX_VALUE)),
                new Unsubscribe(SESSION, 2))) {
            byte[] bytes = RichSyncWire.encode(message);
            assertEquals(message, RichSyncWire.decodeClient(bytes));
            for (int length = 0; length < bytes.length; length++) {
                byte[] truncated = Arrays.copyOf(bytes, length);
                assertThrows(IOException.class, () -> RichSyncWire.decodeClient(truncated));
            }
            assertThrows(IOException.class, () -> RichSyncWire.decodeClient(Arrays.copyOf(bytes, bytes.length + 1)));
        }
        for (ServerMessage message : List.of(new Welcome(NONCE, SESSION, 1, RichSyncProtocol.CAPABILITIES),
                new Unsupported(NONCE), new Snapshot(SESSION, 2, 1, true, values),
                new Patch(SESSION, 2, 1, 2, Map.of(ENERGY, new Number(8)), Set.of(OLD)),
                new Result(SESSION, 2, 1, 2, Outcome.ACCEPTED), new Failure(SESSION, 2, Outcome.DENIED))) {
            byte[] bytes = RichSyncWire.encode(message);
            assertEquals(message, RichSyncWire.decodeServer(bytes));
            for (int length = 0; length < bytes.length; length++) {
                byte[] truncated = Arrays.copyOf(bytes, length);
                assertThrows(IOException.class, () -> RichSyncWire.decodeServer(truncated));
            }
            assertThrows(IOException.class, () -> RichSyncWire.decodeServer(Arrays.copyOf(bytes, bytes.length + 1)));
        }
    }

    @Test
    void rejectsInvalidWireAndUnsafeText() {
        assertThrows(IOException.class, () -> RichSyncWire.decodeClient(new byte[RichSyncWire.MAX_CLIENT_BYTES + 1]));
        assertThrows(IOException.class, () -> RichSyncWire.decodeServer(new byte[RichSyncWire.MAX_SERVER_BYTES + 1]));
        byte[] edit = RichSyncWire.encode(new Edit(SESSION, 2, 1, 3, ENERGY, new Text("z")));
        edit[edit.length - 1] = (byte) 0xFF;
        assertThrows(IOException.class, () -> RichSyncWire.decodeClient(edit));
        byte[] snapshot = RichSyncWire.encode(new Snapshot(SESSION, 2, 1, false, Map.of()));
        snapshot[snapshot.length - 2] = 2;
        assertThrows(IOException.class, () -> RichSyncWire.decodeServer(snapshot));
        snapshot[0]++;
        assertThrows(IOException.class, () -> RichSyncWire.decodeServer(snapshot));
        assertThrows(IllegalArgumentException.class, () -> new Text("§kformat"));
        assertThrows(IllegalArgumentException.class, () -> new Text("new\nline"));
        assertThrows(IllegalArgumentException.class, () -> new Text(String.valueOf((char) 0xD800)));
        assertThrows(IllegalArgumentException.class, () -> new Text("圖".repeat(171)));
        assertThrows(IllegalArgumentException.class, () ->
                new Subscribe(SESSION, 2, new UiId("demo", "x".repeat(128))));
        assertThrows(IllegalArgumentException.class, () -> new Result(SESSION, 2, 1, 2, Outcome.TIMED_OUT));
        assertThrows(IllegalArgumentException.class, () ->
                new Patch(SESSION, 2, 1, 2, Map.of(ENERGY, new Number(1)), Set.of(ENERGY)));
        assertThrows(IllegalArgumentException.class, () ->
                new Patch(SESSION, 2, 1, 3, Map.of(ENERGY, new Number(1)), Set.of()));
    }

    @Test
    void rejectsDuplicateFieldAndRemovalKeys() {
        UiId a = UiId.parse("demo:a");
        UiId b = UiId.parse("demo:b");
        byte[] duplicate = RichSyncWire.encode(new Snapshot(SESSION, 1, 1, false,
                Map.of(a, new Number(1), b, new Number(1))));
        duplicate[duplicate.length - 10] = 'a';
        assertThrows(IOException.class, () -> RichSyncWire.decodeServer(duplicate));
        byte[] removals = RichSyncWire.encode(new Patch(SESSION, 1, 1, 2, Map.of(), Set.of(a, b)));
        removals[removals.length - 1] = 'a';
        assertThrows(IOException.class, () -> RichSyncWire.decodeServer(removals));
    }

    @Test
    void deterministicEncodingAndWorstCasePayloadFitTheBudget() throws IOException {
        var values = new LinkedHashMap<UiId, Value>();
        var reverse = new LinkedHashMap<UiId, Value>();
        var removed = new HashSet<UiId>();
        for (int index = 0; index < 32; index++) {
            values.put(key(0, index), new Text("狐".repeat(170) + "ab"));
            reverse.put(key(0, 31 - index), new Text("狐".repeat(170) + "ab"));
            removed.add(key(1, index));
        }
        var full = new Snapshot(SESSION, 2, 1, false, values);
        var patch = new Patch(SESSION, 2, 1, 2, reverse, removed);
        assertArrayEquals(RichSyncWire.encode(full), RichSyncWire.encode(new Snapshot(SESSION, 2, 1, false, reverse)));
        assertEquals(20_676, RichSyncWire.encode(full).length);
        assertEquals(24_844, RichSyncWire.encode(patch).length);
        assertEquals(patch, RichSyncWire.decodeServer(RichSyncWire.encode(patch)));
        var delta = new Patch(SESSION, 2, 2, 3, Map.of(key(0, 0), new Number(25)), Set.of());
        assertEquals(183, RichSyncWire.encode(delta).length);
        assertThrows(UnsupportedOperationException.class, () -> full.values().clear());
        var oversize = new HashMap<>(values);
        oversize.put(key(2, 0), new Number(1));
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(SESSION, 2, 1, false, oversize));
    }

    @Test
    void malformedByteFuzzRejectsOrProducesAnExactCanonicalRoundTrip() throws IOException {
        var random = new Random(0x5348425549L);
        byte[] validClient = RichSyncWire.encode(new Edit(SESSION, 1, 1, 1, ENERGY, new Text("資料")));
        byte[] validServer = RichSyncWire.encode(new Patch(SESSION, 1, 1, 2,
                Map.of(ENERGY, new Number(3)), Set.of(OLD)));
        for (int index = 0; index < 10_000; index++) {
            byte[] bytes = new byte[random.nextInt(257)];
            random.nextBytes(bytes);
            if (index % 2 == 0 && bytes.length > 1) bytes[0] = RichSyncWire.VERSION;
            checkClient(bytes);
            checkServer(bytes);
            checkClient(mutate(random, validClient));
            checkServer(mutate(random, validServer));
        }
    }

    private static byte[] mutate(Random random, byte[] valid) {
        byte[] changed = valid.clone();
        changed[random.nextInt(changed.length)] ^= (byte) (1 << random.nextInt(8));
        return changed;
    }

    private static void checkClient(byte[] bytes) throws IOException {
        ClientMessage message;
        try {
            message = RichSyncWire.decodeClient(bytes);
        } catch (IOException malformed) {
            return;
        }
        assertEquals(message, RichSyncWire.decodeClient(RichSyncWire.encode(message)));
    }

    private static void checkServer(byte[] bytes) throws IOException {
        ServerMessage message;
        try {
            message = RichSyncWire.decodeServer(bytes);
        } catch (IOException malformed) {
            return;
        }
        assertEquals(message, RichSyncWire.decodeServer(RichSyncWire.encode(message)));
    }

    private static UiId key(int group, int index) {
        return new UiId("demo", "x".repeat(119) + group + "_" + (index < 10 ? "0" : "") + index);
    }
}
