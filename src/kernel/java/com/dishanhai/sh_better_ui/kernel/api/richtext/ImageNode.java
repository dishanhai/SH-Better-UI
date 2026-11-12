// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.Objects;

/** A resource-pack image with declared dimensions so the layout is stable when the asset is absent. */
public record ImageNode(UiId id, UiId texture, int intrinsicWidth, int intrinsicHeight, String altText)
        implements InlineNode {
    public ImageNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(altText, "altText");
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
    }
}
