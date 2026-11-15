// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Util;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** A controlled notification toggle with a stable layout width and a variable-width face. */
public final class UiBellToggle extends Button {
    private static final int LABEL_X = 34;
    private static final int RIGHT_PADDING = 9;

    private final Component offLabel;
    private final Component onLabel;
    private final Supplier<UiPreferences> preferences;
    private final BooleanSupplier enabled;
    private final IntSupplier count;
    private final Predicate<Boolean> change;
    private int faceWidth;
    private boolean pressed;
    private final BellToggleMotion motion;

    public UiBellToggle(int x, int y, int width, int height, Component offLabel, Component onLabel,
                        Supplier<UiPreferences> preferences, BooleanSupplier enabled, IntSupplier count,
                        Predicate<Boolean> change) {
        super(x, y, width, height, offLabel, button -> {}, DEFAULT_NARRATION);
        this.offLabel = offLabel;
        this.onLabel = onLabel;
        this.preferences = preferences;
        this.enabled = enabled;
        this.count = count;
        this.change = change;
        motion = new BellToggleMotion(enabled.getAsBoolean(), count.getAsInt());
        setMessage(motion.on() ? onLabel : offLabel);
        var font = Minecraft.getInstance().font;
        setWidth(Math.min(width, LABEL_X + RIGHT_PADDING + Math.max(font.width(offLabel), font.width(onLabel))));
        faceWidth = Math.min(getWidth(), LABEL_X + RIGHT_PADDING + font.width(getMessage()));
    }

    @Override
    public boolean isMouseOver(double x, double y) {
        return active && visible && super.isMouseOver(x, y) && x < getX() + faceWidth;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0 || !isMouseOver(event.x(), event.y())) return false;
        pressed = true;
        return true;
    }

    public void cancelPress() {
        pressed = false;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) cancelPress();
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        super.updateWidgetNarration(output);
        int waiting = count.getAsInt();
        if (enabled.getAsBoolean() && waiting > 0) {
            output.add(NarratedElementType.HINT, Component.translatable("sh_better_ui.bell_toggle.count", waiting));
        }
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        if (!pressed) return;
        cancelPress();
        if (isMouseOver(event.x(), event.y())) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            toggle(true);
        }
    }

    @Override
    public void onPress(InputWithModifiers input) {
        cancelPress();
        if (active && visible) toggle(false);
    }

    private void toggle(boolean pointer) {
        boolean target = !enabled.getAsBoolean();
        if (!change.test(target)) return;
        long now = Util.getMillis();
        boolean animate = pointer && !preferences.get().reducedMotion();
        motion.set(target, now, animate);
        setMessage(target ? onLabel : offLabel);
        if (animate && target) motion.ring(now, 820, 5, 17);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        var prefs = preferences.get();
        motion.sample(enabled.getAsBoolean(), count.getAsInt(), prefs.reducedMotion(), now);
        setMessage(motion.on() ? onLabel : offLabel);
        var font = Minecraft.getInstance().font;
        float fade = motion.fade(now);
        int width = Math.min(getWidth(), LABEL_X + RIGHT_PADDING + Math.round(
                GuiMotion.lerp(font.width(offLabel), font.width(onLabel), motion.reveal(now))));
        faceWidth = width;
        isHovered = isMouseOver(mouseX, mouseY);
        int x = getX(), y = getY(), height = getHeight();
        var theme = UiTheme.current();
        int offFill = prefs.highContrast() ? 0xff000000 : theme.surface() | 0xff000000;
        int offInk = prefs.highContrast() ? 0xffffffff : theme.text() | 0xff000000;
        int fill = GuiMotion.lerpColor(offFill, offInk, fade);
        if (isHovered) {
            float tint = pressed ? .23f : .11f;
            fill = GuiMotion.lerpColor(fill, GuiMotion.lerpColor(offInk, offFill, fade), tint);
        }
        int edge = isFocused() || prefs.highContrast() ? (motion.on() ? offFill : offInk) : theme.border();
        RoundedCorners.draw(graphics, x, y, width, height, height * .5f, isFocused() ? 1.5f : 1, edge, fill);
        graphics.enableScissor(x, y, x + width - 3, y + height);
        try {
            var canvas = new BellCanvas(graphics);
            motion.drawBell(canvas, x + 13, y + height / 2, GuiMotion.lerpColor(offInk, offFill, fade), now);
            motion.drawBadge(canvas, x + 21, y + 3, now, prefs.reducedMotion());
            drawLabel(graphics, offLabel, x + LABEL_X, y + (height - 8) / 2, width - LABEL_X - 3, offInk, 1 - fade);
            drawLabel(graphics, onLabel, x + LABEL_X, y + (height - 8) / 2, width - LABEL_X - 3, offFill, fade);
        } finally {
            graphics.disableScissor();
        }
    }

    private static void drawLabel(GuiGraphicsExtractor graphics, Component label, int x, int y, int width, int ink, float opacity) {
        if (opacity < .025f || width <= 0) return;
        var font = Minecraft.getInstance().font;
        graphics.text(font, Language.getInstance().getVisualOrder(font.substrByWidth(label, width)), x, y,
                (Math.round(opacity * 255) << 24) | (ink & 0xffffff), false);
    }

    private record BellCanvas(GuiGraphicsExtractor graphics) implements BellToggleMotion.Canvas {
        @Override public void push() { graphics.pose().pushMatrix(); }
        @Override public void pop() { graphics.pose().popMatrix(); }
        @Override public void translate(float x, float y) { graphics.pose().translate(x, y); }
        @Override public void rotate(float degrees) { graphics.pose().rotate((float) Math.toRadians(degrees)); }
        @Override public void scale(float factor) { graphics.pose().scale(factor, factor); }
        @Override public void box(float x, float y, float width, float height, float radius, float stroke, int edge, int fill) {
            RoundedCorners.draw(graphics, x, y, width, height, radius, stroke, edge, fill);
        }
        @Override public void fill(int left, int top, int right, int bottom, int color) {
            graphics.fill(left, top, right, bottom, color);
        }
        @Override public void clip(int left, int top, int right, int bottom) {
            graphics.enableScissor(left, top, right, bottom);
        }
        @Override public void unclip() { graphics.disableScissor(); }
        @Override public int width(String text) { return Minecraft.getInstance().font.width(text); }
        @Override public void text(String text, int x, int y, int color) {
            graphics.text(Minecraft.getInstance().font, text, x, y, color, false);
        }
    }
}
