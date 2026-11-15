// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.kernel.api.input.HoldGesture;
import com.dishanhai.sh_better_ui.kernel.api.input.HoldGesture.Input;
import com.dishanhai.sh_better_ui.kernel.api.input.HoldGesture.Result;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Native UUK button that confirms after an uninterrupted primary-button or keyboard hold. */
public final class UiHoldButton extends AbstractWidget {
    public enum Direction { RIGHT, UP }

    /** Dimensions use GUI units. Null colors follow the active theme. */
    public record Appearance(Direction direction, float radius, float pressScale, float waveAmplitude, boolean glow,
                             Integer backgroundColor, Integer fillColor, Integer textColor, Integer fillTextColor) {
        public static final Appearance DEFAULT = new Appearance(Direction.RIGHT, 14, 0.97f, 6, true,
                null, null, null, null);

        public Appearance(Direction direction) {
            this(direction, 14, 0.97f, 6, true, null, null, null, null);
        }

        public Appearance {
            Objects.requireNonNull(direction);
            if (!Float.isFinite(radius) || radius < 0 || !(pressScale > 0 && pressScale <= 1)
                    || !Float.isFinite(waveAmplitude) || waveAmplitude < 0) {
                throw new IllegalArgumentException("Invalid hold button appearance");
            }
        }
    }

    private static final String KEY = "sh_better_ui.hold_button.";
    private static final int PRIMARY = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    private static final int[] ACTIVATION_KEYS = {GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER};
    private static final int HIT_PAD = 10;
    private static final int LABEL_PADDING = 5;

    private final HoldGesture state;
    private final Appearance appearance;
    private final Component doneLabel;
    private final Component usage;
    private final Tooltip hint;
    private final Tooltip doneHint;
    private final Runnable onHold;
    private final Runnable onTap;
    private UiPreferences preferences;
    private GraphicIcon icon;
    private GraphicIcon doneIcon;
    private int pressedKeys;
    private int holdKey;
    private boolean showingDoneTooltip;

    public UiHoldButton(Bounds area, Component label, Component doneLabel, HoldGesture.Timing timing,
                        Appearance appearance, UiPreferences preferences, Runnable onHold, Runnable onTap) {
        super(area.x(), area.y(), area.width(), area.height(), Objects.requireNonNull(label));
        this.state = new HoldGesture(timing);
        this.appearance = Objects.requireNonNull(appearance);
        this.doneLabel = Objects.requireNonNull(doneLabel);
        this.onHold = Objects.requireNonNull(onHold);
        this.onTap = onTap;
        usage = Component.translatable(KEY + "usage", BigDecimal.valueOf(timing.holdMs(), 3)
                .stripTrailingZeros().toPlainString());
        hint = Tooltip.create(CommonComponents.joinForNarration(label, usage));
        doneHint = Tooltip.create(doneLabel);
        setTooltip(hint);
        setPreferences(preferences);
    }

    public void setPreferences(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
        com.dishanhai.sh_better_ui.client.api.PlatformUiApi.setTooltipDelay(this, preferences.feedback().tooltipDelayMs());
    }

    /** Null removes the corresponding leading icon. */
    public void setLeadingIcons(GraphicIcon icon, GraphicIcon doneIcon) {
        this.icon = icon;
        this.doneIcon = doneIcon;
    }

    public boolean isCompleted() { return state.completed(); }

    /** Cancels an active hold while retaining the normal release animation. */
    public boolean cancelHold() { return state.cancel(now()); }

