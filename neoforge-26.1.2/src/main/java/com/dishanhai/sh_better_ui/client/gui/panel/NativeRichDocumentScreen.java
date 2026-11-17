// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.visual.NativeRichDocumentView;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.ClickEvent;

/** Read-only structured document host. Resource textures remain owned by Minecraft. */
public final class NativeRichDocumentScreen extends Screen {
    private final Screen parent;
    private final RichDocument document;
    private NativeRichDocumentView view;
    private int scroll, maxScroll, left, top, bottom, contentWidth;
    private net.minecraft.locale.Language language;

    public NativeRichDocumentScreen(Screen parent, Component title, RichDocument document) {
        super(java.util.Objects.requireNonNull(title).copy());
        this.parent = parent;
        this.document = java.util.Objects.requireNonNull(document);
    }

    @Override protected void init() {
        contentWidth = Math.max(32, Math.min(520, width - 32));
        left = Math.max(0, (width - contentWidth) / 2);
        top = 56;
        bottom = Math.max(top, height - 34);
        view = new NativeRichDocumentView(font, document, contentWidth);
        language = net.minecraft.locale.Language.getInstance();
        addRenderableWidget(Button.builder(Component.translatable("sh_better_ui.native.rich_document.refresh"),
                button -> rebuildWidgets()).bounds(8, 28, Math.min(160, Math.max(20, width - 16)), 20).build());
        maxScroll = Math.max(0, view.height() - (bottom - top));
        scroll = Math.min(scroll, maxScroll);
        int buttonWidth = Math.min(100, Math.max(20, (width - 24) / 2));
        var back = addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(8, Math.max(0, height - 26), buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("sh_better_ui.native.rich_document.copy"), b -> minecraft.keyboardHandler.setClipboard(view.narration().getString()))
                .bounds(16 + buttonWidth, Math.max(0, height - 26), buttonWidth, 20).build());
        setInitialFocus(back);
    }

    // This document supplies its own opaque background before extracting the content.
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xee151b25);
        graphics.centeredText(font, title, width / 2, 10, 0xffe7edf7);
        graphics.enableScissor(left, top, Math.min(width, left + contentWidth), bottom);
        view.render(graphics, left, top - scroll, top, bottom,
                id -> minecraft.getResourceManager().getResource(id).isPresent());
        graphics.disableScissor();
        if (maxScroll > 0 && bottom > top) {
            int track = bottom - top;
            int thumb = Math.max(8, track * track / Math.max(1, view.height()));
            int y = top + (track - thumb) * scroll / maxScroll;
            graphics.fill(Math.min(width - 3, left + contentWidth + 3), y,
                    Math.min(width, left + contentWidth + 5), y + thumb, 0xff879dbd);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void tick() {
        super.tick();
        if (language != net.minecraft.locale.Language.getInstance()) rebuildWidgets();
    }

    public boolean mouseScrolled(double x, double y, double dy) {
        if (x < left || x >= left + contentWidth || y < top || y >= bottom || dy == 0) return false;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (dy * 24)));
        return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) { return mouseScrolled(x, y, dy); }
    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        int delta = switch (key) {
            case GLFW.GLFW_KEY_DOWN -> 18;
            case GLFW.GLFW_KEY_UP -> -18;
            case GLFW.GLFW_KEY_PAGE_DOWN -> Math.max(18, bottom - top - 18);
            case GLFW.GLFW_KEY_PAGE_UP -> -Math.max(18, bottom - top - 18);
            case GLFW.GLFW_KEY_HOME -> -maxScroll;
            case GLFW.GLFW_KEY_END -> maxScroll;
            default -> 0;
        };
        if (delta != 0) { scroll = Math.max(0, Math.min(maxScroll, scroll + delta)); return true; }
        return super.keyPressed(event);
    }
    @Override public Component getNarrationMessage() {
        return net.minecraft.network.chat.CommonComponents.joinForNarration(getTitle(), view == null ? Component.empty() : view.narration());
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() {
        if (com.dishanhai.sh_better_ui.kernel.internal.NativePort.screen(minecraft) == this)
            com.dishanhai.sh_better_ui.kernel.internal.NativePort.show(minecraft, parent);
    }
}
