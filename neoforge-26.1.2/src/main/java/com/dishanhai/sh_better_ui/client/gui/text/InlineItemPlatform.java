// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.client.gui.Font;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.display.MaterialEffects;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class InlineItemPlatform {
    private static final FontDescription FONT = new FontDescription.Resource(Identifier.tryParse("sh_better_ui:inline_items"));
    private InlineItemPlatform() {}

    static Style style(Style base) { return base.withFont(FONT); }
    static boolean isItemFont(Style style) { return FONT.equals(style.getFont()); }

    public static void draw(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, float x, float y) {
        if (text.accept((index, style, cp) -> !UiRichText.isObject(style, cp))) return;
        float[] cursor = {x};
        text.accept((index, style, cp) -> {
            String id = UiRichText.itemId(style, cp);
            String texture = UiRichText.imageTexture(style, cp);
            if (id != null || texture != null) {
                var preferences = UiPreferences.configured();
                var renderer = UiRichText.renderer(style, cp);
                long now = Util.getMillis();
                var pose = graphics.pose();
                pose.pushMatrix();
                try {
                    pose.translate(cursor[0], y);
                    if (renderer.motion() != null && !preferences.reducedMotion() && !preferences.highContrast()) {
                        var motion = renderer.motion().sample(now, 9, false);
                        pose.translate(4.5f, 4.5f + motion.offsetY())
                                .rotate((float) Math.toRadians(motion.rotationDegrees())).scale(motion.scale())
                                .translate(-4.5f, -4.5f);
                    }
                    pose.scale(9f / 16);
                    if (texture != null) {
                        var icon = new GraphicIcon(Identifier.tryParse(texture));
                        if (renderer.effect() == UiRichIconRenderers.Effect.NONE || preferences.highContrast()) {
                            icon.draw(graphics, 0, 0, 1, 0xffffffff, preferences);
                        } else {
                            MaterialEffects.drawIcon(graphics, new Bounds(0, 0, 16, 16), icon,
                                    MaterialEffects.Effect.valueOf(renderer.effect().name()),
                                    renderer.effect().phase(now, preferences.reducedMotion()));
                        }
                    } else {
                        var item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(id));
                        if (item == null || item == Items.AIR) item = Items.BARRIER;
                        // 26.x binds default item components when registry data arrives from a world.
                        if (item.builtInRegistryHolder().areComponentsBound()) {
                            graphics.item(new ItemStack(item), 0, 0);
                        } else {
                            GraphicIcon.ITEM.draw(graphics, 0, 0, 1, 0xffffffff, preferences);
                        }
                    }
                } finally {
                    pose.popMatrix();
                }
            }
            cursor[0] += font.getSplitter().stringWidth(FormattedCharSequence.codepoint(cp, style));
            return true;
        });
    }
}
