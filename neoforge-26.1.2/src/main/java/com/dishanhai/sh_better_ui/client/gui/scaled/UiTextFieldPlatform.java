// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

abstract class UiTextFieldPlatform extends AbstractWidget {
    protected final Font font;
    private @Nullable IMEPreeditOverlay composition;

    UiTextFieldPlatform(Font font, Bounds bounds, Component label) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), label);
        this.font = font;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && visible && isActive() && isMouseOver(event.x(), event.y()) && clearOnRightClick()) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        pointerDown(event.x(), event.hasShiftDown(), doubleClick);
    }
    @Override protected void onDrag(MouseButtonEvent event, double dx, double dy) { pointerDrag(event.x()); }
    @Override public void onRelease(MouseButtonEvent event) { pointerUp(); }
    @Override public void playDownSound(SoundManager soundManager) {}

    @Override
    public boolean charTyped(CharacterEvent event) {
        return event.isAllowedChatCharacter() && input(event.codepointAsString());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return key(event.key(), event.hasControlDownWithQuirk(), event.hasShiftDown(), event.hasAltDown());
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) composition = null;
        Minecraft.getInstance().onTextInputFocusChange(this, focused && isActive());
    }

    @Override
    public boolean preeditUpdated(@Nullable PreeditEvent event) {
        composition = event != null && isFocused() && isActive() ? new IMEPreeditOverlay(event, font, font.lineHeight) : null;
        return true;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        paint(new Canvas() {
            public void box(int x, int y, int w, int h, int radius, int stroke, int edge, int fill) {
                RoundedCorners.draw(graphics, x, y, w, h, radius, stroke, edge, fill);
            }
            public void fill(int x, int y, int right, int bottom, int color) {
                graphics.fill(x, y, right, bottom, color);
            }
            public void text(FormattedCharSequence text, int x, int y, int color) {
                graphics.text(font, text, x, y, color, false);
            }
            public void clip(int x, int y, int right, int bottom) { graphics.enableScissor(x, y, right, bottom); }
            public void unclip() { graphics.disableScissor(); }
        });
        if (isHovered()) graphics.requestCursor(isActive() ? CursorTypes.IBEAM : CursorTypes.NOT_ALLOWED);
        if (isFocused() && isActive() && composition != null) graphics.setPreeditOverlay(composition);
    }

    void tooltipDelay(int millis) { setTooltipDelay(Duration.ofMillis(millis)); }
    boolean composing() { return composition != null; }
    void textInputArea(int x, int y) {
        if (composition != null) composition.updateInputPosition(x, y);
    }
    abstract void pointerDown(double x, boolean extend, boolean doubleClick);
    abstract boolean clearOnRightClick();
    abstract void pointerDrag(double x);
    abstract void pointerUp();
    abstract boolean key(int key, boolean shortcut, boolean shift, boolean alt);
    abstract boolean input(String text);
    abstract void paint(Canvas canvas);

    interface Canvas {
        void box(int x, int y, int w, int h, int radius, int stroke, int edge, int fill);
        void fill(int x, int y, int right, int bottom, int color);
        void text(FormattedCharSequence text, int x, int y, int color);
        void clip(int x, int y, int right, int bottom);
        void unclip();
    }
}
