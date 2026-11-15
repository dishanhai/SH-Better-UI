// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Util;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 山海：可从 UUK Controls API 挂载的浮动文件夹。 */
public final class UiFolderFloat<T> extends AbstractWidget {
    private final FolderFloatControl<T> control;
    private final Font font;
    private BooleanSupplier inputEnabled = () -> true;

    public UiFolderFloat(Font font, Bounds area, Component label, Component sublabel,
                         List<FolderFloatControl.Item<T>> items, FolderFloatControl.Options options,
                         UiPreferences preferences, BiConsumer<T, Integer> onSelect, Consumer<Boolean> onOpenChange) {
        super(area.x(), area.y(), area.width(), area.height(), label);
        this.font = font;
        control = new FolderFloatControl<>(font, area.width(), area.height(), label, sublabel,
                items, options, preferences, onSelect, onOpenChange);
    }

    public boolean isOpen() { return control.isOpen(); }
    public void setSublabel(Component text) { control.setSublabel(text); }
    public void setInputEnabled(BooleanSupplier enabled) { inputEnabled = Objects.requireNonNull(enabled); }

    public void cancelInteraction() {
        control.cancel();
    }

    private boolean interactive() {
        return visible && active && Minecraft.getInstance().isWindowActive() && inputEnabled.getAsBoolean();
    }

    @Override
    public boolean isMouseOver(double x, double y) {
        return interactive() && control.hit(x - getX(), y - getY());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !isMouseOver(event.x(), event.y())) return false;
        if (!control.press(event.x() - getX(), event.y() - getY(), Util.getMillis())) control.toggle();
        playDownSound(Minecraft.getInstance().getSoundManager());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !control.isPressed()) return false;
        if (interactive()) control.drag(event.x() - getX(), event.y() - getY(), Util.getMillis());
        else cancelInteraction();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !control.isPressed()) return false;
        if (interactive()) control.release(event.x() - getX(), event.y() - getY(), Util.getMillis());
        else cancelInteraction();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (!isFocused() || !interactive()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE && control.isPressed()) {
            cancelInteraction();
            return true;
        }
        boolean handled = switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> control.dismiss();
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> control.moveFocus(-1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN -> control.moveFocus(1);
            case GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> control.activate();
            default -> false;
        };
        if (handled) Minecraft.getInstance().getNarrator().saySystemNow(control.narration());
        return handled;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) cancelInteraction();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, control.narration());
        output.add(NarratedElementType.USAGE, FolderFloatControl.usage());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean enabled = interactive();
        if (!enabled) cancelInteraction();
        int x = getX(), y = getY();
        control.update(mouseX - x, mouseY - y, Util.getMillis(), enabled);
        UiTheme theme = UiTheme.current();
        FolderFloatControl.ShapePainter shape = (sx, sy, width, height, radius, edge, fill) ->
                RoundedCorners.draw(graphics, sx, sy, width, height, radius, 1, edge, fill);
        FolderFloatControl.TextPainter text = (line, tx, ty, color) -> graphics.text(font, line, tx, ty, color, false);
        graphics.enableScissor(x, y, x + getWidth(), y + control.cloudHeight());
        try {
            control.drawPills(x, y, mouseX - x, mouseY - y, isFocused(), theme, shape, text);
        } finally {
            graphics.disableScissor();
        }
        control.drawFolder(x, y, mouseX - x, mouseY - y, isFocused(), theme, shape, text);
    }
}
