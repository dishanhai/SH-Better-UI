// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsDraftSessionTest {
    private SettingsDraftSession<String, Boolean> session() {
        return new SettingsDraftSession<>(Map.of("name", true, "health", false),
                Map.of("name", true, "health", true));
    }

    @Test void cancelDoesNotWriteAndPreservesParentChanges() {
        var parent = new LinkedHashMap<>(Map.of("name", true, "health", false, "world", false));
        var session = session();
        session.edit("name", false);
        parent.put("world", true);
        session.cancel();
        assertFalse(session.dirty());
        assertTrue(session.get("name"));
        assertTrue(parent.get("world"));
        assertTrue(parent.get("name"));
    }

    @Test void applySendsOnlyChangedKeysAndDoesNotReplayUnchangedValues() {
        var parent = new LinkedHashMap<>(Map.of("name", true, "health", false, "world", false));
        var session = session();
        session.edit("name", false);
        parent.put("health", true); // Independently updated since opening this child.
        parent.put("world", true);  // Another group has pending changes.
        assertTrue(session.apply(patch -> {
            assertEquals(Map.of("name", false), patch);
            assertThrows(UnsupportedOperationException.class, () -> patch.put("world", false));
            parent.putAll(patch);
        }));
        assertEquals(Map.of("name", false, "health", true, "world", true), parent);
        assertFalse(session.dirty());
        var calls = new AtomicInteger();
        assertFalse(session.apply(patch -> calls.incrementAndGet()));
        assertEquals(0, calls.get());
    }

    @Test void resetCanBeUndoneOrCancelled() {
        var session = session();
        session.edit("name", false);
        session.resetDefaults();
        assertEquals(Map.of("name", true, "health", true), session.snapshot());
        assertTrue(session.canUndoReset());
        assertTrue(session.undoReset());
        assertEquals(Map.of("name", false, "health", false), session.snapshot());
        assertFalse(session.undoReset());
        session.resetDefaults();
        session.cancel();
        assertEquals(Map.of("name", true, "health", false), session.snapshot());
        assertFalse(session.canUndoReset());
    }

    @Test void newEditsInvalidateResetUndoAndCancelUsesLastAppliedBaseline() {
        var session = session();
        session.resetDefaults();
        session.edit("name", false);
        assertFalse(session.canUndoReset());
        session.apply(patch -> {});
        session.edit("health", false);
        session.cancel();
        assertEquals(Map.of("name", false, "health", true), session.snapshot());
    }

    @Test void failuresKeepDraftAndDoNotPermitReentrantMutation() {
        var session = session();
        session.edit("health", true);
        assertThrows(IllegalStateException.class, () -> session.apply(patch -> session.cancel()));
        assertTrue(session.dirty());
        assertTrue(session.get("health"));
        assertThrows(IllegalArgumentException.class, () -> session.apply(patch -> {
            throw new IllegalArgumentException("invalid value");
        }));
        assertTrue(session.dirty());
        assertTrue(session.apply(patch -> {}));
    }

    @Test void keysAreScopedAndSnapshotsAreIndependent() {
        var session = session();
        var snapshot = session.snapshot();
        session.edit("name", false);
        assertTrue(snapshot.get("name"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        assertThrows(IllegalArgumentException.class, () -> session.edit("world", false));
        assertThrows(IllegalArgumentException.class, () -> session.get("world"));
        assertThrows(IllegalArgumentException.class, () -> new SettingsDraftSession<>(Map.of("name", true), Map.of()));
        assertThrows(NullPointerException.class, () -> session.edit("name", null));
    }
}
