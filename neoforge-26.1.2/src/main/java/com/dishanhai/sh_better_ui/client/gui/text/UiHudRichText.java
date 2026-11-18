// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import com.dishanhai.sh_better_ui.kernel.api.richtext.LocalizedText;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichStyle;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;


/** HUD adapter for styled components and SHBUI text effects on the extractor backend. */
public final class UiHudRichText {
    private UiHudRichText() {}

    public static Component prepare(Component text) { return UiRichText.prepare(UiTextEffects.normalize(text)); }

    /** Project a text-only rich document into one HUD row. */
    public static Component prepare(RichDocument document) {
        var result = Component.empty();
        for (var node : document.nodes()) {
            if (node instanceof TextRun run) {
                result.append(Component.literal(run.text()).setStyle(nativeStyle(run.style())));
            } else if (node instanceof LocalizedText localized) {
                result.append(Component.translatableWithFallback(localized.key(), localized.fallback(),
                        localized.arguments().toArray()).setStyle(nativeStyle(localized.style())));
            } else {
                throw new IllegalArgumentException("HUD rows support text nodes only");
            }
        }
        return prepare(result);
    }

    private static Style nativeStyle(RichStyle style) {
        var nativeStyle = Style.EMPTY.withBold(style.bold()).withItalic(style.italic())
                .withUnderlined(style.underlined()).withStrikethrough(style.strikethrough())
                .withObfuscated(style.obfuscated());
        if (style.color() != null) nativeStyle = nativeStyle.withColor(style.color());
        if (style.font() != null) nativeStyle = nativeStyle.withFont(new FontDescription.Resource(Identifier.tryParse(style.font().toString())));
        return nativeStyle;
    }

    /** Draw one visual line using the caller's already sampled readability palette. */
    public static void draw(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, int x, int y,
                            int color, UiPreferences preferences, TextReadabilityPalette palette) {
        draw(graphics, font, text, x, y, color, color, preferences, palette);
    }

    /** Accepts the authored base independently of the currently animated HUD base fill. */
    public static void draw(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, int x, int y,
                            int color, int authoredColor, UiPreferences preferences, TextReadabilityPalette palette) {
        UiFormattedText.draw(graphics, font, text, x, y, color, authoredColor, false, preferences, palette,
                System.currentTimeMillis());
    }
}
