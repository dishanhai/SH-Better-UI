// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.text.UiHudRichText;
import com.dishanhai.sh_better_ui.kernel.api.controls.SwitchRowState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.Consumer;

/** Controlled settings row with a trailing switch, native focus and cancelable release. */
public final class UiSwitchRow extends Button implements UiCancelableInteraction {
    private final SwitchRowState state;
    private final Consumer<Boolean> change;
    private final InterruptibleAnimation knob;
    private UiPreferences preferences;
    private Component description;
    private boolean divider = true;

    public UiSwitchRow(Bounds area, Component title, Component description, boolean value,
                       UiPreferences preferences, Consumer<Boolean> change) {
        super(area.x(), area.y(), area.width(), area.height(), UiHudRichText.prepare(title),
                ignored -> {}, DEFAULT_NARRATION);
        this.description = UiHudRichText.prepare(Objects.requireNonNull(description));
        this.preferences = Objects.requireNonNull(preferences);
        this.change = Objects.requireNonNull(change);
        state = new SwitchRowState(value);
        knob = new InterruptibleAnimation(new Spring.Preset(.20, 1, .001f, .001f), value ? 1 : 0);
        updateTooltip();
    }

    public boolean value() { return state.value(); }
    public void setValue(boolean value) { state.setValue(value); }
    public void setPending(boolean pending) { state.setPending(pending); updateTooltip(); }
    public void setDescription(Component description) {
        this.description = UiHudRichText.prepare(Objects.requireNonNull(description)); updateTooltip();
    }
    public void setDivider(boolean divider) { this.divider = divider; }
    public void setPreferences(UiPreferences preferences) { this.preferences = Objects.requireNonNull(preferences); }
    private void updateTooltip() {
        var text = getMessage().copy().append("\n").append(description);
        setTooltip(Tooltip.create(text));
    }
    private boolean interactive() {
        return active && visible && !state.pending() && Minecraft.getInstance().isWindowActive();
    }
    @Override public void onPress(InputWithModifiers input) { onPress(); }
    public void onPress() { if (interactive()) change.accept(!state.value()); }
    @Override public boolean cancelInteraction() {
        boolean held = state.pressed(); state.cancel(); return held;
    }
    @Override public boolean releasePointer(double x, double y, int button) {
        if (button != 0 || !state.pressed()) return false;
        boolean activate = state.release(interactive() && isMouseOver(x, y));
        if (activate) {
            playDownSound(Minecraft.getInstance().getSoundManager()); onPress();
        }
        return true;
    }
    @Override public void setFocused(boolean focused) {
        super.setFocused(focused); if (!focused) state.cancel();
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return event.button() == 0 && interactive() && state.press(isMouseOver(event.x(), event.y()));
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return event.button() == 0 && state.pressed();
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return releasePointer(event.x(), event.y(), event.button());
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && cancelInteraction()) return true;
        return interactive() && super.keyPressed(event);
    }
    @Override public void updateWidgetNarration(NarrationElementOutput output) {
        super.updateWidgetNarration(output);
        output.add(NarratedElementType.HINT, description.copy().append(" ").append(Component.translatable(
                state.value() ? "options.on" : "options.off")));
    }
    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        if (!Minecraft.getInstance().isWindowActive() || !active || !visible) state.cancel();
        var theme = UiTheme.current();
        int text = theme.text(), secondary = preferences.highContrast() ? text : theme.secondaryText(text, .28f);
        boolean hover = isHovered() && interactive();
        if (hover || isFocused() || state.pressed()) RoundedCorners.draw(graphics, getX(), getY(), getWidth(), getHeight(),
                7, isFocused() ? 1 : 0, theme.accent(), theme.controlFill(theme.accent(), state.pressed() ? .25f : .12f));
        if (divider) graphics.fill(getX() + 10, getY() + getHeight() - 1, getX() + getWidth() - 10,
                getY() + getHeight(), theme.border() & 0x00FFFFFF | (preferences.highContrast() ? 0xFF000000 : 0x60000000));
        int trackWidth = 30, trackHeight = 16;
        int trackX = getX() + getWidth() - trackWidth - 10, trackY = getY() + (getHeight() - trackHeight) / 2;
        int available = Math.max(1, trackX - getX() - 20);
        var font = Minecraft.getInstance().font;
        int titleY = getY() + (getHeight() >= 32 && !description.getString().isEmpty() ? 7 : (getHeight() - font.lineHeight) / 2);
        var lines = font.split(getMessage(), available);
        if (!lines.isEmpty()) graphics.text(font, lines.get(0), getX() + 10, titleY, active ? text : secondary, false);
        if (getHeight() >= 32 && !description.getString().isEmpty()) {
            var hints = font.split(description, available);
            if (!hints.isEmpty()) graphics.text(font, hints.get(0), getX() + 10,
                    titleY + font.lineHeight + 4, secondary, false);
        }
        long now = System.nanoTime() / 1_000_000L;
        float target = state.value() ? 1 : 0;
        if (knob.target() != target) knob.animateTo(target, now);
        float position = Math.max(0, Math.min(1, knob.sample(now, preferences)));
        int edge = preferences.highContrast() ? text : theme.border();
        int fill = state.value() ? theme.accent() : theme.controlFill(theme.text(), .12f);
        RoundedCorners.draw(graphics, trackX, trackY, trackWidth, trackHeight, 8, 1, edge, fill);
        RoundedCorners.draw(graphics, trackX + 2 + position * 14, trackY + 2, 12, 12, 6, 0, 0,
                state.pending() ? secondary : 0xFFFFFFFF);
        // A small mark keeps on/off recognizable without color or motion.
        if (state.value()) graphics.fill(trackX + 6, trackY + 6, trackX + 7, trackY + 10, text);
    }
}
