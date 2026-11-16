// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

abstract class UiCodeEditorPlatform extends AbstractWidget {
    protected final MultiLineEditBox editor;
    UiCodeEditorPlatform(Font font, Bounds bounds, Component label, UiPreferences preferences) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), label);
        editor = new MultiLineEditBox(font, bounds.x() + 1, bounds.y() + 1,
                Math.max(1, bounds.width() - 2), Math.max(1, bounds.height() - 2), Component.empty(), label) {
            @Override protected void renderBackground(GuiGraphics graphics) {}
        };
    }
    @Override public void setFocused(boolean focused) { super.setFocused(focused); if (editor != null) editor.setFocused(focused); }
    @Override public boolean mouseClicked(double x, double y, int button) { return editor.mouseClicked(x, y, button); }
    @Override public boolean mouseReleased(double x, double y, int button) { return editor.mouseReleased(x, y, button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return editor.mouseDragged(x, y, button, dx, dy);
    }
    public boolean mouseScrolled(double x, double y, double delta) { return PlatformUiApi.scrollWidget(editor, x, y, 0, delta); }
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return PlatformUiApi.scrollWidget(editor, x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!isFocused()) return false;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_V && net.minecraft.client.gui.screens.Screen.hasControlDown())
            return pasteExact(net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard());
        return editor.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) { return isFocused() && editor.charTyped(character, modifiers); }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { output.add(NarratedElementType.TITLE, getMessage()); }
    abstract boolean pasteExact(String text);
    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var theme = UiTheme.current();
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), isFocused() ? theme.accent() : theme.border());
        graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, 0xFF101820);
        editor.render(graphics, mouseX, mouseY, partialTick);
    }
}
