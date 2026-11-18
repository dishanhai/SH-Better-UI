// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.util.FormattedCharSequence;

/** Apply after layout/visual ordering, so component and legacy colors cannot replace safe glass ink. */
final class GlassTextInk {
    private GlassTextInk() {}

    static FormattedCharSequence apply(FormattedCharSequence glyphs, int argb, boolean glass) {
        if (!glass) return glyphs;
        return sink -> glyphs.accept((index, style, codepoint) ->
                sink.accept(index, style.withColor(argb & 0xffffff), codepoint));
    }
}
