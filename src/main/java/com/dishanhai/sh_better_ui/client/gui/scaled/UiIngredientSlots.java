// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.controls.IngredientSlotsState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

/** Focusable grid of virtual ingredient slots. All changes are delegated to its host. */
public final class UiIngredientSlots extends UiIngredientSlotsPlatform {
    interface Canvas {
        void fill(int x, int y, int right, int bottom, int color);
        void item(Font font, ItemStack stack, int x, int y);
        void tooltip(Font font, Component label, int x, int y);
    }

    private final Font font;
    private final UiPreferences preferences;
    private final int columns, rows;
    private final IngredientSlotsState state;
    private final IntConsumer onChoose, onClear;
    private final BiConsumer<Integer, Integer> onMove;
    private List<ItemStack> items;
    private List<Component> names;
    private Screen owner;
    private double pointerX, pointerY;
    private boolean allowDrag = true;
    private boolean rounded;
    private boolean cancelledPress;

    public UiIngredientSlots(Font font, Bounds area, Component name, int columns, List<ItemStack> items,
                             UiPreferences preferences, IntConsumer onChoose, IntConsumer onClear,
                             BiConsumer<Integer, Integer> onMove) {
        super(area, name);
        this.font = Objects.requireNonNull(font);
        this.preferences = Objects.requireNonNull(preferences);
        if (columns < 1) throw new IllegalArgumentException("At least one column is required");
        this.items = copy(items);
        if (this.items.isEmpty()) throw new IllegalArgumentException("At least one slot is required");
        this.columns = columns;
        this.rows = (this.items.size() + columns - 1) / columns;
        this.state = new IngredientSlotsState(this.items.size(), columns);
        this.names = java.util.Collections.nCopies(this.items.size(), name);
        this.onChoose = Objects.requireNonNull(onChoose);
        this.onClear = Objects.requireNonNull(onClear);
        this.onMove = Objects.requireNonNull(onMove);
    }

    private static List<ItemStack> copy(List<ItemStack> value) {
        return Objects.requireNonNull(value).stream().map(stack -> Objects.requireNonNull(stack).copy()).toList();
    }

    /** Changes the displayed stacks without moving real inventory items. */
    public void setItems(List<ItemStack> value) {
        var next = copy(value);
        if (next.size() != items.size()) throw new IllegalArgumentException("Slot count cannot change");
        cancelInteraction();
        items = next;
    }

    public void setSlotNames(List<Component> value) {
        var next = List.copyOf(value);
        if (next.size() != items.size()) throw new IllegalArgumentException("Expected a name for every slot");
        names = next;
    }

    public void setAllowDrag(boolean value) { allowDrag = value; if (!value) cancelInteraction(); }
    public void setRounded(boolean value) { rounded = value; }

    /** Call when unmounting a widget before its Screen closes. */
    public void cancelInteraction() {
        if (owner != null) cancelCapture(owner);
        owner = null;
        state.cancel();
    }

    /** Cancels a pending press, preserving its release for this control. */
    public boolean cancelDrag() {
        if (state.pressed() < 0) return false;
        cancelInteraction();
        cancelledPress = true;
        return true;
    }

    public boolean consumeCancelledRelease() {
        if (!cancelledPress) return false;
        cancelledPress = false;
        return true;
    }

    @Override public void setFocused(boolean focused) {
        super.setFocused(focused);
        // Native containers briefly unset and restore focus even when clicking this same widget.
        // Check the settled focus in paint/release instead of cancelling that new press here.
    }

    private int side() { return Math.max(0, Math.min((getWidth() - (columns - 1) * 2) / columns,
            (getHeight() - (rows - 1) * 2) / rows)); }
    private int x(int index) { return getX() + index % columns * (side() + 2); }
    private int y(int index) { return getY() + index / columns * (side() + 2); }
    private int at(double x, double y) {
        int side = side();
        if (side < 1 || x < getX() || y < getY()) return -1;
        int col = (int) ((x - getX()) / (side + 2)), row = (int) ((y - getY()) / (side + 2));
        int index = row * columns + col;
        if (col >= columns || row >= rows || index >= items.size() || x >= x(index) + side || y >= y(index) + side) return -1;
        return index;
    }

