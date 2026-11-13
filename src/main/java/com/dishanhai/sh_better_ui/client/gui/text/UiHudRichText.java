// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import com.dishanhai.sh_better_ui.client.visual.RichTextComponents;
import com.dishanhai.sh_better_ui.kernel.api.richtext.LocalizedText;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** HUD adapter for the same styled components and text effects used by SHBUI controls. */
public final class UiHudRichText {
    private UiHudRichText() {}

    /** Resolve formatting before measuring or truncating; prepare again after a language change. */
    public static Component prepare(Component text) { return UiTextEffects.normalize(text); }

    /** Project a text-only rich document into one HUD row. */
    public static Component prepare(RichDocument document) {
        for (var node : document.nodes()) {
            if (!(node instanceof TextRun) && !(node instanceof LocalizedText)) {
                throw new IllegalArgumentException("HUD rows support text nodes only");
            }
        }
        return prepare(RichTextComponents.toComponent(document));
    }

    /** Draw one visual line using the caller's already sampled readability palette. */
    public static void draw(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y,
                            int color, UiPreferences preferences, TextReadabilityPalette palette) {
        UiFormattedText.drawHud(graphics, font, text, x, y, color, preferences, palette);
    }
}
