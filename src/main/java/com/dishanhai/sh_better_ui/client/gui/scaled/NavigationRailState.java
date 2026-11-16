// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.function.IntPredicate;

/** Manual-activation navigation. Focus never changes the selected page. */
public final class NavigationRailState {
    private final UiTabSelection selection;
    private final int count;
    private int focused, pressed = -1;
    public NavigationRailState(int count, int selected, IntPredicate enabled) {
        selection = new UiTabSelection(count, selected, enabled);
        this.count = count; focused = selected;
    }
    public int selectedIndex() { return selection.selectedIndex(); }
    public int focusedIndex() { return focused; }
    public int pressedIndex() { return pressed; }
    /** Keyboard re-entry starts at the active page; pointer entry retains its pressed item. */
    public void focusEntered() { focused = pressed >= 0 ? pressed : selectedIndex(); }
    /** Hover narration follows the actual item, unless keyboard focus owns the group. */
    public int narrationIndex(boolean focused, int hovered) {
        return !focused && isEnabled(hovered) ? hovered : this.focused;
    }
    public boolean isEnabled(int index) { return selection.isEnabled(index); }
    /** Synchronize without an activation callback; retain keyboard focus. */
    public boolean select(int index) { cancel(); return selection.select(index); }
    public void move(int direction) {
        cancel(); int step = Integer.signum(direction);
        if (step == 0) return;
        for (int n = 1; n <= count; n++) {
            int next = Math.floorMod(focused + n * step, count);
            if (isEnabled(next)) { focused = next; return; }
        }
    }
    public void edge(boolean last) {
        cancel();
        for (int n = 0; n < count; n++) {
            int next = last ? count - n - 1 : n;
            if (isEnabled(next)) { focused = next; return; }
        }
    }
    public boolean press(int index) {
        cancel(); if (!isEnabled(index)) return false;
        focused = pressed = index; return true;
    }
    public void drag(int index) { if (index != pressed) cancel(); }
    public boolean release(int index) {
        int previous = pressed; cancel();
        return previous >= 0 && previous == index && selection.select(index);
    }
    public boolean activate() { cancel(); return selection.select(focused); }
    public void cancel() { pressed = -1; }
}
