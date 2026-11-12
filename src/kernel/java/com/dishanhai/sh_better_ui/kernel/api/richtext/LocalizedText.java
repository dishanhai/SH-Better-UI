// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.List;
import java.util.Objects;

/** Translation key with an explicit fallback and literal arguments. */
public record LocalizedText(UiId id, String key, String fallback, List<String> arguments, RichStyle style)
        implements InlineNode {
    public LocalizedText {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(fallback, "fallback");
        Objects.requireNonNull(style, "style");
        arguments = List.copyOf(arguments);
        if (key.isBlank()) throw new IllegalArgumentException("Translation key must not be blank");
    }
}
