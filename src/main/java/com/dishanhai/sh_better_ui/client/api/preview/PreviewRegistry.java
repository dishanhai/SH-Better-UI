package com.dishanhai.sh_better_ui.client.api.preview;

import com.dishanhai.sh_better_ui.ShBetterUi;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 山海：开发者图库、查询及全屏入口共用的预览目录。 */
public final class PreviewRegistry {
    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    private static final String BUILTIN_NAMESPACE = ShBetterUi.MOD_ID + ":";
    private static final Comparator<PreviewSpec> ORDER = Comparator.comparing(PreviewSpec::category)
            .thenComparingInt(PreviewSpec::order).thenComparing(PreviewSpec::id);

    private final List<Source> sources = new ArrayList<>();
    private boolean dirty;
    private final Map<String, Entry> byId = new HashMap<>();
    private volatile List<PreviewSpec> snapshot = List.of();

    public Registration register(PreviewSpec spec) {
        return registerAll(List.of(spec));
    }

    /** Registers all declarations together, or leaves the current directory intact on an ID conflict. */
    public synchronized Registration registerAll(List<PreviewSpec> specs) {
        var current = entries();
        var additions = new HashMap<String, Entry>();
        for (var spec : specs) {
            var entry = new Entry(spec);
            if (current.stream().anyMatch(existing -> existing.id().equals(spec.id())) || additions.putIfAbsent(spec.id(), entry) != null) {
                throw new IllegalStateException("Developer preview already registered: " + spec.id());
            }
        }
        if (!additions.isEmpty()) {
            byId.putAll(additions);
            dirty = true;
            refreshSnapshot();
        }
        return () -> unregister(additions);
    }

    /** Adds a live, lazy catalog. Sources must return immutable lists and stable descriptors until a registration changes.
     * IDs must be unique across all sources and direct registrations. No scene is created by discovery. */
    public synchronized Registration registerSource(Supplier<List<PreviewSpec>> source) {
        Objects.requireNonNull(source);
        var entry = new Source(source);
        sources.add(entry);
        dirty = true;
        try { refreshSnapshot(); }
        catch (RuntimeException failure) { sources.remove(entry); dirty = true; throw failure; }
        return () -> { synchronized (this) {
            if (sources.remove(entry)) { dirty = true; refreshSnapshot(); }
        } };
    }

    /** 按分类、顺序和 ID 排列，注册变化前复用同一个只读快照。 */
    public synchronized List<PreviewSpec> entries() {
        refreshSnapshot();
        return snapshot;
    }

    public synchronized Optional<PreviewSpec> find(String id) {
        Objects.requireNonNull(id);
        String canonical = id.indexOf(':') < 0 ? BUILTIN_NAMESPACE + id : id;
        return entries().stream().filter(spec -> spec.id().equals(canonical)).findFirst();
    }

    /** 兼容原有内置短 ID，其他模组的条目返回完整命名空间。 */
    public List<String> ids() {
        return entries().stream().map(PreviewSpec::id)
                .map(id -> id.startsWith(BUILTIN_NAMESPACE) ? id.substring(BUILTIN_NAMESPACE.length()) : id)
                .toList();
    }

    private void refreshSnapshot() {
        var values = new ArrayList<List<PreviewSpec>>(sources.size());
        boolean changed = dirty;
        for (var source : sources) {
            var entries = Objects.requireNonNull(source.supplier.get());
            values.add(entries);
            changed |= entries != source.previous;
        }
        if (!changed) return;
        var merged = new HashMap<String, PreviewSpec>();
        byId.forEach((id, entry) -> merged.put(id, entry.spec));
        for (var entries : values) for (var spec : entries) {
            if (merged.putIfAbsent(spec.id(), spec) != null) {
                throw new IllegalStateException("Developer preview already registered: " + spec.id());
            }
        }
        var next = merged.values().stream().sorted(ORDER).toList();
        boolean same = next.size() == snapshot.size();
        for (int i = 0; same && i < next.size(); i++) same = next.get(i) == snapshot.get(i);
        if (!same) snapshot = next;
        for (int i = 0; i < sources.size(); i++) sources.get(i).previous = values.get(i);
        dirty = false;
    }

    private synchronized void unregister(Map<String, Entry> entries) {
        boolean changed = false;
        for (var entry : entries.entrySet()) {
            changed |= byId.remove(entry.getKey(), entry.getValue());
        }
        if (changed) { dirty = true; refreshSnapshot(); }
    }

    private static final class Source {
        private final Supplier<List<PreviewSpec>> supplier;
        private List<PreviewSpec> previous;
        private Source(Supplier<List<PreviewSpec>> supplier) { this.supplier = supplier; }
    }

    private static final class Entry {
        private final PreviewSpec spec;

        private Entry(PreviewSpec spec) {
            // 同一个声明撤销再登记时，活动图库也需要识别新的注册世代。
            this.spec = new PreviewSpec(spec.id(), spec.category(), spec.order(), spec.titleKey(),
                    spec.preferredHeight(), spec.toolbarRightInset(), spec.code(), spec.factory());
        }
    }
}
