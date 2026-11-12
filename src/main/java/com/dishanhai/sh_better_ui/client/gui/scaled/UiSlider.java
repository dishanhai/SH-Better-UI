package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
import com.dishanhai.sh_better_ui.client.gui.text.UiFormattedText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** 山海：保留原生键鼠、焦点和旁白的液态玻璃整数滑杆。 */
public final class UiSlider extends AbstractSliderButton {
    private static final int TRACK_INSET = 4; // 与原生滑杆的指针映射一致。
    private static final int MIN_HEIGHT = 16;

    private final int min;
    private final int step;
    private final long range;
    private final IntFunction<Component> label;
    private final IntConsumer onChange;
    private UiPreferences preferences;
    private int currentValue;
    private int keyDirection;
    private int messageValue;
    private Tooltip valueTooltip;
    private IntConsumer onCommit;
    private IntConsumer onCancel;
    private Integer pressedValue;

    /**
     * min 必须小于 max，step 必须为正数。各档从 min 起按 step 递增，max 始终可到达。
     * 若最后一档不足一个 step，方向键仍依次经过两端。
     * 区域宽度必须大于 8、高度至少为 16 个 GUI 单位。
     */
    public UiSlider(Bounds area, int min, int max, int step, int value, UiPreferences preferences,
                    IntFunction<Component> label, IntConsumer onChange) {
        super(area.x(), area.y(), area.width(), area.height(), CommonComponents.EMPTY, 0);
        if (min >= max || step <= 0 || area.width() <= 2 * TRACK_INSET || area.height() < MIN_HEIGHT) {
            throw new IllegalArgumentException();
        }
        this.min = min;
        this.step = step;
        this.range = (long) max - min;
        this.preferences = Objects.requireNonNull(preferences);
        PlatformUiApi.setTooltipDelay(this, preferences.feedback().tooltipDelayMs());
        this.label = Objects.requireNonNull(label);
        this.onChange = Objects.requireNonNull(onChange);
        setValue(value);
    }

    public int value() {
        return currentValue;
    }

    /** 同步到最近的一档。等距时取较大值，超出范围时取端点。此方法不触发回调。 */
    public void setValue(int value) {
        syncValue(quantize(UiMath.clamp((long) value - min, 0L, range)));
    }

    public void setPreferences(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
        PlatformUiApi.setTooltipDelay(this, preferences.feedback().tooltipDelayMs());
    }

    /** Optional final-value callback for pointer releases and keyboard edits. */
    public void setOnCommit(IntConsumer commit, IntConsumer cancel) {
        onCommit = Objects.requireNonNull(commit, "commit");
        onCancel = Objects.requireNonNull(cancel, "cancel");
    }

    @Override
    public void onClick(double x, double y) {
        if (onCommit != null) pressedValue = currentValue;
        super.onClick(x, y);
    }

    @Override
    public void onRelease(double x, double y) {
        super.onRelease(x, y);
        Integer previous = pressedValue;
        pressedValue = null;
        if (previous != null && active && visible && previous != currentValue) onCommit.accept(currentValue);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) cancelEdit();
    }

    /** Cancels a drag that lost its capture before release. */
    public void cancelEdit() {
        if (pressedValue != null) {
            int previous = pressedValue;
            pressedValue = null;
            if (previous != currentValue) {
                setValue(previous);
                onCancel.accept(previous);
            }
        }
    }

    public void refreshLabel() {
        refreshTooltip(true);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!active || !visible) return false;
        keyDirection = key == GLFW.GLFW_KEY_LEFT ? -1 : key == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
        int previous = currentValue;
        try {
            boolean handled = super.keyPressed(key, scanCode, modifiers);
            if (handled && onCommit != null && previous != currentValue) onCommit.accept(currentValue);
            return handled;
        } finally {
            keyDirection = 0;
        }
    }

    @Override
    protected void applyValue() {
        int previous = currentValue;
        int next = keyDirection == 0 ? quantize(value * range) : keyboardValue();
        syncValue(next);
        if (next != previous) onChange.accept(next);
    }

    @Override
    protected void updateMessage() {
        refreshTooltip(false);
    }

    private void refreshTooltip(boolean force) {
        if (!force && valueTooltip != null && messageValue == currentValue) return;
        Component message = Objects.requireNonNull(label.apply(currentValue));
        setMessage(message);
        messageValue = currentValue;
        if (getTooltip() == valueTooltip) {
            valueTooltip = Tooltip.create(message);
            setTooltip(valueTooltip);
        }
    }

    private void syncValue(int next) {
        currentValue = next;
        value = ((long) next - min) / (double) range;
        updateMessage();
    }

    private int quantize(double offset) {
        long lower = (long) (offset / step) * step;
        long upper = Math.min(range, lower + step);
        return (int) (min + (offset - lower < upper - offset ? lower : upper));
    }

    private int keyboardValue() {
        long offset = (long) currentValue - min;
        long next = keyDirection > 0 ? Math.min(range, offset + step) : Math.max(0, offset - 1) / step * step;
        return (int) (min + next);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), width = getWidth(), height = getHeight();
        if (width <= 2 * TRACK_INSET || height < MIN_HEIGHT) return;
        LiquidGlassTheme.input(graphics, x, y, width, height, preferences, false, isFocused());

        int trackX = x + TRACK_INSET;
        int trackWidth = width - 2 * TRACK_INSET;
        int trackY = y + height - 5;
        int thumbX = trackX + (int) Math.round(value * trackWidth);
        int accent = active ? LiquidGlassTheme.accentColor() : LiquidGlassTheme.mutedTextColor();
        graphics.fill(trackX, trackY, trackX + trackWidth, trackY + 1, LiquidGlassTheme.mutedTextColor());
        graphics.fill(trackX, trackY, thumbX, trackY + 1, accent);
        graphics.fill(thumbX - 2, trackY - 1, thumbX + 2, trackY + 2, accent);

        var font = Minecraft.getInstance().font;
        var message = getMessage();
        graphics.enableScissor(x + 2, y + 1, x + width - 2, trackY - 1);
        try {
            UiFormattedText.draw(graphics, font, message, x + (width - font.width(message)) / 2,
                    y + Math.max(1, (height - 5 - font.lineHeight) / 2), LiquidGlassTheme.controlText(active),
                    false, preferences);
        } finally {
            graphics.disableScissor();
        }
    }
}
