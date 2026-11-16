package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;

/** 山海：保存画面返回或本地撤销所需的快照。两种用途应各自持有实例。 */
public final class UiHistory<S> {
    private final int capacity;
    private final ArrayDeque<S> snapshots = new ArrayDeque<>();

    public UiHistory(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException();
        this.capacity = capacity;
    }

    /** 在切页或提交可逆编辑前保存独立快照，例如筛选、滚动位置和编辑草稿。 */
    public void remember(S snapshot) {
        Objects.requireNonNull(snapshot);
        if (snapshots.size() == capacity) snapshots.removeFirst();
        snapshots.addLast(snapshot);
    }

    public boolean canRestore() { return !snapshots.isEmpty(); }

    public Optional<S> restore() { return Optional.ofNullable(snapshots.pollLast()); }

    public void clear() { snapshots.clear(); }
}
