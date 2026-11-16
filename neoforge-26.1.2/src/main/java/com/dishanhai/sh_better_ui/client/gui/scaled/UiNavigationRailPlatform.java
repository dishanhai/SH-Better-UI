// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
abstract class UiNavigationRailPlatform extends AbstractWidget {
    UiNavigationRailPlatform(Bounds area, Component name) { super(area.x(), area.y(), area.width(), area.height(), name); }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean doubleClick) { return isValidClickButton(e.buttonInfo()) && pointerDown(e.x(), e.y()); }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent e) { return isValidClickButton(e.buttonInfo()) && pointerUp(e.x(), e.y()); }
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent e, double dx, double dy) { return isValidClickButton(e.buttonInfo()) && pointerDrag(e.x(), e.y()); }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent e) { return key(e.key()); }
    @Override protected void extractWidgetRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mx, int my, float partialTick) {
        prepare(mx, my);
        for (var cell : cells()) cell.button().extractRenderState(graphics, mx, my, partialTick);
        var bar = indicatorBounds(); graphics.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), indicatorColor());
    }
    abstract void prepare(int mx, int my);
    abstract java.util.List<UiNavigationRail.Cell> cells();
    abstract Bounds indicatorBounds();
    abstract int indicatorColor();
    abstract boolean pointerDown(double x, double y);
    abstract boolean pointerUp(double x, double y);
    abstract boolean pointerDrag(double x, double y);
    abstract boolean key(int key);
}