    /** Clears the done label or an active hold. A new press is required to confirm again. */
    public void reset() {
        state.reset();
        updateTooltip();
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) cancelHold();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return (button == PRIMARY) && ready() && isMouseOver(mouseX, mouseY) && state.begin(Input.POINTER, now());
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (!(button == PRIMARY) || state.input() != Input.POINTER) return false;
        if (!ready() || !withinTolerance(mouseX, mouseY)) cancelHold();
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!(button == PRIMARY) || state.input() != Input.POINTER) return false;
        if (ready() && withinTolerance(mouseX, mouseY)) dispatch(state.release(Input.POINTER, now()));
        else cancelHold();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode;
        if (key == GLFW.GLFW_KEY_ESCAPE) return cancelHold();
        int bit = keyBit(key);
        if (bit == 0 || !isFocused() || !ready()) return false;
        if ((pressedKeys & bit) == 0 && state.begin(Input.KEYBOARD, now())) holdKey = key;
        pressedKeys |= bit;
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        int key = keyCode;
        int bit = keyBit(key);
        if ((pressedKeys & bit) == 0) return false;
        pressedKeys &= ~bit;
        if (state.input() == Input.KEYBOARD && key == holdKey) {
            if (ready() && isFocused()) dispatch(state.release(Input.KEYBOARD, now()));
            else cancelHold();
        }
        return true;
    }

    private static int keyBit(int key) {
        for (int i = 0; i < ACTIVATION_KEYS.length; i++) {
            if (ACTIVATION_KEYS[i] == key) return 1 << i;
        }
        return 0;
    }

    private void checkCapture() {
        if (!ready()) cancelHold();
        if (state.input() == Input.POINTER) {
            var mouse = UiMouseState.read();
            if (!mouse.available() || !mouse.isDown(PRIMARY) || !withinTolerance(mouse.x(), mouse.y())) cancelHold();
        }
        if (pressedKeys == 0) return;
        long handle = Minecraft.getInstance().getWindow().getWindow();
        for (int i = 0; i < ACTIVATION_KEYS.length; i++) {
            int bit = 1 << i;
            if ((pressedKeys & bit) == 0 || GLFW.glfwGetKey(handle, ACTIVATION_KEYS[i]) == GLFW.GLFW_PRESS) continue;
            if (state.input() == Input.KEYBOARD && holdKey == ACTIVATION_KEYS[i]) cancelHold();
            pressedKeys &= ~bit;
        }
    }

    private boolean ready() {
        return (active && visible) && getWidth() > 4 && getHeight() > 4 && Minecraft.getInstance().isWindowActive();
    }

    private boolean withinTolerance(double x, double y) {
        return x >= getX() - HIT_PAD && x < getX() + getWidth() + HIT_PAD
                && y >= getY() - HIT_PAD && y < getY() + getHeight() + HIT_PAD;
    }

    private void dispatch(Result result) {
        updateTooltip();
        if (result == Result.CONFIRMED) onHold.run();
        if (result == Result.TAP && onTap != null) onTap.run();
    }

    private void updateTooltip() {
        if (state.completed() == showingDoneTooltip) return;
        showingDoneTooltip = state.completed();
        setTooltip(showingDoneTooltip ? doneHint : hint);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.button", label()));
        if ((active && visible) && !state.completed()) output.add(NarratedElementType.USAGE, usage);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        checkCapture();
        long now = now();
        Result result = state.advance(now);
        int width = getWidth(), height = getHeight();
        if (width > 4 && height > 4) {
            int x = getX(), y = getY();
            boolean enabled = (active && visible);
            boolean focused = enabled && isFocused();
            boolean reduced = preferences.reducedMotion();
            float progress = state.progress(now);
            float radius = RoundedCorners.fit(width, height, appearance.radius());
            var paint = HoldButtonPaint.palette(appearance, preferences, enabled, isHovered(), focused);
            float scale = !reduced && preferences.feedback().pressAnimation() && state.input() == Input.POINTER
                    ? appearance.pressScale() : 1;
            var pose = graphics.pose();
            pose.pushPose();
            if (scale != 1) {
                float centerX = x + width * 0.5f, centerY = y + height * 0.5f;
                pose.translate(centerX, centerY, 0);
                pose.scale(scale, scale, 1);
                pose.translate(-centerX, -centerY, 0);
            }
            try {
                float glow = appearance.glow() && !reduced && enabled && !preferences.highContrast()
                        ? Math.max(progress * 0.24f, state.completionPulse(now) * 0.55f) : 0;
                if (glow > 0) {
                    int padding = 2 + Math.round(6 * state.completionPulse(now));
                    int edge = ((int) (glow * 255) << 24) | (paint.fill() & 0xFFFFFF);
                    RoundedCorners.draw(graphics, x - padding, y - padding, width + 2 * padding,
                            height + 2 * padding, radius + padding, padding, edge, 0);
                }
                int border = focused || preferences.highContrast() ? 2 : 1;
                int base = reduced ? GuiMotion.lerpColor(paint.background(), paint.fill(), progress) : paint.background();
                RoundedCorners.draw(graphics, x, y, width, height, radius, border, paint.border(), base);
                drawContent(graphics, paint, progress, reduced, border);
            } finally {
                pose.popPose();
            }
        }
        dispatch(result);
    }

    private void drawContent(GuiGraphics graphics, HoldButtonPaint.Palette paint, float progress,
                             boolean reduced, int border) {
        int x = getX(), y = getY(), width = getWidth(), height = getHeight();
        var font = Minecraft.getInstance().font;
        GraphicIcon shown = state.completed() ? doneIcon : icon;
        int available = Math.max(0, width - 2 * LABEL_PADDING);
        int iconWidth = shown != null && height >= GraphicIcon.PIXELS && available >= GraphicIcon.PIXELS + 12
                ? GraphicIcon.PIXELS + 4 : 0;
        int textWidth = available - iconWidth;
        FormattedText text = label();
        if (font.width(text) > textWidth) {
            int ellipsisWidth = font.width(CommonComponents.ELLIPSIS);
            text = textWidth < ellipsisWidth ? CommonComponents.EMPTY : FormattedText.composite(
                    font.substrByWidth(text, textWidth - ellipsisWidth), CommonComponents.ELLIPSIS);
        }
        var visual = Language.getInstance().getVisualOrder(text);
        int contentWidth = iconWidth + font.width(visual);
        int contentHeight = Math.max(font.lineHeight, iconWidth > 0 ? GraphicIcon.PIXELS : 0);
        int left = x + (width - contentWidth) / 2;
        int top = y + (height - font.lineHeight) / 2;
        int contentTop = y + (height - contentHeight) / 2;
        IntConsumer ink = color -> {
            if (iconWidth > 0) shown.draw(graphics, left, y + (height - GraphicIcon.PIXELS) / 2, 1, color, preferences);
            graphics.drawString(font, visual, left + iconWidth, top, color, false);
        };
        if (reduced) {
            int background = GuiMotion.lerpColor(paint.background(), paint.fill(), progress);
            int labelColor = GuiMotion.lerpColor(paint.text(), paint.fillText(), progress);
            ink.accept(ContrastPolicy.readable(labelColor, background, background,
                    preferences.highContrast() ? ContrastPolicy.HIGH_CONTRAST_RATIO : ContrastPolicy.TEXT_RATIO));
            return;
        }

        ink.accept(paint.text());
        int innerX = x + border, innerY = y + border;
        float radius = RoundedCorners.insetRadius(width, height, appearance.radius(), border);
        var matrix = graphics.pose().last().pose();
        var min = new Vector3f();
        var max = new Vector3f();
        HoldButtonPaint.spans(width - 2 * border, height - 2 * border, radius, progress,
                appearance.direction() == Direction.UP, appearance.waveAmplitude(), (l, t, r, b) -> {
                    graphics.fill(innerX + l, innerY + t, innerX + r, innerY + b, paint.fill());
                    if (innerX + r <= left || innerX + l >= left + contentWidth
                            || innerY + b <= contentTop || innerY + t >= contentTop + contentHeight) return;
                    // Legacy GuiGraphics expects screen coordinates even under a scaled pose.
                    matrix.transformPosition(innerX + l, innerY + t, 0, min);
                    matrix.transformPosition(innerX + r, innerY + b, 0, max);
                    graphics.enableScissor(Math.round(min.x), Math.round(min.y), Math.round(max.x), Math.round(max.y));
                    try {
                        ink.accept(paint.fillText());
                    } finally {
                        graphics.disableScissor();
                    }
                });
    }

    private Component label() { return state.completed() ? doneLabel : getMessage(); }

    private static long now() { return System.nanoTime() / 1_000_000L; }
}
