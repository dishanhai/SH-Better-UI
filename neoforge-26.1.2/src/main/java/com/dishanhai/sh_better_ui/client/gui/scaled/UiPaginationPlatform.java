// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

abstract class UiPaginationPlatform extends AbstractWidget {
    UiPaginationPlatform(Bounds area, Component name) { super(area.x(), area.y(), area.width(), area.height(), name); }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        return isValidClickButton(e.buttonInfo()) && pointerDown(e.x(), e.y());
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) {
        return isValidClickButton(e.buttonInfo()) && pointerUp(e.x(), e.y());
    }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        return isValidClickButton(e.buttonInfo()) && pointerDrag(e.x(), e.y());
    }
    @Override public boolean keyPressed(KeyEvent e) { return key(e.key()); }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mx, int my, float partialTick) {
        prepare(mx, my);
        for (var cell : cells()) {
            if (cell.button() != null) cell.button().extractRenderState(graphics, mx, my, partialTick);
            else {
                var font = net.minecraft.client.Minecraft.getInstance().font;
                graphics.text(font, cell.label(), cell.area().x() + (cell.area().width() - font.width(cell.label())) / 2,
                        cell.area().y() + (cell.area().height() - font.lineHeight) / 2, UiTheme.current().text(), false);
            }
        }
    }
    abstract void prepare(int mx, int my);
    abstract java.util.List<UiPagination.Cell> cells();
    abstract boolean pointerDown(double x, double y);
    abstract boolean pointerUp(double x, double y);
    abstract boolean pointerDrag(double x, double y);
    abstract boolean key(int key);
}
