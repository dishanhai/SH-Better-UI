// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

abstract class UiJellyRadioPlatform extends AbstractWidget {
    private Tooltip currentTooltip;

    UiJellyRadioPlatform(Bounds area, Component name) {
        super(area.x(), area.y(), area.width(), area.height(), name);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return button == 0 && pointerDown(x, y);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return button == 0 && pointerUp(x, y);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return button == 0 && pointerDragged(x, y);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return key(key);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        paint(canvas(graphics), mouseX, mouseY);
    }

    static Canvas canvas(GuiGraphics graphics) {
        return new Canvas() {
            public void box(float x, float y, float width, float height, float radius, int stroke, int edge, int fill) {
                RoundedCorners.draw(graphics, x, y, width, height, radius, stroke, edge, fill);
            }

            public void text(Font font, FormattedCharSequence text, int x, int y, int ink) {
                graphics.drawString(font, text, x, y, ink, false);
            }

            public void icon(GraphicIcon icon, int x, int y, int ink, UiPreferences preferences) {
                icon.draw(graphics, x, y, 1, ink, preferences);
            }
        };
    }

    Tooltip tooltip(Component label) { return Tooltip.create(label); }
    void tooltipDelay(int millis) { PlatformUiApi.setTooltipDelay(this, millis); }

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
