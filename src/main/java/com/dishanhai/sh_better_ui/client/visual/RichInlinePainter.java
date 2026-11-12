// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiStateGuard;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Fragment;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Rect;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Button;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Entity;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Frames;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Icon;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode.Slider;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Optional;

/** Fixed native renderers called inside the host UUK visual pass. */
final class RichInlinePainter {
    private static final float[][] CHECK = {{3, 8}, {6, 11}, {13, 4}};
    private static final float[][] CHEVRON = {{6, 3}, {11, 8}, {6, 13}};
    private static final float[][] STAR = {{8, 2}, {10, 6}, {14, 6}, {11, 9}, {12, 13},
            {8, 11}, {4, 13}, {5, 9}, {2, 6}, {6, 6}, {8, 2}};

    private RichInlinePainter() {}

    static boolean paint(GuiGraphics graphics, Font font, Fragment fragment, Map<UiId, RichTextImages.Image> textures,
                         long frameTime, boolean reducedMotion, RichInlineInteraction.PaintState state,
                         RichInlineInteraction interaction) {
        var node = (RichObjectNode) fragment.node();
        var box = fragment.bounds();
        if (box.width() == 0 || box.height() == 0) return true;
        boolean drawn;
        if (node.content() instanceof Frames frames) {
            var image = textures.get(frames.textureAt(frameTime, reducedMotion));
            drawn = image != null;
            if (drawn) RichTextView.paintImage(graphics, box, image);
        } else if (node.content() instanceof Icon icon) {
            paintIcon(graphics, box, icon);
            drawn = true;
        } else if (node.content() instanceof Button button) {
            paintButton(graphics, font, box, button, state);
            drawn = true;
        } else if (node.content() instanceof Slider slider) {
            paintSlider(graphics, font, box, slider, state);
            drawn = true;
        } else if (node.content() instanceof Entity entity) {
            drawn = paintEntity(graphics, box, entity, interaction.objects());
        } else {
            var ingredient = interaction.ingredient(node);
            drawn = ingredient.isPresent();
            if (drawn) paintIngredient(graphics, box, ingredient.orElseThrow());
        }
        if (state.dropTarget() || state.focused() && !(node.content() instanceof Button)
                && !(node.content() instanceof Slider)) {
            outline(graphics, box, DarkModeTheme.accentColor());
        }
        return drawn;
    }

    private static void paintButton(GuiGraphics graphics, Font font, Rect box, Button button,
                                    RichInlineInteraction.PaintState state) {
        paintControl(graphics, box, state);
        label(graphics, font, box, button.label(), state.enabled() ? DarkModeTheme.textColor() : DarkModeTheme.mutedTextColor(),
                box.y() + (box.height() - font.lineHeight) / 2);
    }

    private static void paintSlider(GuiGraphics graphics, Font font, Rect box, Slider slider,
                                    RichInlineInteraction.PaintState state) {
        paintControl(graphics, box, state);
        if (box.width() < 3 || box.height() < 3) return;
        int inset = RichInlineInteraction.sliderInset(box.width());
        int left = box.x() + inset;
        int right = box.x() + box.width() - inset;
        double fraction = (state.sliderValue() - (double) slider.min()) / ((long) slider.max() - slider.min());
        int thumb = Math.min(right - 1, left + (int) Math.round(fraction * (right - left)));
        boolean labelFits = box.height() >= font.lineHeight + 7;
        int y = labelFits ? box.y() + box.height() - 4 : box.y() + box.height() / 2;
        graphics.fill(left, y, right, y + 1, DarkModeTheme.hoverBorderColor());
        graphics.fill(left, y, thumb + 1, y + 1, DarkModeTheme.accentColor());
        graphics.fill(Math.max(box.x() + 1, thumb - 1), y - 1,
                Math.min(box.x() + box.width() - 1, thumb + 2), y + 2, DarkModeTheme.textColor());
        if (labelFits) label(graphics, font, box, Integer.toString(state.sliderValue()),
                DarkModeTheme.textColor(), box.y() + 2);
    }

    private static void paintControl(GuiGraphics graphics, Rect box, RichInlineInteraction.PaintState state) {
        int border = state.focused() ? DarkModeTheme.accentColor()
                : state.hovered() ? DarkModeTheme.hoverBorderColor() : DarkModeTheme.borderColor();
        int fill = state.pressed() ? DarkModeTheme.backgroundColor() : DarkModeTheme.surfaceColor();
        int x = box.x(), y = box.y(), right = x + box.width(), bottom = y + box.height();
        graphics.fill(x, y, right, bottom, border);
        if (box.width() > 2 && box.height() > 2) graphics.fill(x + 1, y + 1, right - 1, bottom - 1, fill);
    }

