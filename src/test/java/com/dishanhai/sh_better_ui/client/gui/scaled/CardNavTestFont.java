// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

/** Layout-only test font; does not need a running client or uploaded glyph textures. */
final class CardNavTestFont {
    private CardNavTestFont() {}
    static Font create() {
        var splitter = new StringSplitter((codePoint, style) -> 5f);
        return new Font(ignored -> null, false) {
            @Override public StringSplitter getSplitter() { return splitter; }
            @Override public int width(FormattedCharSequence line) { return (int) Math.ceil(splitter.stringWidth(line)); }
            @Override public int width(FormattedText text) { return (int) Math.ceil(splitter.stringWidth(text)); }
            @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
                return splitter.splitLines(text, width, Style.EMPTY).stream().map(Language.getInstance()::getVisualOrder).toList();
            }
        };
    }
}
