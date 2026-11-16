// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

abstract class UiIngredientSlotsPlatform extends AbstractWidget {
    UiIngredientSlotsPlatform(Bounds area, Component name) { super(area.x(), area.y(), area.width(), area.height(), name); }
    @Override public boolean mouseClicked(double x, double y, int button) { return pointerDown(x, y, button); }
    @Override public boolean mouseReleased(double x, double y, int button) { return button == 0 && pointerUp(x, y); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return button == 0 && pointerDrag(x, y);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) { return key(key, modifiers); }
    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        paint(new UiIngredientSlots.Canvas() {
            @Override public void fill(int x, int y, int right, int bottom, int color) { graphics.fill(x, y, right, bottom, color); }
            @Override public void item(Font font, ItemStack stack, int x, int y) {
                if (stack.isEmpty()) return;
                graphics.renderItem(stack, x, y);
                graphics.renderItemDecorations(font, stack, x, y);
            }
            @Override public void tooltip(Font font, Component label, int x, int y) { graphics.renderTooltip(font, label, x, y); }
        }, mouseX, mouseY);
    }
    void beginCapture(Screen owner, int button) {
        UiPointerCapture.start(owner, button, new UiPointerCapture.Handler() {
            @Override public void drag(double x, double y) { pointerDrag(x, y); }
            @Override public void release(double x, double y) { pointerUp(x, y); }
            @Override public void cancel() { capturedCancel(); }
        });
    }
    void cancelCapture(Screen owner) { UiPointerCapture.cancel(owner); }
    abstract void capturedCancel();
    abstract boolean pointerDown(double x, double y, int button);
    abstract boolean pointerDrag(double x, double y);
    abstract boolean pointerUp(double x, double y);
    abstract boolean key(int key, int modifiers);
    abstract void paint(UiIngredientSlots.Canvas canvas, int mouseX, int mouseY);
}
