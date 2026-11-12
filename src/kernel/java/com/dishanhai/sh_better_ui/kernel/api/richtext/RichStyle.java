// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

/** Passive text style. A null color or font inherits the host's default. */
public record RichStyle(Integer color, boolean bold, boolean italic, boolean underlined,
                        boolean strikethrough, boolean obfuscated, UiId font) {
    public static final RichStyle EMPTY = new RichStyle(null, false, false, false, false, false, null);

    public RichStyle {
        if (color != null && (color < 0 || color > 0xffffff)) {
            throw new IllegalArgumentException("Rich text color must be RGB");
        }
    }
}