    @Override boolean pointerDown(double x, double y, int button) {
        if (!visible || !active) return false;
        int index = at(x, y);
        if (index < 0) return false;
        if (button == 1) { onClear.accept(index); return true; }
        if (button != 0) return false;
        cancelInteraction();
        cancelledPress = false;
        state.press(index, x, y, allowDrag);
        pointerX = x; pointerY = y;
        owner = Minecraft.getInstance().screen;
        if (owner != null) beginCapture(owner, button);
        return true;
    }

    @Override void capturedCancel() { owner = null; state.cancel(); }

    @Override boolean pointerDrag(double x, double y) {
        if (state.pressed() < 0) return false;
        pointerX = x; pointerY = y;
        state.move(x, y, at(x, y));
        return true;
    }

    @Override boolean pointerUp(double x, double y) {
        if (consumeCancelledRelease()) return true;
        if (state.pressed() < 0) return false;
        if (!isFocused()) { cancelInteraction(); return true; }
        var action = state.release(at(x, y));
        owner = null;
        if (action.kind() == IngredientSlotsState.Kind.CHOOSE) onChoose.accept(action.from());
        else if (action.kind() == IngredientSlotsState.Kind.MOVE) onMove.accept(action.from(), action.to());
        return true;
    }

    @Override boolean key(int key, int modifiers) {
        if (!isFocused() || !active || !visible) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE && cancelDrag()) return true;
        if ((modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) return false;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            onChoose.accept(state.selected()); return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            onClear.accept(state.selected()); return true;
        }
        return switch (key) {
            case GLFW.GLFW_KEY_LEFT -> state.navigate(-1, 0);
            case GLFW.GLFW_KEY_RIGHT -> state.navigate(1, 0);
            case GLFW.GLFW_KEY_UP -> state.navigate(0, -1);
            case GLFW.GLFW_KEY_DOWN -> state.navigate(0, 1);
            default -> false;
        };
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        int index = state.selected();
        output.add(NarratedElementType.TITLE, CommonComponents.joinForNarration(getMessage(), names.get(index),
                items.get(index).getHoverName()));
    }

    @Override void paint(Canvas canvas, int mouseX, int mouseY) {
        if (!visible || !active || state.pressed() >= 0 && !isFocused() || !Minecraft.getInstance().isWindowActive()
                || owner != null && owner != Minecraft.getInstance().screen) cancelInteraction();
        int side = side();
        int hovered = at(mouseX, mouseY);
        var theme = UiTheme.current();
        for (int i = 0; i < items.size(); i++) {
            int sx = x(i), sy = y(i);
            boolean marked = i == state.target() || i == state.pressed() || i == hovered || isFocused() && i == state.selected();
            fillSlot(canvas, sx, sy, side,
                    marked ? preferences.highContrast() ? theme.text() : theme.accent() : theme.border());
            if (side > 2) fillSlot(canvas, sx + 1, sy + 1, side - 2, theme.surface() | 0xFF000000);
            if (side >= 18 && !(state.dragging() && i == state.pressed()))
                canvas.item(font, items.get(i), sx + (side - 16) / 2, sy + (side - 16) / 2);
        }
        if (state.dragging() && !items.get(state.pressed()).isEmpty())
            canvas.item(font, items.get(state.pressed()), (int) pointerX - 8, (int) pointerY - 8);
        if (hovered >= 0 && !state.dragging() && !names.get(hovered).getString().isEmpty())
            canvas.tooltip(font, names.get(hovered), mouseX, mouseY);
    }

    private void fillSlot(Canvas canvas, int x, int y, int size, int color) {
        if (!rounded || size < 4) { canvas.fill(x, y, x + size, y + size, color); return; }
        canvas.fill(x + 1, y, x + size - 1, y + size, color);
        canvas.fill(x, y + 1, x + size, y + size - 1, color);
    }
}
