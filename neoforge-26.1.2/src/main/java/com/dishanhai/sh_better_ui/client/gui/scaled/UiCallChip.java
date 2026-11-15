// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.kernel.api.controls.CallChipState;
import com.dishanhai.sh_better_ui.kernel.api.controls.CallChipState.Status;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/** Shows the state of an operation and exposes retry input in the error state. */
public final class UiCallChip extends AbstractWidget {
    private static final String KEY = "sh_better_ui.call_chip.";
    private static final int PAD = 7;
    private static final int GAP = 5;
    private static final int DONE_COLOR = 0xFF22C55E;
    private static final int ERROR_COLOR = 0xFFEF4444;

    private final Component name;
    private final Component argument;
    private final GraphicIcon icon;
    private final CallChipState state;
    private final boolean showTimer;
    private final Runnable onRetry;
    private UiPreferences preferences;
    private Status tooltipStatus;
    private boolean pressed;

    /** A null retry callback makes the chip a passive indicator in every state. */
    public UiCallChip(Bounds area, Component name, Component argument, GraphicIcon icon,
                      CallChipState state, UiPreferences preferences, boolean showTimer, Runnable onRetry) {
        super(area.x(), area.y(), area.width(), area.height(), name);
        this.name = name;
        this.argument = Objects.requireNonNull(argument);
        this.icon = Objects.requireNonNull(icon);
        this.state = Objects.requireNonNull(state);
        this.showTimer = showTimer;
        this.onRetry = onRetry;
        updateStatusTooltip(state.status());
        setPreferences(preferences);
    }

    public void setPreferences(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
        setTooltipDelay(Duration.ofMillis(preferences.feedback().tooltipDelayMs()));
    }

    public boolean cancelPress() {
        boolean cancelled = pressed;
        pressed = false;
        return cancelled;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) cancelPress();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return press(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return release(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return drag(event.x(), event.y(), event.button());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return handleKey(event.key());
    }

    private boolean press(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !canRetry() || !isMouseOver(x, y)) return false;
        pressed = true;
        return true;
    }

