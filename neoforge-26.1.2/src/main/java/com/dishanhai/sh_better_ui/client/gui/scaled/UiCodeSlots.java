// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.Consumer;

/** A single focusable numeric input with individually editable digit slots. */
public final class UiCodeSlots extends AbstractWidget {
    public enum Status { IDLE, ERROR, SUCCESS }

    private static final String KEY = "sh_better_ui.code_slots.";
    private final CodeSlotsState state;
    private UiPreferences preferences;
    private boolean masked;
    private boolean caret = true;

    public UiCodeSlots(Bounds bounds, int length, Component label, UiPreferences preferences) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), Objects.requireNonNull(label));
        state = new CodeSlotsState(length);
        this.preferences = Objects.requireNonNull(preferences);
    }

    public String value() { return state.value(); }
    public Status status() { return state.status(); }
    public void setMasked(boolean value) { masked = value; }
    public void setCaretVisible(boolean value) { caret = value; }

    public void setPreferences(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    /** Synchronizes a value supplied by the host without firing edit or completion callbacks. */
    public void setValue(String value) {
        state.setValue(value, Util.getMillis());
    }

    public void setStatus(Status status) {
        state.setStatus(status, Util.getMillis(), preferences.reducedMotion());
    }

    public void setOnChange(Consumer<String> callback) {
        state.onChange = Objects.requireNonNull(callback);
    }

    public void setOnComplete(Consumer<String> callback) {
        state.onComplete = Objects.requireNonNull(callback);
    }

    /** Advances a pending rejection even while a mounted widget is temporarily outside the viewport. */
    public void tick() { state.advance(Util.getMillis()); }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        tick();
        state.select(CodeSlotsPaint.slotAt(event.x() - getX(), getWidth(), state.length()));
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        tick();
        if (!isFocused() || !isActive() || event.codepoint() < '0' || event.codepoint() > '9') return false;
        return state.insert(event.codepointAsString(), Util.getMillis());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        tick();
        if (!isFocused() || !isActive() || !state.accepting()) return false;
        long now = Util.getMillis();
        if (event.isPaste()) {
            state.insert(Minecraft.getInstance().keyboardHandler.getClipboard(), now);
            return true;
        }
        if (event.hasControlDownWithQuirk() || event.hasAltDown()) return false;
        return switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT -> state.select(state.selected() - 1);
            case GLFW.GLFW_KEY_RIGHT -> state.select(state.selected() + 1);
            case GLFW.GLFW_KEY_HOME -> state.select(0);
            case GLFW.GLFW_KEY_END -> state.select(state.length() - 1);
            case GLFW.GLFW_KEY_BACKSPACE -> state.delete(true, now);
            case GLFW.GLFW_KEY_DELETE -> state.delete(false, now);
            default -> false;
        };
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        state.advance(now);
        int hovered = isActive() && isHovered()
                ? CodeSlotsPaint.slotAt(mouseX - getX(), getWidth(), state.length()) : -1;
        var font = Minecraft.getInstance().font;
        CodeSlotsPaint.draw(new CodeSlotsPaint.Canvas() {
            @Override
            public void box(int x, int y, int width, int height, int radius, int stroke, int edge, int fill) {
                if (width > 0 && height > 0) RoundedCorners.draw(graphics, x, y, width, height, radius, stroke, edge, fill);
            }

            @Override
            public void text(String value, int centerX, int centerY, int color) {
                if (color >>> 24 < 4) return;
                graphics.text(font, value, centerX - font.width(value) / 2, centerY - font.lineHeight / 2, color, false);
            }

            @Override
            public void line(int x, int y, int height, int color) {
                graphics.fill(x, y, x + 1, y + height, color);
            }
        }, state, getX(), getY(), getWidth(), getHeight(), preferences, isFocused(),
                hovered, masked, caret, isActive(), alpha, now);
    }

    public Component statusMessage() {
        return switch (state.status()) {
            case IDLE -> Component.translatable(KEY + "count", state.filled(), state.length());
            case ERROR -> Component.translatable(KEY + "error");
            case SUCCESS -> Component.translatable(KEY + "success");
        };
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage().copy().append(" ").append(statusMessage()));
        if (isFocused() && isActive() && state.accepting()) {
            output.add(NarratedElementType.USAGE, Component.translatable(KEY + "usage", state.selected() + 1));
        }
    }
}
