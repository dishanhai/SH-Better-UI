package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/** 山海：按下的归属优先于后续的命中，即使鼠标已经移出局部覆盖区。 */
final class UiLocalInputRouter<T> {
    @FunctionalInterface
    interface Outline<T> {
        boolean contains(T target, double x, double y);
    }

    private final List<T> foreground;
    private final Outline<T> outline;
    private final Map<Integer, T> captured = new HashMap<>();

    UiLocalInputRouter(List<T> foreground, Outline<T> outline) {
        this.foreground = foreground;
        this.outline = outline;
    }

    T hit(double x, double y) {
        for (int i = foreground.size() - 1; i >= 0; i--) {
            T target = foreground.get(i);
            if (outline.contains(target, x, y)) return target;
        }
        return null;
    }

    void capture(int button, T target) { captured.put(button, target); }

    T forget(int button) { return captured.remove(button); }

    T dragTarget(int button) { return captured.get(button); }

    void drain(T target, IntConsumer release) {
        captured.entrySet().removeIf(entry -> {
            if (entry.getValue() != target) return false;
            release.accept(entry.getKey());
            return true;
        });
    }
}
