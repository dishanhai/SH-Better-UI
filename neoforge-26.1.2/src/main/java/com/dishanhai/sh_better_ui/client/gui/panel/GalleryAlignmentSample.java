// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame.VisualBounds;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Insets;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.layout.PixelAlignment;
import com.dishanhai.sh_better_ui.client.gui.layout.TextureMetrics;
import com.dishanhai.sh_better_ui.client.gui.layout.VisualAlignment;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.List;

final class GalleryAlignmentSample extends GallerySampleScreen {
    private static final GraphicIcon ICON = GraphicIcon.EDIT;
    private static final int ICON_SIZE = 16;
    private static final int COLLAPSED = 28;
    private static final int NEIGHBOR_WIDTH = 52;
    private static final String LABEL = "sh_better_ui.layout.preview.";

    private final InterruptibleAnimation expansion = new InterruptibleAnimation(Spring.PANEL);
    private AlignmentLayout layout;
    private AlignmentLayout.Node card;
    private AlignmentLayout.Node neighbor;
    private AlignmentLayout.Node icon;
    private AlignmentLayout.Node caption;
    private PixelAlignment grid;
    private Rect stage;
    private Component captionText;
    private UiButton expand;
    private boolean debug;

    GalleryAlignmentSample(DeveloperGalleryScreen parent, String id) { super(parent, id); }

    @Override
    protected void initSample() {
        int y = body.bottom() - 22;
        int half = (body.width() - 5) / 2;
        expand = addButton(new Bounds(body.x(), y, half, 22), expandLabel(), ignored -> toggleCard(), true);
        initialFocus = expand;
        addButton(new Bounds(body.x() + half + 5, y, body.width() - half - 5, 22), debugLabel(), button -> {
            debug = !debug;
            button.setMessage(debugLabel());
        }, false);

        stage = new Rect(body.x(), body.y(), body.width(), Math.min(62, body.height() - 27));
        var window = minecraft.getWindow();
        grid = new PixelAlignment(window.getWidth() / (double) window.getGuiScaledWidth(),
                window.getHeight() / (double) window.getGuiScaledHeight());
        layout = new AlignmentLayout(stage);
        card = layout.node("card", panelFrame(expansion.value()));
        VisualAlignment.align(card).relativeToScreen().horizontal(VisualAlignment.Align.LEFT)
                .vertical(VisualAlignment.Align.TOP).offset(4, 4).apply();
        neighbor = layout.node("neighbor", AlignmentFrame.box(new Rect(stage.x() + 38, stage.y() + 4, NEIGHBOR_WIDTH, panelHeight())));
        neighbor.policies(AlignmentLayout.Axis.HORIZONTAL, 6, List.of(card),
                AlignmentLayout.Strategy.REFLOW, AlignmentLayout.Strategy.CLAMP_TO_PARENT);

        var iconBox = new Rect(0, 0, ICON_SIZE, ICON_SIZE);
        icon = layout.node("icon", card, AlignmentFrame.box(iconBox).withVisual(measureIcon(iconBox))).snap(PixelAlignment.Snap.ORIGIN);
        centerOptically(icon, card);
        captionText = Component.translatable(LABEL + "follower");
        var textBox = new Rect(0, 0, font.width(captionText), font.lineHeight);
        var ink = font.prepareText(captionText.getVisualOrderText(), 0, 0, 0xFFFFFFFF, false, false, 0).bounds();
        var measured = ink == null ? textBox : new Rect(ink.left(), ink.top(), ink.width(), ink.height());
        caption = layout.node("caption", neighbor, AlignmentFrame.box(textBox).withVisual(new VisualBounds(measured)))
                .snap(PixelAlignment.Snap.ORIGIN);
        centerOptically(caption, neighbor);
        layout.resolve();
    }

