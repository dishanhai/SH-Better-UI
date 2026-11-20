package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.controls.ControlPaint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

/** 山海：使用原生键鼠与旁白的开关。滑块位置反映接入方传入的实际状态。 */
public final class NetworkToggle extends Button {
    private static final int TRACK_WIDTH = 22;
    private static final int TRACK_HEIGHT = 12;
    private static final int PADDING = 4;

    private final UiPreferences preferences;
    private final DarkModeTheme.Interaction motion = DarkModeTheme.Interaction.control();
    private boolean value;
    private boolean justPressed;

    public NetworkToggle(Bounds area, Component label, boolean value, UiPreferences preferences,
                         Consumer<Boolean> onChange) {
        super(area.x(), area.y(), area.width(), area.height(), label, action(onChange), DEFAULT_NARRATION);
        if (area.width() < 40 || area.height() < 14) {
            throw new IllegalArgumentException("Not enough room for a toggle");
        }
        this.value = value;
        this.preferences = Objects.requireNonNull(preferences);
        setTooltip(Tooltip.create(label));
    }

    private static OnPress action(Consumer<Boolean> onChange) {
        Objects.requireNonNull(onChange);
        return button -> onChange.accept(!((NetworkToggle) button).value());
    }

    public boolean value() { return value; }

    /** 同步本地提交结果或服务器确认值。此方法不会再次发出请求。 */
    public void setValue(boolean value) { this.value = value; }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        setTooltip(Tooltip.create(message));
    }

    @Override
    public void onPress() {
        if (!active || !visible) return;
        justPressed = true;
        super.onPress();
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        super.updateWidgetNarration(output);
        output.add(NarratedElementType.HINT, Component.translatable(value ? "options.on" : "options.off"));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), width = getWidth(), height = getHeight();
        if (width < 40 || height < 14) return;
        var state = motion.sample(isHovered(), justPressed, isFocused(), active, preferences, PageTransition.nowMs());
        justPressed = false;
        if (state.focused() || state.hover() > 0f || state.press() > 0f) {
            int fill = GuiMotion.lerpColor(0x001C3448, UiTheme.current().controlFill(0xCC1C3448, 0.14f), Math.max(state.hover(), state.press()));
            DarkModeTheme.rounded(graphics, x, y, width, height, 4, state.focused() ? 2 : 1,
                    NetworkTheme.outline(preferences.highContrast() || state.focused()), fill);
        }

        int trackX = x + width - TRACK_WIDTH - PADDING;
        int trackY = y + (height - TRACK_HEIGHT) / 2;
        int edge = active ? DarkModeTheme.secondaryTextColor() : DarkModeTheme.mutedTextColor();
        int fill = value && active ? UiTheme.current().accent(0xFF265C83) : UiTheme.current().background(0xFF121A24);
        ControlPaint.toggle((rx, ry, rw, rh, radius, stroke, border, color) ->
                        DarkModeTheme.rounded(graphics, (int) rx, (int) ry, (int) rw, (int) rh,
                                (float) radius, (int) stroke, border, color),
                trackX, trackY, 1, value ? 1 : 0, edge, fill, value && active ? 0xFFFFFFFF : edge);

        var font = Minecraft.getInstance().font;
        graphics.enableScissor(x + PADDING, y + 2, trackX - PADDING, y + height - 2);
        try {
            graphics.drawString(font, getMessage(), x + PADDING, y + (height - font.lineHeight) / 2,
                    active ? NetworkTheme.labelColor(preferences) : DarkModeTheme.mutedTextColor(), false);
        } finally {
            graphics.disableScissor();
        }
    }
}