    private static void outline(GuiGraphics graphics, Rect box, int argb) {
        int x = box.x(), y = box.y(), right = x + box.width(), bottom = y + box.height();
        graphics.fill(x, y, right, y + 1, argb);
        graphics.fill(x, bottom - 1, right, bottom, argb);
        graphics.fill(x, y, x + 1, bottom, argb);
        graphics.fill(right - 1, y, right, bottom, argb);
    }

    private static void label(GuiGraphics graphics, Font font, Rect box, String label, int argb, int y) {
        int width = box.width() - 4;
        if (width <= 0 || box.height() < font.lineHeight + 2) return;
        var text = font.substrByWidth(FormattedText.of(label), width);
        var sequence = Language.getInstance().getVisualOrder(text);
        graphics.drawString(font, sequence, box.x() + (box.width() - font.width(sequence)) / 2, y, argb, false);
    }

    private static void paintIcon(GuiGraphics graphics, Rect box, Icon icon) {
        var points = switch (icon.symbol()) {
            case CHECK -> CHECK;
            case CHEVRON -> CHEVRON;
            case STAR -> STAR;
        };
        drawInBox(graphics, box, 16, () -> {
            for (int index = 1; index < points.length; index++) {
                var a = points[index - 1];
                var b = points[index];
                float dx = b[0] - a[0], dy = b[1] - a[1];
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(a[0], a[1], 0);
                    graphics.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
                    graphics.fill(0, -1, (int) Math.ceil(Math.hypot(dx, dy)), 1, DarkModeTheme.accentColor());
                } finally {
                    graphics.pose().popPose();
                }
            }
        });
    }

    private static void paintIngredient(GuiGraphics graphics, Rect box,
                                        com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient ingredient) {
        drawInBox(graphics, box, 16, () -> ingredient.render(graphics, 0, 0));
    }

    private static void drawInBox(GuiGraphics graphics, Rect box, int nativeSize, Runnable draw) {
        float scale = Math.min(box.width(), box.height()) / (float) nativeSize;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(box.x() + (box.width() - scale * nativeSize) * 0.5f,
                    box.y() + (box.height() - scale * nativeSize) * 0.5f, 0);
            graphics.pose().scale(scale, scale, 1);
            draw.run();
        } finally {
            graphics.pose().popPose();
        }
    }

    private static boolean paintEntity(GuiGraphics graphics, Rect box, Entity node, RichInlineObjects objects) {
        var preview = objects.entity(node.type()).orElse(null);
        if (preview == null || box.height() < 3 || box.width() < 3) return false;
        int scale = (int) Math.floor(Math.min((box.width() - 2f) / Math.max(0.1f, preview.getBbWidth()),
                (box.height() - 2f) / Math.max(0.1f, preview.getBbHeight())));
        if (scale <= 0) return false;
        graphics.flush();
        try {
            GuiStateGuard.draw(() -> {
                // Separate pose stack keeps even a failing third-party model away from sibling fragments.
                var fork = new GuiGraphics(Minecraft.getInstance(), graphics.bufferSource());
                fork.pose().last().pose().set(graphics.pose().last().pose());
                fork.pose().last().normal().set(graphics.pose().last().normal());
                applyEntityClip(graphics, box);
                try {
                    RichInlinePlatform.entity(fork, preview, box.x() + box.width() / 2, box.y() + box.height() - 1, scale);
                } finally {
                    try {
                        fork.flush();
                    } finally {
                        graphics.disableScissor();
                    }
                }
            });
            return true;
        } catch (RuntimeException error) {
            Minecraft.getInstance().getEntityRenderDispatcher().setRenderShadow(true);
            Lighting.setupFor3DItems();
            objects.disableEntity(node.type());
            ShBetterUi.LOGGER.debug("Could not render inline entity preview {}", node.type(), error);
            return false;
        }
    }

    private static void applyEntityClip(GuiGraphics graphics, Rect box) {
        var matrix = graphics.pose().last().pose();
        var a = matrix.transformPosition(box.x(), box.y(), 0, new Vector3f());
        var b = matrix.transformPosition(box.x() + box.width(), box.y(), 0, new Vector3f());
        var c = matrix.transformPosition(box.x(), box.y() + box.height(), 0, new Vector3f());
        var d = matrix.transformPosition(box.x() + box.width(), box.y() + box.height(), 0, new Vector3f());
        graphics.enableScissor((int) Math.floor(Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x))),
                (int) Math.floor(Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y))),
                (int) Math.ceil(Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x))),
                (int) Math.ceil(Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y))));
    }
}
