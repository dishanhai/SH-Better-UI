// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicQueue.Mode;
import java.util.*;

/** Queue-position navigation, confined to the player lock. Duplicate songs remain distinct entries. */
final class PlaybackOrder {
    private final Random random;
    private final List<Integer> remaining = new ArrayList<>(), history = new ArrayList<>();
    private int size, index = -1, cursor = -1;
    private Mode mode = Mode.SEQUENTIAL;
    PlaybackOrder() { this(new Random()); }
    PlaybackOrder(Random random) { this.random = random; }
    record Saved(int size, int index, Mode mode, List<Integer> remaining, List<Integer> history, int cursor) {}
    Saved save() { return new Saved(size, index, mode, List.copyOf(remaining), List.copyOf(history), cursor); }
    void restore(Saved saved) {
        size = saved.size(); index = saved.index(); mode = saved.mode(); cursor = saved.cursor();
        remaining.clear(); remaining.addAll(saved.remaining());
        history.clear(); history.addAll(saved.history());
    }
    Mode mode() { return mode; }
    void mode(Mode value) { mode = Objects.requireNonNull(value); reset(size, index); }
    void reset(int size, int index) {
        this.size = size; this.index = index;
        remaining.clear(); history.clear(); cursor = -1;
        if (index >= 0) { history.add(index); cursor = 0; }
        refill();
    }
    private void refill() {
        remaining.clear();
        for (int i = 0; i < size; i++) if (i != index) remaining.add(i);
        Collections.shuffle(remaining, random);
    }
    boolean canNext() {
        return index >= 0 && (index + 1 < size || mode == Mode.REPEAT_ALL
                || mode == Mode.SHUFFLE && size > 1);
    }
    boolean canPrevious() {
        return index >= 0 && (mode == Mode.SHUFFLE ? cursor > 0 : index > 0 || mode == Mode.REPEAT_ALL);
    }
    int next(boolean automatic) {
        if (index < 0) return -1;
        if (automatic && mode == Mode.REPEAT_ONE) return index;
        if (mode == Mode.SHUFFLE) {
            if (cursor + 1 < history.size()) return index = history.get(++cursor);
            if (size < 2) return automatic ? index : -1;
            if (remaining.isEmpty()) refill();
            index = remaining.remove(remaining.size() - 1);
            history.add(index); cursor++;
            // Retain useful previous navigation without unbounded growth during long sessions.
            if (history.size() > 256) { history.remove(0); cursor--; }
            return index;
        }
        if (index + 1 < size) return ++index;
        return mode == Mode.REPEAT_ALL ? index = 0 : -1;
    }
    int previous() {
        if (!canPrevious()) return -1;
        if (mode == Mode.SHUFFLE) return index = history.get(--cursor);
        return index = index > 0 ? index - 1 : size - 1;
    }
}
