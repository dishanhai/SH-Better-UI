// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** Native event/render adapter; the pagination behavior is shared by all loaders. */
abstract class UiPaginationPlatform extends AbstractWidget {
    UiPaginationPlatform(Bounds area, Component name) { super(area.x(), area.y(), area.width(), area.height(), name); }
    @Override public boolean mouseClicked(double x, double y, int button) { return button == 0 && pointerDown(x, y); }
    @Override public boolean mouseReleased(double x, double y, int button) { return button == 0 && pointerUp(x, y); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return button == 0 && pointerDrag(x, y);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) { return key(key); }
    @Override protected void renderWidget(GuiGraphics graphics, int mx, int my, float partialTick) {
        prepare(mx, my);
        for (var cell : cells()) {
            if (cell.button() != null) cell.button().render(graphics, mx, my, partialTick);
            else graphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, cell.label(),
                    cell.area().x() + cell.area().width() / 2, cell.area().y() + (cell.area().height() - 9) / 2,
                    UiTheme.current().text());
        }
    }
    abstract void prepare(int mx, int my);
    abstract java.util.List<UiPagination.Cell> cells();
    abstract boolean pointerDown(double x, double y);
    abstract boolean pointerUp(double x, double y);
    abstract boolean pointerDrag(double x, double y);
    abstract boolean key(int key);
}
