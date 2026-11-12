// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.text;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.Objects;

/**
 * A stable identity for one text placement within a live render scope.
 * Virtualized placements supply the current data item ID. Rich text may supply a stable fragment ID.
 * A null data item or fragment represents an absent component of the identity.
 */
public record TextId(UiId text, UiId dataItem, UiId fragment) {
    public TextId {
        Objects.requireNonNull(text, "text");
    }

    public TextId(UiId text) {
        this(text, null, null);
    }
}
