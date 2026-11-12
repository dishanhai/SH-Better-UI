// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.page;

import com.dishanhai.sh_better_ui.kernel.api.page.UiDataSchema;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Owner-thread field replacement for the lifetime of a fixed page tree. */
public final class PageDataState {
    public enum Update { APPLIED, STALE, SNAPSHOT_REQUIRED }

    private final UiDataSchema schema;
    private Map<String, UiValue> data;
    private long revision;
    private long missingThrough;

    public PageDataState(UiDataSchema schema, Map<String, UiValue> initial) {
        this.schema = Objects.requireNonNull(schema);
        var copy = Map.copyOf(initial);
        schema.validate(copy, true);
        data = copy;
    }

    public Map<String, UiValue> data() { return data; }

    public long revision() { return revision; }

    public boolean synchronizedData() { return missingThrough == 0; }

    public Update apply(long base, long next, Map<String, UiValue> updates) {
        if (base < 0 || next <= base) throw new IllegalArgumentException("Invalid page revisions");
        if (next <= revision) return Update.STALE;
        var changes = Map.copyOf(updates);
        schema.validate(changes, false);
        if (!synchronizedData() || base != revision) {
            missingThrough = Math.max(missingThrough, next);
            return Update.SNAPSHOT_REQUIRED;
        }
        var merged = new LinkedHashMap<>(data);
        merged.putAll(changes);
        data = Map.copyOf(merged);
        revision = next;
        return Update.APPLIED;
    }

    public boolean restore(long next, Map<String, UiValue> snapshot) {
        if (next < revision || next < missingThrough || next == revision && synchronizedData()) return false;
        var copy = Map.copyOf(snapshot);
        schema.validate(copy, true);
        data = copy;
        revision = next;
        missingThrough = 0;
        return true;
    }
}
