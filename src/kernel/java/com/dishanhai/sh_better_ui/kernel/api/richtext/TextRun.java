// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.Objects;

/** Text in logical order. The layout honors explicit line breaks. */
public record TextRun(UiId id, String text, RichStyle style) implements InlineNode {
    public TextRun(UiId id, String text) {
        this(id, text, RichStyle.EMPTY);
    }

    public TextRun {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(style, "style");
    }
}
