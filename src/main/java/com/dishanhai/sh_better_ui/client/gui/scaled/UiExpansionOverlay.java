package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/** 山海：在原生 Screen 绘制完成后画局部前景，轮廓与输入路由共用同一帧几何。 */
final class UiExpansionOverlay {
    private UiExpansionOverlay() {}

    static void render(GuiGraphics graphics, UiButton source, UiLocalModal.Content content,
                       UiLocalModal.Frame frame, GuiRect outline, Bounds start,
                       float expansion, boolean centeredMenu, int mouseX, int mouseY, float partialTick) {
        if (outline.isEmpty()) return;
        float x = outline.getX(), y = outline.getY(), width = outline.getW(), height = outline.getH();
        graphics.flush();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, 400);
            graphics.enableScissor((int) Math.floor(x), (int) Math.floor(y),
                    (int) Math.ceil(x + width), (int) Math.ceil(y + height));
            try {
                if (!content.renderSurface(graphics, outline, frame)) {
                    drawDefaultSurface(graphics, source, frame, outline, expansion, centeredMenu);
                }
                if (expansion == 1f) {
                    boolean hover = frame.state() == UiExpansionController.State.ACTIVE
                            && UiExpansionController.contains(outline, frame.radius(), mouseX, mouseY);
                    content.render(graphics, frame, hover ? mouseX : -1, hover ? mouseY : -1, partialTick);
                } else {
                    float opacity = content.animatesContents() ? contentOpacity(expansion) : 0f;
                    if (!centeredMenu && opacity < 1f) {
                        drawTitle(graphics, source.getMessage(), outline, start, 1f - opacity);
                    }
                    if (opacity > 0f) drawTransitionContent(graphics, content, frame, outline, opacity, partialTick);
                }
            } finally {
                graphics.disableScissor();
            }
        } finally {
            try {
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        }
    }

    private static void drawDefaultSurface(GuiGraphics graphics, UiButton source, UiLocalModal.Frame frame,
                                           GuiRect outline, float expansion, boolean centeredMenu) {
        if (centeredMenu && UiTheme.current().liquidGlass()) {
            LiquidGlassTheme.centeredMenu(graphics, outline, frame.radius(), frame.preferences(),
                    expansion, frame.focused());
            return;
        }
        int surface = frame.preferences().highContrast() ? DarkModeTheme.backgroundColor() : DarkModeTheme.surfaceColor();
        if (!centeredMenu && expansion < 0.58f && !frame.preferences().highContrast()) {
            surface = GuiMotion.lerpColor(source.expansionFill(frame.nowMs()), surface, expansion / 0.58f);
        }
        int focus = frame.preferences().highContrast() ? DarkModeTheme.textColor() : LiquidGlassTheme.accentColor();
        int edge = DarkModeTheme.hoverBorderColor();
        float highlight = frame.state() == UiExpansionController.State.ACTIVATING ? frame.phaseProgress() : 1f;
        RoundedCorners.draw(graphics, outline.getX(), outline.getY(), outline.getW(), outline.getH(), frame.radius(),
                frame.focused() && frame.preferences().highContrast() ? 2f : 1f,
                frame.focused() ? GuiMotion.lerpColor(edge, focus, highlight) : edge, surface);
    }

    static float contentOpacity(float expansion) {
        return UiExpansionController.menuContentOpacity(expansion);
    }

    private static void drawTitle(GuiGraphics graphics, Component title, GuiRect outline, Bounds source, float opacity) {
        int maxWidth = Math.max(0, Math.min(source.width(), (int) outline.getW()) - 12);
        int alpha = Math.round(opacity * 255f);
        if (maxWidth == 0 || alpha < 4) return;
        var font = Minecraft.getInstance().font;
        var text = Language.getInstance().getVisualOrder(font.substrByWidth(title, maxWidth));
        graphics.drawString(font, text, outline.getX() + (outline.getW() - font.width(text)) * 0.5f,
                outline.getY() + (outline.getH() - font.lineHeight) * 0.5f,
                (DarkModeTheme.textColor() & 0x00FFFFFF) | (alpha << 24), false);
    }

    private static void drawTransitionContent(GuiGraphics graphics, UiLocalModal.Content content,
                                              UiLocalModal.Frame frame, GuiRect outline,
                                              float opacity, float partialTick) {
        var target = frame.bounds();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(outline.getX(), outline.getY(), 0);
            graphics.pose().scale(outline.getW() / target.width(), outline.getH() / target.height(), 1f);
            graphics.pose().translate(-target.x(), -target.y(), 0);
            GuiRenderUtil.withAttentionContent(graphics, opacity, 0,
                    () -> content.render(graphics, frame, -1, -1, partialTick));
            if (opacity >= 1f) graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }
}
