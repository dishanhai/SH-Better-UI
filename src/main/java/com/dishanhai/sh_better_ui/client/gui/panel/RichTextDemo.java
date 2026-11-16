// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import com.dishanhai.sh_better_ui.client.gui.text.UiTextEffects;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Same localized content and native layout on all preview backends. */
final class RichTextDemo {
    static final String ID = "rich_text";
    static final int ORDER = 36;
    static final int HEIGHT = 168;
    static final String CODE = """
            import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
            import net.minecraft.network.chat.Component;
            import net.minecraft.util.FormattedCharSequence;

            private FormattedCharSequence richLabel() {
                // Put <rich:vmaterial-shop,rndr:energy_wave> or <rich:item=minecraft:diamond>
                // in your localized text. Prepare before measuring or wrapping.
                return UiRichText.sequence(Component.translatable(
                        "sh_better_ui.panel.developer.gallery.rich_text.image"));
            }
            """;

    private RichTextDemo() {}

    static Component text(String key, Object... arguments) {
        return Component.translatable("sh_better_ui.panel.developer.gallery." + ID + "." + key, arguments);
    }

    static Component button(boolean alternate) {
        return UiRichText.prepare(text("button", UiRichText.item(alternate ? "minecraft:apple" : "minecraft:diamond")));
    }

    static List<FormattedCharSequence> lines(Font font, int width) {
        var lines = new ArrayList<FormattedCharSequence>();
        for (String key : List.of("inline", "image", "styles", "effects", "more_effects")) {
            lines.addAll(font.split(UiRichText.prepare(UiTextEffects.normalize(text(key))), width));
        }
        lines.addAll(font.split(UiRichText.prepare(text("wrapped")), width).stream().limit(2).toList());
        var longText = UiRichText.prepare(text("clipped"));
        int budget = Math.min(150, width);
        if (font.width(longText) <= budget) {
            lines.add(UiRichText.sequence(longText));
        } else {
            var prefix = font.substrByWidth(longText, Math.max(0, budget - font.width(CommonComponents.ELLIPSIS)));
            lines.add(Language.getInstance().getVisualOrder(FormattedText.composite(prefix, CommonComponents.ELLIPSIS)));
        }
        return List.copyOf(lines);
    }
}
