// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.time.Duration;

abstract class UiJellyRadioPlatform extends AbstractWidget {
    private Tooltip currentTooltip;

    UiJellyRadioPlatform(Bounds area, Component name) {
        super(area.x(), area.y(), area.width(), area.height(), name);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return isValidClickButton(event.buttonInfo()) && pointerDown(event.x(), event.y());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return isValidClickButton(event.buttonInfo()) && pointerUp(event.x(), event.y());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return isValidClickButton(event.buttonInfo()) && pointerDragged(event.x(), event.y());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return key(event.key());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        paint(new Canvas() {
            public void box(float x, float y, float width, float height, float radius, int stroke, int edge, int fill) {
                RoundedCorners.draw(graphics, x, y, width, height, radius, stroke, edge, fill);
            }

            public void text(Font font, FormattedCharSequence text, int x, int y, int ink) {
                graphics.text(font, text, x, y, ink, false);
            }

            public void icon(GraphicIcon icon, int x, int y, int ink, UiPreferences preferences) {
                icon.draw(graphics, x, y, 1, ink, preferences);
            }
        }, mouseX, mouseY);
    }

    Tooltip tooltip(Component label) { return Tooltip.create(label, null); }
    void tooltipDelay(int millis) { setTooltipDelay(Duration.ofMillis(millis)); }

    void showTooltip(Tooltip tooltip) {
        if (currentTooltip == tooltip) return;
        currentTooltip = tooltip;
        setTooltip(tooltip);
    }

    abstract boolean pointerDown(double x, double y);
    abstract boolean pointerUp(double x, double y);
    abstract boolean pointerDragged(double x, double y);
    abstract boolean key(int key);
    abstract void paint(Canvas canvas, int mouseX, int mouseY);

    interface Canvas {
        void box(float x, float y, float width, float height, float radius, int stroke, int edge, int fill);
        void text(Font font, FormattedCharSequence text, int x, int y, int ink);
        void icon(GraphicIcon icon, int x, int y, int ink, UiPreferences preferences);
    }
}
