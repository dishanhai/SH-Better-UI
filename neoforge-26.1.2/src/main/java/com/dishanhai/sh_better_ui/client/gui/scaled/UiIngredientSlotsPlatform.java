// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

abstract class UiIngredientSlotsPlatform extends AbstractWidget {
    UiIngredientSlotsPlatform(Bounds area, Component name) { super(area.x(), area.y(), area.width(), area.height(), name); }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) { return pointerDown(e.x(), e.y(), e.button()); }
    @Override public boolean mouseReleased(MouseButtonEvent e) { return e.button() == 0 && pointerUp(e.x(), e.y()); }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return e.button() == 0 && pointerDrag(e.x(), e.y()); }
    @Override public boolean keyPressed(KeyEvent e) { return key(e.key(), e.modifiers()); }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        paint(new UiIngredientSlots.Canvas() {
            @Override public void fill(int x, int y, int right, int bottom, int color) { graphics.fill(x, y, right, bottom, color); }
            @Override public void item(Font font, ItemStack stack, int x, int y) {
                if (stack.isEmpty()) return;
                graphics.item(stack, x, y);
                if (stack.getCount() > 1) {
                    var count = Component.literal(Integer.toString(stack.getCount()));
                    graphics.text(font, count, x + 17 - font.width(count), y + 9, 0xFFFFFFFF, true);
                }
            }
            @Override public void tooltip(Font font, Component label, int x, int y) {
                graphics.setTooltipForNextFrame(font.split(label, Math.max(1, Math.min(170, graphics.guiWidth() - 12))), x, y);
            }
        }, mouseX, mouseY);
    }
    // The 26 port has no UiPointerCapture yet; the focused native widget receives drag/release events.
    void beginCapture(Screen owner, int button) {}
    void cancelCapture(Screen owner) {}
    abstract void capturedCancel();
    abstract boolean pointerDown(double x, double y, int button);
    abstract boolean pointerDrag(double x, double y);
    abstract boolean pointerUp(double x, double y);
    abstract boolean key(int key, int modifiers);
    abstract void paint(UiIngredientSlots.Canvas canvas, int mouseX, int mouseY);
}
