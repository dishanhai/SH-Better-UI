// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

abstract class UiSwipeRowPlatform extends AbstractWidget {
    UiSwipeRowPlatform(Bounds area, Component label) {
        super(area.x(), area.y(), area.width(), area.height(), label);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return event.button() == 0 && pointerDown(event.x(), event.y());
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return event.button() == 0 && pointerDragged(event.x(), event.y());
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return event.button() == 0 && pointerUp(event.x(), event.y());
    }

    @Override public boolean keyPressed(KeyEvent event) { return key(event.key()); }
    @Override public boolean keyReleased(KeyEvent event) { return releaseKey(event.key()); }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        paint(new Canvas() {
            public void box(float x, float y, float width, float height, float radius, int stroke, int edge, int fill) {
                RoundedCorners.draw(graphics, x, y, width, height, radius, stroke, edge, fill);
            }

            public void clip(int x, int y, int right, int bottom) { graphics.enableScissor(x, y, right, bottom); }
            public void unclip() { graphics.disableScissor(); }

            public void text(Font font, FormattedCharSequence text, int x, int y, int ink) {
                graphics.text(font, text, x, y, ink, false);
            }

            public void icon(GraphicIcon icon, int x, int y, int ink, UiPreferences preferences) {
                icon.draw(graphics, x, y, 1, ink, preferences);
            }
        }, mouseX, mouseY);
    }

    abstract boolean pointerDown(double x, double y);
    abstract boolean pointerDragged(double x, double y);
    abstract boolean pointerUp(double x, double y);
    abstract boolean key(int key);
    abstract boolean releaseKey(int key);
    abstract void paint(Canvas canvas, int mouseX, int mouseY);

    interface Canvas {
        void box(float x, float y, float width, float height, float radius, int stroke, int edge, int fill);
        void clip(int x, int y, int right, int bottom);
        void unclip();
        void text(Font font, FormattedCharSequence text, int x, int y, int ink);
        void icon(GraphicIcon icon, int x, int y, int ink, UiPreferences preferences);
    }
}
