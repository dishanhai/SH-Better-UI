// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.input.SingleLineEdit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/** Themed UUK single-line text field. Use setError to report the host's validation result. */
public final class UiTextField extends UiTextFieldPlatform {
    private static final int PADDING = 6;
    private final SingleLineEdit edit;
    private final UiPreferences preferences;
    private final InterruptibleAnimation hover = new InterruptibleAnimation(Spring.INDICATOR);
    private Consumer<String> onChange = value -> {};
    private Consumer<String> onSubmit;
    private Component hint = Component.empty();
    private Component error;
    private long blink = now();
    private int scroll;
    private int[] boundaries = {0};
    private int[] positions = {0};
    private boolean selecting;

    public UiTextField(Font font, Bounds bounds, Component label, int maxLength, UiPreferences preferences) {
        super(font, bounds, label);
        edit = new SingleLineEdit(maxLength);
        this.preferences = Objects.requireNonNull(preferences);
        tooltipDelay(preferences.feedback().tooltipDelayMs());
    }

    public String getValue() { return edit.value(); }

    /** Synchronizes the field without firing edit callbacks. */
    public void setValue(String value) {
        if (edit.value().equals(value)) return;
        edit.setValue(value);
        measure();
        updateCaret(false);
    }

    public void setOnChange(Consumer<String> listener) { onChange = Objects.requireNonNull(listener); }
    public void setOnSubmit(Consumer<String> listener) { onSubmit = listener; }
    public void setHint(Component hint) { this.hint = Objects.requireNonNull(hint); }
    public void setError(Component error) { this.error = error; }

    @Override
    public void setFocused(boolean focused) {
        if (isFocused() == focused) return;
        super.setFocused(focused);
        if (!focused) selecting = false;
        updateCaret(false);
    }

    @Override
    void pointerDown(double x, boolean extend, boolean doubleClick) {
        selecting = true;
        int point = pointAt(x);
        if (doubleClick) edit.selectWord(point);
        else edit.moveTo(point, extend);
        updateCaret(false);
    }

    @Override
    void pointerDrag(double x) {
        if (!selecting || !isActive()) return;
        edit.moveTo(pointAt(x), true);
        updateCaret(false);
    }

    @Override void pointerUp() { selecting = false; }

    @Override
    boolean input(String text) {
        if (!acceptsInput()) return false;
        updateCaret(edit.insert(text));
        return true;
    }

