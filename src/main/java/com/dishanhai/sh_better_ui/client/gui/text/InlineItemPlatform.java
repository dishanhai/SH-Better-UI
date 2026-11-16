// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIconShader;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.mojang.math.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

/** Version-specific native GUI rendering; layout uses the matching space-font advance. */
public final class InlineItemPlatform {
    private static final ResourceLocation FONT = ResourceLocation.tryParse("sh_better_ui:inline_items");
    private InlineItemPlatform() {}

    static Style style(Style base) { return base.withFont(FONT); }
    static boolean isItemFont(Style style) { return FONT.equals(style.getFont()); }

    /** Reuses the caller's pose and buffers, including direct Font batches used by HUD effects. */
    public static void draw(Font font, FormattedCharSequence text, float x, float y, Matrix4f pose,
                            MultiBufferSource buffers) {
        if (text.accept((index, style, cp) -> !UiRichText.isObject(style, cp))) return;
        if (!(buffers instanceof MultiBufferSource.BufferSource source)) return;
        var graphics = new GuiGraphics(Minecraft.getInstance(), source);
        graphics.pose().last().pose().set(pose);
        draw(graphics, font, text, x, y);
    }

    public static void draw(GuiGraphics graphics, Font font, FormattedCharSequence text, float x, float y) {
        if (text.accept((index, style, cp) -> !UiRichText.isObject(style, cp))) return;
        float[] cursor = {x};
        text.accept((index, style, cp) -> {
            String id = UiRichText.itemId(style, cp);
            String texture = UiRichText.imageTexture(style, cp);
            if (id != null || texture != null) {
                var preferences = UiPreferences.configured();
                var renderer = UiRichText.renderer(style, cp);
                var pose = graphics.pose();
                pose.pushPose();
                try {
                    pose.translate(cursor[0], y, 0);
                    if (renderer.motion() != null && !preferences.reducedMotion() && !preferences.highContrast()) {
                        var motion = renderer.motion().sample(PageTransition.nowMs(), 9, false);
                        pose.translate(4.5f, 4.5f + motion.offsetY(), 0);
                        pose.mulPose(Axis.ZP.rotationDegrees(motion.rotationDegrees()));
                        pose.scale(motion.scale(), motion.scale(), 1);
                        pose.translate(-4.5f, -4.5f, 0);
                    }
                    pose.scale(9f / 16, 9f / 16, 1);
                    if (texture != null) {
                        var icon = new GraphicIcon(ResourceLocation.tryParse(texture));
                        if (!preferences.highContrast()) icon = icon.withShader(shader(renderer.effect()));
                        icon.draw(graphics, 0, 0, 1, 0xffffffff, preferences);
                    } else {
                        var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
                        graphics.renderItem(new ItemStack(item == Items.AIR ? Items.BARRIER : item), 0, 0);
                    }
                } finally {
                    pose.popPose();
                }
            }
            cursor[0] += font.getSplitter().stringWidth(FormattedCharSequence.codepoint(cp, style));
            return true;
        });
    }

    private static GraphicIconShader shader(UiRichIconRenderers.Effect effect) {
        return switch (effect) {
            case NONE -> null;
            case GRADIENT_SWEEP -> GraphicIconShader.GRADIENT_SWEEP;
            case COLOR_CYCLE -> GraphicIconShader.COLOR_CYCLE;
            case ENERGY_WAVE -> GraphicIconShader.ENERGY_WAVE;
            case PULSE -> GraphicIconShader.PULSE;
            case HOLOGRAM -> GraphicIconShader.HOLOGRAM;
            case GLITCH -> GraphicIconShader.GLITCH;
            case SCANLINE -> GraphicIconShader.SCANLINE;
            case SPARKLE -> GraphicIconShader.SPARKLE;
            case DISSOLVE -> GraphicIconShader.DISSOLVE;
            case OUTLINE -> GraphicIconShader.OUTLINE;
        };
    }
}
