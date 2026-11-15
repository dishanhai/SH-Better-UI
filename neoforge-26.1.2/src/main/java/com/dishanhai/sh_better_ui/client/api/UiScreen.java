// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSloshGauge;
import com.dishanhai.sh_better_ui.kernel.internal.NativePort;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Native Screen with content and foreground extraction callbacks. */
public final class UiScreen extends Screen {
    private Screen parent;
    private final boolean parentSpecified;
    private final boolean pausesGame;
    private final Consumer<UiScreen> init;
    private final Consumer<UiScreen> tick;
    private final Consumer<UiScreen> removed;
    private final Draw content;
    private final Draw foreground;

    UiScreen(Component title, Screen parent, boolean parentSpecified, boolean pausesGame, Consumer<UiScreen> init,
             Consumer<UiScreen> tick, Consumer<UiScreen> removed, Draw content, Draw foreground) {
        super(title);
        this.parent = parent;
        this.parentSpecified = parentSpecified;
        this.pausesGame = pausesGame;
        this.init = init;
        this.tick = tick;
        this.removed = removed;
        this.content = content;
        this.foreground = foreground;
    }

    public void open() {
        var client = Minecraft.getInstance();
        if (!parentSpecified && NativePort.screen(client) != this) parent = NativePort.screen(client);
        NativePort.show(client, this);
    }

    @Override public void onClose() { NativePort.show(Minecraft.getInstance(), parent); }
    @Override public boolean isPauseScreen() { return pausesGame; }
    @Override protected void init() { init.accept(this); }
    @Override protected void repositionElements() {
        if (getFocused() instanceof UiSloshGauge gauge) gauge.cancelDrag();
        rebuildWidgets();
    }
    @Override public void tick() { tick.accept(this); }
    @Override public void removed() {
        try {
            if (getFocused() instanceof UiSloshGauge gauge) gauge.cancelDrag();
        } finally {
            removed.accept(this);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && getFocused() instanceof UiSloshGauge gauge && gauge.cancelDrag()) {
            setDragging(false);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (getFocused() instanceof UiSloshGauge gauge && gauge.mouseReleased(event)) {
            setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
    }

    public Bounds viewport() { return new Bounds(0, 0, width, height); }
    public <T extends GuiEventListener & Renderable & NarratableEntry> T add(T widget) {
        return addRenderableWidget(widget);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var frame = new UiFrame(graphics, viewport(), mouseX, mouseY, UiPreferences.configured(),
                System.nanoTime() / 1_000_000, partialTick);
        content.draw(this, frame);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        foreground.draw(this, frame);
    }

    @FunctionalInterface public interface Draw { void draw(UiScreen screen, UiFrame frame); }
}
