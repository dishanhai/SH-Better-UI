// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

/** Platform-independent focus and pointer state for an ordered grid of ingredient slots. */
public final class IngredientSlotsState {
    public enum Kind { NONE, CHOOSE, MOVE }
    public record Action(Kind kind, int from, int to) {}
    public record Grid(int columns, int rows) { public int capacity() { return columns * rows; } }

    /** Maximum bounded grid that keeps every slot at least the requested size. */
    public static Grid fit(int width, int height, int preferredColumns, int preferredRows, int minimumSide, int gap) {
        if (width < 0 || height < 0 || preferredColumns < 1 || preferredRows < 1 || minimumSide < 1 || gap < 0)
            throw new IllegalArgumentException("Invalid grid layout");
        int columns = Math.min(preferredColumns, (width + gap) / (minimumSide + gap));
        int rows = Math.min(preferredRows, (height + gap) / (minimumSide + gap));
        return new Grid(columns, rows);
    }
    private static final Action NONE = new Action(Kind.NONE, -1, -1);
    private final int size, columns;
    private int selected, pressed = -1, target = -1;
    private double startX, startY;
    private boolean draggable, dragging;

    public IngredientSlotsState(int size, int columns) {
        if (size < 0 || columns < 1) throw new IllegalArgumentException("Invalid grid dimensions");
        this.size = size;
        this.columns = columns;
        selected = size == 0 ? -1 : 0;
    }

    public int selected() { return selected; }
    public int pressed() { return pressed; }
    public int target() { return dragging ? target : -1; }
    public boolean dragging() { return dragging; }

    public boolean press(int index, double x, double y, boolean canDrag) {
        cancel();
        if (index < 0 || index >= size) return false;
        selected = pressed = index;
        startX = x; startY = y; draggable = canDrag;
        return true;
    }

    public void move(double x, double y, int index) {
        if (pressed < 0) return;
        double dx = x - startX, dy = y - startY;
        if (draggable && dx * dx + dy * dy >= 16) dragging = true;
        target = dragging && index >= 0 && index < size && index != pressed ? index : -1;
    }

    public Action release(int index) {
        Action result = NONE;
        if (pressed >= 0) {
            if (dragging && target >= 0 && target == index) {
                selected = target;
                result = new Action(Kind.MOVE, pressed, target);
            }
            else if (!dragging && pressed == index) result = new Action(Kind.CHOOSE, pressed, pressed);
        }
        cancel();
        return result;
    }

    public void cancel() { pressed = -1; target = -1; draggable = false; dragging = false; }

    public boolean navigate(int dx, int dy) {
        if (selected < 0 || Math.abs(dx) + Math.abs(dy) != 1) return false;
        int col = selected % columns + dx;
        int row = selected / columns + dy;
        int next = row * columns + col;
        if (col < 0 || col >= columns || row < 0 || next >= size) return false;
        selected = next;
        return true;
    }
}