    @Override
    boolean key(int key, boolean shortcut, boolean shift, boolean alt) {
        if (!acceptsInput() || alt) return false;
        if (shortcut && shortcuts(key, shift)) return true;
        boolean edited = false;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> edit.move(-1, shortcut, shift);
            case GLFW.GLFW_KEY_RIGHT -> edit.move(1, shortcut, shift);
            case GLFW.GLFW_KEY_HOME -> edit.moveTo(0, shift);
            case GLFW.GLFW_KEY_END -> edit.moveTo(edit.value().length(), shift);
            case GLFW.GLFW_KEY_BACKSPACE -> edited = edit.delete(-1, shortcut);
            case GLFW.GLFW_KEY_DELETE -> edited = edit.delete(1, shortcut);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (onSubmit == null) return false;
                onSubmit.accept(edit.value());
                return true;
            }
            default -> { return false; }
        }
        updateCaret(edited);
        return true;
    }

    private boolean shortcuts(int key, boolean shift) {
        boolean edited = false;
        switch (key) {
            case GLFW.GLFW_KEY_A -> edit.selectAll();
            case GLFW.GLFW_KEY_C -> copy();
            case GLFW.GLFW_KEY_X -> {
                copy();
                edited = edit.deleteSelection();
            }
            case GLFW.GLFW_KEY_V -> edited = edit.insert(Minecraft.getInstance().keyboardHandler.getClipboard());
            case GLFW.GLFW_KEY_Z -> edited = shift ? edit.redo() : edit.undo();
            case GLFW.GLFW_KEY_Y -> edited = edit.redo();
            default -> { return false; }
        }
        updateCaret(edited);
        return true;
    }

    private void copy() {
        if (edit.selectionStart() != edit.selectionEnd()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(edit.selectedText());
        }
    }

    private void updateCaret(boolean edited) {
        if (edited) measure();
        blink = now();
        revealCaret();
        if (edited) onChange.accept(edit.value());
    }

    private boolean acceptsInput() { return isFocused() && isActive(); }
    private int textWidth() { return Math.max(1, getWidth() - 2 * PADDING); }

    private void revealCaret() {
        int x = advance(edit.caret());
        scroll = Math.max(x - textWidth() + 1, Math.min(scroll, x));
        scroll = Math.min(scroll, Math.max(0, advance(edit.value().length()) - textWidth() + 1));
    }

    private int pointAt(double mouseX) {
        double target = mouseX - getX() - PADDING + scroll;
        for (int i = 1; i < boundaries.length; i++) {
            if (target < (positions[i - 1] + positions[i]) * .5) return boundaries[i - 1];
        }
        return edit.value().length();
    }

    private int advance(int offset) {
        return positions[Arrays.binarySearch(boundaries, offset)];
    }

    private void measure() {
        boundaries = edit.boundaries();
        positions = new int[boundaries.length];
        float width = 0f;
        for (int i = 1; i < boundaries.length; i++) {
            var cluster = edit.value().substring(boundaries[i - 1], boundaries[i]);
            width += font.getSplitter().stringWidth(literal(cluster));
            positions[i] = Math.round(width);
        }
    }

    @Override
    void paint(Canvas canvas) {
        boolean focused = acceptsInput();
        long time = now();
        var theme = UiTheme.current();
        int ink = opaque(theme.text());
        int fill = GuiMotion.lerpColor(opaque(theme.surface()), opaque(theme.background()), .55f);
        int accent = opaque(theme.accent());
        int baseEdge = preferences.highContrast() ? ink : opaque(theme.border());
        int danger = theme.light() ? 0xFFAA2435 : 0xFFFFA4AA;
        float target = isActive() && isHovered() ? 1f : 0f;
        if (hover.target() != target) hover.animateTo(target, time);
        float emphasis = GuiMotion.clamp01(hover.sample(time, preferences));
        fill = GuiMotion.lerpColor(fill, ink, emphasis * .06f);
        int edge = error != null ? danger : GuiMotion.lerpColor(baseEdge, ink, emphasis * .5f);
        if (focused && error == null) edge = accent;
        if (!isActive()) ink = GuiMotion.lerpColor(ink, fill, .5f);
        canvas.box(getX(), getY(), getWidth(), getHeight(), 5, focused ? 2 : 1, edge, fill);
        if (getWidth() <= 2 * PADDING || getHeight() < font.lineHeight) return;
        revealCaret();
        int left = getX() + PADDING;
        int x = left - scroll;
        int y = getY() + (getHeight() - font.lineHeight) / 2;
        int caretX = x + advance(edit.caret());
        canvas.clip(left, getY(), left + textWidth(), getY() + getHeight());
        try {
            if (focused && edit.selectionStart() != edit.selectionEnd()) {
                int start = x + advance(edit.selectionStart());
                int end = x + advance(edit.selectionEnd());
                canvas.fill(start, y - 1, end, y + font.lineHeight + 1, GuiMotion.lerpColor(fill, accent, .38f));
            }
            if (edit.value().isEmpty()) {
                int hintColor = GuiMotion.lerpColor(ink, fill, preferences.highContrast() ? 0f : .27f);
                canvas.text(hint.getVisualOrderText(), x, y, hintColor);
            } else {
                canvas.text(literal(edit.value()), x, y, ink);
            }
            if (focused && !composing() && (preferences.reducedMotion() || (time - blink) % 1000 < 550)) {
                canvas.fill(caretX, y - 1, caretX + 1, y + font.lineHeight + 1, ink);
            }
        } finally {
            canvas.unclip();
        }
        if (focused) textInputArea(caretX, y);
    }

    // Keep glyph order aligned with the editing offsets in this single-line control.
    private static FormattedCharSequence literal(String value) {
        return FormattedCharSequence.forward(value, Style.EMPTY);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.editBox", getMessage(), edit.value()));
        if (error != null) output.add(NarratedElementType.HINT, error);
    }

    private static int opaque(int argb) { return argb | 0xFF000000; }
    private static long now() { return System.nanoTime() / 1_000_000L; }
}
