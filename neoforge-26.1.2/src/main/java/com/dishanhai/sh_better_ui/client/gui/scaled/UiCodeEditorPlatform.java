// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;

abstract class UiCodeEditorPlatform extends AbstractWidget {
    protected final MultiLineEditBox editor;
    UiCodeEditorPlatform(Font font, Bounds bounds, Component label, UiPreferences preferences) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), label);
        editor = MultiLineEditBox.builder().setX(bounds.x() + 1).setY(bounds.y() + 1)
                .setPlaceholder(Component.empty()).setTextColor(UiTheme.current().text())
                .setShowBackground(false).build(font, Math.max(1, bounds.width() - 2), Math.max(1, bounds.height() - 2), label);
    }
    @Override public void setFocused(boolean focused) { super.setFocused(focused); if (editor != null) editor.setFocused(focused); }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) { return editor.mouseClicked(e, doubleClick); }
    @Override public boolean mouseReleased(MouseButtonEvent e) { return editor.mouseReleased(e); }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return editor.mouseDragged(e, dx, dy); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return editor.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if (!isFocused()) return false;
        if (e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_V && e.hasControlDownWithQuirk())
            return pasteExact(net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard());
        return editor.keyPressed(e);
    }
    @Override public boolean charTyped(CharacterEvent e) { return isFocused() && editor.charTyped(e); }
    @Override public boolean preeditUpdated(PreeditEvent e) { return isFocused() && editor.preeditUpdated(e); }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { output.add(NarratedElementType.TITLE, getMessage()); }
    abstract boolean pasteExact(String text);
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var theme = UiTheme.current();
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), isFocused() ? theme.accent() : theme.border());
        graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, theme.surface() | 0xFF000000);
        editor.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