    private boolean release(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !cancelPress()) return false;
        if (canRetry() && isMouseOver(x, y)) retry();
        return true;
    }

    private boolean drag(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !pressed) return false;
        if (!canRetry() || !isMouseOver(x, y)) cancelPress();
        return true;
    }

    private boolean handleKey(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return cancelPress();
        if (pressed || !isFocused() || !canRetry() || key != GLFW.GLFW_KEY_ENTER
                && key != GLFW.GLFW_KEY_KP_ENTER && key != GLFW.GLFW_KEY_SPACE) return false;
        retry();
        return true;
    }

    private boolean canRetry() {
        return onRetry != null && state.status() == Status.ERROR && isActive()
                && Minecraft.getInstance().isWindowActive();
    }

    private void retry() {
        playDownSound(Minecraft.getInstance().getSoundManager());
        onRetry.run();
    }

    private Component statusTitle(Status status) {
        return Component.translatable(KEY + "narration", name, argument,
                Component.translatable(KEY + "status." + status.name().toLowerCase(Locale.ROOT)));
    }

    private void updateStatusTooltip(Status status) {
        if (tooltipStatus == status) return;
        tooltipStatus = status;
        setTooltip(Tooltip.create(statusTitle(status), null));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        var frame = state.sample(true);
        Component title = statusTitle(frame.status());
        if (showTimer && (frame.status() == Status.DONE || frame.status() == Status.ERROR)) {
            title = Component.translatable(KEY + "narration_time", title, elapsed(frame.elapsedMs()));
        }
        output.add(NarratedElementType.TITLE, title);
        if (canRetry()) output.add(NarratedElementType.USAGE, Component.translatable(KEY + "retry"));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (pressed && (!canRetry() || !isMouseOver(mouseX, mouseY))) cancelPress();
        int width = getWidth();
        int height = getHeight();
        if (width < 2 * PAD + GraphicIcon.PIXELS || height < GraphicIcon.PIXELS + 2) return;
        var frame = state.sample(preferences.reducedMotion());
        updateStatusTooltip(frame.status());
        var theme = UiTheme.current();
        int track = theme.surface() | 0xFF000000;
        int ink = readable(theme.text(), track);
        int stateColor = color(frame.status(), ink, track);
        int edge = isFocused() || pressed ? readable(theme.accent(), track)
                : preferences.highContrast() ? ink : theme.border();
        int x = getX() + Math.round(6 * frame.shake());
        int y = getY();
        float radius = Math.min(10, height / 2f);
        RoundedCorners.draw(graphics, x, y, width, height, radius, 0, track, track);

        int fillWidth = Math.round(width * frame.fill());
        int fillTop = y + height - Math.round(height * frame.wash());
        if (fillWidth > 0 && fillTop < y + height) {
            int fill = frame.status() == Status.RUNNING ? (ink & 0xFFFFFF) | 0x14000000
                    : (stateColor & 0xFFFFFF) | 0x24000000;
            graphics.enableScissor(x, fillTop, x + fillWidth, y + height);
            try {
                RoundedCorners.draw(graphics, x, y, width, height, radius, 0, fill, fill);
            } finally {
                graphics.disableScissor();
            }
        }
        RoundedCorners.draw(graphics, x, y, width, height, radius, pressed ? 3 : isFocused() ? 2 : 1, edge, 0);

        int iconX = x + PAD;
        int iconY = y + (height - GraphicIcon.PIXELS) / 2;
        drawGlyph(graphics, frame, iconX, iconY, ink, track);
        drawText(graphics, frame, iconX + GraphicIcon.PIXELS + GAP, x + width - PAD, y, height, ink);
    }

    private void drawGlyph(GuiGraphicsExtractor graphics, CallChipState.Snapshot frame, int x, int y, int ink, int track) {
        var current = glyph(frame.status());
        var previous = glyph(frame.previousStatus());
        float roll = current == previous ? 1 : frame.glyphRoll();
        int distance = Math.round(GraphicIcon.PIXELS * .7f);
        graphics.enableScissor(x, y, x + GraphicIcon.PIXELS, y + GraphicIcon.PIXELS);
        try {
            if (roll < 1) {
                previous.draw(graphics, x, y - Math.round(distance * roll), 1,
                        fade(color(frame.previousStatus(), ink, track), 1 - roll), preferences);
            }
            if (roll > 0) {
                current.draw(graphics, x, y + Math.round(distance * (1 - roll)), 1,
                        fade(color(frame.status(), ink, track), roll), preferences);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawText(GuiGraphicsExtractor graphics, CallChipState.Snapshot frame,
                          int left, int right, int y, int height, int ink) {
        if (left >= right) return;
        var font = Minecraft.getInstance().font;
        int baseline = y + (height - font.lineHeight) / 2;
        var label = Component.translatable(KEY + "label", name, argument);
        if (showTimer) {
            var timer = frame.status() == Status.IDLE ? Component.translatable(KEY + "timer_idle") : elapsed(frame.elapsedMs());
            int timerWidth = font.width(timer);
            if (right - left >= timerWidth + GAP + font.width(CommonComponents.ELLIPSIS)) {
                graphics.text(font, timer, right - timerWidth, baseline, ink, false);
                right -= timerWidth + GAP;
            }
        }
        int available = right - left;
        int ellipsis = font.width(CommonComponents.ELLIPSIS);
        if (available < ellipsis) return;
        var fitted = Language.getInstance().getVisualOrder(font.width(label) <= available ? label
                : FormattedText.composite(font.substrByWidth(label, available - ellipsis), CommonComponents.ELLIPSIS));
        graphics.text(font, fitted, left, baseline, ink, false);
    }

    private GraphicIcon glyph(Status status) {
        return switch (status) {
            case DONE -> GraphicIcon.SUCCESS;
            case ERROR -> onRetry != null ? GraphicIcon.RESET : GraphicIcon.WARNING;
            default -> icon;
        };
    }

    private int color(Status status, int ink, int track) {
        return switch (status) {
            case DONE -> readable(DONE_COLOR, track);
            case ERROR -> readable(ERROR_COLOR, track);
            default -> ink;
        };
    }

    private int readable(int ink, int track) {
        return ContrastPolicy.readable(ink, track, track,
                preferences.highContrast() ? ContrastPolicy.HIGH_CONTRAST_RATIO : ContrastPolicy.TEXT_RATIO);
    }

    private static int fade(int color, float opacity) {
        return (color & 0xFFFFFF) | (Math.round(opacity * 255) << 24);
    }

    private static Component elapsed(long ms) {
        if (ms < 10_000) return Component.translatable(KEY + "timer_ms", ms);
        String seconds = String.format(Locale.ROOT, "%.1f", ms / 1000.0);
        return Component.translatable(KEY + "timer_s", seconds);
    }
}
