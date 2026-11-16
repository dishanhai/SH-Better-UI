// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
abstract class UiNavigationRailPlatform extends AbstractWidget {
    UiNavigationRailPlatform(Bounds area, Component name) { super(area.x(), area.y(), area.width(), area.height(), name); }
    @Override public boolean mouseClicked(double x, double y, int button) { return button == 0 && pointerDown(x, y); }
    @Override public boolean mouseReleased(double x, double y, int button) { return button == 0 && pointerUp(x, y); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return button == 0 && pointerDrag(x, y); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) { return key(key); }
    @Override protected void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mx, int my, float partialTick) {
        prepare(mx, my);
        for (var cell : cells()) cell.button().render(graphics, mx, my, partialTick);
        var bar = indicatorBounds(); graphics.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), indicatorColor());
    }
    static long navigationNowMs() { return PageTransition.nowMs(); }
    abstract void prepare(int mx, int my);
    abstract java.util.List<UiNavigationRail.Cell> cells();
    abstract Bounds indicatorBounds();
    abstract int indicatorColor();
    abstract boolean pointerDown(double x, double y);
    abstract boolean pointerUp(double x, double y);
    abstract boolean pointerDrag(double x, double y);
    abstract boolean key(int key);
}
