// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.settings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Client-thread, scoped draft transaction for a secondary settings page.
 * Keys and values must be non-null; values must be immutable (or copied by the caller).
 * Only the initial keys belong to this session. Apply supplies a changed-values patch,
 * never a whole-parent snapshot, so unrelated parent-draft settings remain untouched.
 * The merge callback must atomically merge that patch into the parent's in-memory draft.
 * It must not perform partial external writes: this class cannot roll those back.
 */
public final class SettingsDraftSession<K, V> {
    private Map<K, V> baseline;
    private final Map<K, V> defaults;
    private final Map<K, V> draft;
    private Map<K, V> beforeReset;
    private boolean applying;

    public SettingsDraftSession(Map<K, V> initial, Map<K, V> defaults) {
        baseline = snapshotOf(initial);
        this.defaults = snapshotOf(defaults);
        if (!baseline.keySet().equals(this.defaults.keySet())) {
            throw new IllegalArgumentException("Defaults must contain exactly the session keys");
        }
        draft = new LinkedHashMap<>(baseline);
    }

    public V get(K key) {
        requireKey(key);
        return draft.get(key);
    }

    public void edit(K key, V value) {
        requireEditable();
        requireKey(key);
        Objects.requireNonNull(value, "value");
        if (!Objects.equals(draft.get(key), value)) {
            draft.put(key, value);
            beforeReset = null;
        }
    }

    /** Immutable copy, safe to keep while the session continues editing. */
    public Map<K, V> snapshot() { return snapshotOf(draft); }
    public boolean dirty() { return !draft.equals(baseline); }
    public boolean canUndoReset() { return beforeReset != null; }

    /** Changes only this session, not the main draft; Cancel can still discard it. */
    public void resetDefaults() {
        requireEditable();
        if (draft.equals(defaults)) return;
        beforeReset = snapshotOf(draft);
        draft.clear();
        draft.putAll(defaults);
    }

    public boolean undoReset() {
        requireEditable();
        if (beforeReset == null) return false;
        draft.clear();
        draft.putAll(beforeReset);
        beforeReset = null;
        return true;
    }

    /** Discards local edits since construction or the last successful Apply. */
    public void cancel() {
        requireEditable();
        draft.clear();
        draft.putAll(baseline);
        beforeReset = null;
    }

    /**
     * Returns false without invoking merge if unchanged. If merge throws, the session
     * stays dirty. The caller must reconcile any uncertain external result before retry;
     * prefer a single atomic in-memory parent-draft replacement in this callback.
     */
    public boolean apply(Consumer<Map<K, V>> merge) {
        requireEditable();
        Objects.requireNonNull(merge, "merge");
        var changes = new LinkedHashMap<K, V>();
        draft.forEach((key, value) -> {
            if (!Objects.equals(baseline.get(key), value)) changes.put(key, value);
        });
        if (changes.isEmpty()) return false;
        applying = true;
        try {
            merge.accept(Collections.unmodifiableMap(changes));
            baseline = snapshotOf(draft);
            beforeReset = null;
            return true;
        } finally {
            applying = false;
        }
    }

    private void requireKey(K key) {
        if (!draft.containsKey(Objects.requireNonNull(key, "key"))) {
            throw new IllegalArgumentException("Key is outside this settings session: " + key);
        }
    }

    private void requireEditable() {
        if (applying) throw new IllegalStateException("Cannot modify a session during Apply");
    }

    private static <K, V> Map<K, V> snapshotOf(Map<K, V> source) {
        Objects.requireNonNull(source, "source");
        var copy = new LinkedHashMap<K, V>();
        source.forEach((key, value) -> copy.put(Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(value, "value")));
        return Collections.unmodifiableMap(copy);
    }
}