    private static void centerOptically(AlignmentLayout.Node node, AlignmentLayout.Node parent) {
        VisualAlignment.align(node).relativeTo(parent).horizontal(VisualAlignment.Align.CENTER)
                .vertical(VisualAlignment.Align.CENTER).bounds(VisualAlignment.Bounds.VISUAL).opticalCorrection(true).apply();
    }

    private VisualBounds measureIcon(Rect box) {
        var resource = minecraft.getResourceManager().getResource(ICON.texture());
        if (resource.isEmpty()) return new VisualBounds(box);
        try (var input = resource.get().open(); var image = NativeImage.read(input)) {
            var metrics = TextureMetrics.scan(image.getWidth(), image.getHeight(), 16, (x, y) -> image.getPixel(x, y) >>> 24);
            return metrics.in(box);
        } catch (IOException invalidTexture) {
            return new VisualBounds(box);
        }
    }

    private AlignmentFrame panelFrame(double progress) {
        double end = Math.min(132, stage.width() - NEIGHBOR_WIDTH - 16);
        return AlignmentFrame.box(new Rect(0, 0, Math.round(COLLAPSED + (end - COLLAPSED) * Math.clamp(progress, 0, 1)), panelHeight()));
    }

    private int panelHeight() { return Math.min(30, (int) stage.height() - 8); }

    private Component expandLabel() { return Component.translatable(LABEL + (expansion.target() < 0.5f ? "expand" : "collapse")); }

    private Component debugLabel() { return Component.translatable(LABEL + (debug ? "hide" : "show")); }

    private void toggleCard() {
        expansion.toggle(Util.getMillis());
        expand.setMessage(expandLabel());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!compact && card != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && card.current(grid).hitTest(event.x(), event.y())) {
            toggleCard();
            setInitialFocus(expand);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        card.sample(expansion, Util.getMillis(), preferences.reducedMotion(), this::panelFrame);
        layout.resolve();
        graphics.enableScissor(pixel(stage.x()), pixel(stage.y()), pixel(stage.right()), pixel(stage.bottom()));
        try {
            drawCard(graphics, card, theme.surface() | 0xFF000000);
            drawCard(graphics, neighbor, theme.background() | 0xFF000000);
            Rect iconRect = icon.current(grid).renderBounds();
            ICON.draw(graphics, pixel(iconRect.x()), pixel(iconRect.y()), 1, theme.text(), preferences);
            Rect textRect = caption.current(grid).renderBounds();
            graphics.text(font, captionText, pixel(textRect.x()), pixel(textRect.y()), theme.text(), false);
            if (debug) {
                graphics.nextStratum();
                for (var node : layout.nodes()) drawBounds(graphics, node.current(grid));
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawCard(GuiGraphicsExtractor graphics, AlignmentLayout.Node node, int fill) {
        Rect rect = node.current(grid).renderBounds();
        graphics.fill(pixel(rect.x()), pixel(rect.y()), pixel(rect.right()), pixel(rect.bottom()), fill);
        outline(graphics, rect, theme.text());
    }

    private static void drawBounds(GuiGraphicsExtractor graphics, AlignmentLayout.View view) {
        outline(graphics, view.layoutBounds(), 0xFFFFD073);
        outline(graphics, view.hitBounds().outset(Insets.all(1)), 0xFF64D1FF);
        outline(graphics, view.visualBounds(), 0xFF8BFF9B);
        int x = pixel(view.opticalCenter().x()), y = pixel(view.opticalCenter().y());
        graphics.fill(x - 2, y, x + 3, y + 1, 0xFFFF90C9);
        graphics.fill(x, y - 2, x + 1, y + 3, 0xFFFF90C9);
    }

    private static void outline(GuiGraphicsExtractor graphics, Rect rect, int color) {
        if (!rect.empty()) graphics.outline(pixel(rect.x()), pixel(rect.y()), Math.max(1, pixel(rect.width())),
                Math.max(1, pixel(rect.height())), color);
    }

    private static int pixel(double value) { return (int) Math.round(value); }
}
