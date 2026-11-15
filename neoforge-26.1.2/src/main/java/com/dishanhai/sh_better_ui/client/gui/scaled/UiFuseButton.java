// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.kernel.api.controls.FuseButtonState;
import com.dishanhai.sh_better_ui.kernel.api.controls.FuseButtonState.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/** An undoable action. Register as a widget and call tick() while its owning screen is active. */
public final class UiFuseButton extends Button {
    private final FuseButtonState state;
    private final FuseButtonStyle style;
    private final FuseButtonState.Options options;
    private final Component idleLabel;
    private final Component undoLabel;
    private final Component doneLabel;
    private final Consumer<Phase> phaseChange;
    private UiPreferences preferences;
    private GraphicIcon idleIcon = GraphicIcon.ARCHIVE;
    private boolean disabled;
    private boolean pointerDown;
    private int activationKey = -1;
    private boolean animate;
    private Phase previous = Phase.IDLE;
    private Phase displayed = Phase.IDLE;
    private long changedAt;

    public UiFuseButton(Bounds area, Component label, Component undo, Component done, UiPreferences preferences,
                        FuseButtonStyle style, FuseButtonState.Options options, FuseButtonState.Callbacks callbacks) {
        super(area.x(), area.y(), area.width(), area.height(), label, button -> {}, DEFAULT_NARRATION);
        idleLabel = Objects.requireNonNull(label);
        undoLabel = Objects.requireNonNull(undo);
        doneLabel = Objects.requireNonNull(done);
        this.style = Objects.requireNonNull(style);
        this.options = Objects.requireNonNull(options);
        setPreferences(preferences);
        setTooltip(Tooltip.create(label));
        state = new FuseButtonState(options, new FuseButtonState.Callbacks(
                callbacks.commit(), callbacks.undo(), callbacks.fuseEnd(), this::syncPhase));
        phaseChange = callbacks.phaseChange();
    }

    public Phase phase() { return state.phase(); }

    public void setPreferences(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
        setTooltipDelay(Duration.ofMillis(preferences.feedback().tooltipDelayMs()));
    }

    public void setIdleIcon(GraphicIcon icon) {
        idleIcon = Objects.requireNonNull(icon);
    }

    /** Disabling the idle action preserves Undo while armed. */
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
        syncActive();
    }

    public void tick() {
        boolean windowActive = Minecraft.getInstance().isWindowActive();
        if (!windowActive) clearInput();
        state.advance(Util.getMillis(), isHovered(), windowActive);
    }

    /** Cancels a pending action without waiting for the next tick. */
    public boolean undo() {
        animate = false;
        return state.undo();
    }

    public void cancelPress() {
        clearInput();
    }

    private void clearInput() {
        pointerDown = false;
        activationKey = -1;
    }

    private void syncPhase(Phase next) {
        previous = displayed;
        displayed = next;
        pointerDown = false;
        changedAt = Util.getMillis();
        var label = label(next);
        setMessage(label);
        setTooltip(Tooltip.create(label));
        syncActive();
        phaseChange.accept(next);
    }

    private void syncActive() {
        active = switch (state.phase()) {
            case IDLE -> !disabled;
            case ARMED -> true;
            case SETTLED -> false;
        };
        if (!active) pointerDown = false;
    }

    private void activate(boolean pointer) {
        var before = state.phase();
        tick();
        if (before != state.phase() || !isActive()) return;
        animate = pointer;
        if (before == Phase.ARMED) state.undo();
        else state.arm(Util.getMillis(), pointer || isHovered(), Minecraft.getInstance().isWindowActive());
    }

    @Override public void onPress(InputWithModifiers input) { activate(false); }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return isMouseOver(event.x(), event.y())
                && super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY)
                && FuseButtonPaint.hit(getWidth(), getHeight(), style.radius(), mouseX - getX(), mouseY - getY());
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        pointerDown = true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !pointerDown) return false;
        pointerDown = false;
        if (isMouseOver(event.x(), event.y()) && Minecraft.getInstance().isWindowActive()) activate(true);
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) clearInput();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (phase() == Phase.ARMED) {
                cancelPress();
                tick();
                undo();
                return true;
            }
            if (pointerDown) {
                cancelPress();
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            if (!isActive()) return false;
            if (activationKey == key) return true;
            activationKey = key;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        int key = event.key();
        if (activationKey == key) activationKey = -1;
        return super.keyReleased(event);
    }

    private Component label(Phase phase) {
        return switch (phase) {
            case IDLE -> idleLabel;
            case ARMED -> undoLabel;
            case SETTLED -> doneLabel;
        };
    }

    private Component undoHint() {
        return options.commitOn() == FuseButtonState.CommitOn.PRESS
                ? Component.translatable("sh_better_ui.fuse_button.undo_hint", doneLabel)
                : Component.translatable("sh_better_ui.fuse_button.pending_hint");
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        var narration = super.createNarrationMessage();
        return phase() == Phase.ARMED ? CommonComponents.joinForNarration(narration, undoHint()) : narration;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || alpha <= 0) return;
        long now = Util.getMillis();
        if (pointerDown && !isMouseOver(mouseX, mouseY)) pointerDown = false;
        boolean pressed = isActive() && pointerDown && Minecraft.getInstance().isWindowActive();
        float mix = animate && !preferences.reducedMotion() && !preferences.highContrast() && style.crossfadeMs() > 0
                ? GuiMotion.clamp01((now - changedAt) / (float) style.crossfadeMs()) : 1f;
        var surface = new FuseButtonPaint.Surface() {
            @Override public void shape(float sx, float sy, float sw, float sh, float radius, float stroke,
                                        int edge, int fill) {
                RoundedCorners.draw(graphics, sx, sy, sw, sh, radius, stroke, edge, fill);
            }

            @Override public void pushClip(int left, int top, int right, int bottom) {
                graphics.enableScissor(left, top, right, bottom);
            }

            @Override public void popClip() { graphics.disableScissor(); }

            @Override public void text(Font font, FormattedCharSequence line, int tx, int ty, int color) {
                graphics.text(font, line, tx, ty, color, false);
            }

            @Override public void icon(GraphicIcon icon, int ix, int iy, int color) {
                icon.draw(graphics, ix, iy, 1, color, preferences);
            }
        };
        FuseButtonPaint.draw(surface, new Bounds(x, y, w, h), style, preferences, phase(), previous, this::label,
                idleIcon, isActive(), isFocused(), pressed, isHovered(), alpha, mix, state.fractionRemaining(now));
    }
}
