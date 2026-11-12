// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.protocol;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.Objects;
import java.util.UUID;

/** Data-only identifier for the target of a locally initiated page request. */
public sealed interface UiInteractionTarget permits UiInteractionTarget.Block, UiInteractionTarget.Entity,
        UiInteractionTarget.Page {
    record Block(UiId dimension, int x, int y, int z) implements UiInteractionTarget {
        public Block { Objects.requireNonNull(dimension, "dimension"); }
    }

    record Entity(UiId dimension, UUID entityId) implements UiInteractionTarget {
        public Entity {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(entityId, "entityId");
        }
    }

    record Page(UiId pageId) implements UiInteractionTarget {
        public Page { Objects.requireNonNull(pageId, "pageId"); }
    }
}
