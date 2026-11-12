// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.Objects;

/** Explicit paragraph boundary with additional spacing in logical GUI pixels. */
public record ParagraphBreak(UiId id, int spacing) implements InlineNode {
    public ParagraphBreak {
        Objects.requireNonNull(id, "id");
        if (spacing < 0) throw new IllegalArgumentException("Negative paragraph spacing");
    }
}
