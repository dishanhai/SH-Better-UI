// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.page;

import com.dishanhai.sh_better_ui.kernel.api.page.UiDataSchema;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValueSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.dishanhai.sh_better_ui.kernel.internal.page.PageDataState.Update.*;
import static org.junit.jupiter.api.Assertions.*;

class PageDataStateTest {
    private static final UiDataSchema SCHEMA = new UiDataSchema(Map.of(
            "balance", UiValueSchema.integer(0, 500), "items", UiValueSchema.list(UiValueSchema.text(12), 3)));
    private static final UiValue.Sequence ITEMS = new UiValue.Sequence(List.of(new UiValue.Text("sample")));
    private static final Map<String, UiValue> INITIAL = Map.of("balance", new UiValue.Int(10), "items", ITEMS);

    @Test
    void replacesWholeFieldsAndKeepsPreviousSnapshotImmutable() {
        var state = new PageDataState(SCHEMA, INITIAL);
        var before = state.data();
        var empty = new UiValue.Sequence(List.of());
        assertEquals(APPLIED, state.apply(0, 2, Map.of("items", empty)));
        assertEquals(empty, state.data().get("items"));
        assertEquals(ITEMS, before.get("items"));
        assertEquals(new UiValue.Int(10), state.data().get("balance"));
        assertEquals(STALE, state.apply(0, 1, Map.of("balance", new UiValue.Int(7))));
        assertEquals(2, state.revision());
    }

    @Test
    void validatesEveryFieldBeforePublishingAnyChange() {
        var state = new PageDataState(SCHEMA, INITIAL);
        assertThrows(IllegalArgumentException.class, () -> state.apply(0, 1,
                Map.of("balance", new UiValue.Int(11), "items", new UiValue.Text("bad"))));
        assertEquals(INITIAL, state.data());
        assertEquals(0, state.revision());
        assertTrue(state.synchronizedData());
    }

    @Test
    void requiresAFullSnapshotThroughTheMissingRevision() {
        var state = new PageDataState(SCHEMA, INITIAL);
        assertEquals(SNAPSHOT_REQUIRED, state.apply(4, 5, Map.of("balance", new UiValue.Int(7))));
        assertFalse(state.synchronizedData());
        assertEquals(SNAPSHOT_REQUIRED, state.apply(0, 1, Map.of("balance", new UiValue.Int(8))));
        assertFalse(state.restore(4, INITIAL));
        assertThrows(IllegalArgumentException.class, () -> state.restore(5, Map.of("balance", new UiValue.Int(8))));
        assertFalse(state.synchronizedData());
        assertTrue(state.restore(5, INITIAL));
        assertTrue(state.synchronizedData());
        assertFalse(state.restore(4, INITIAL));
        assertEquals(APPLIED, state.apply(5, 6, Map.of("balance", new UiValue.Int(8))));
    }
}
