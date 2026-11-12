// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.HashSet;
import java.util.List;

/** Immutable document in logical order. Safe to share without loading client classes. */
public record RichDocument(List<InlineNode> nodes) {
    public RichDocument {
        nodes = List.copyOf(nodes);
        var ids = new HashSet<UiId>();
        for (var node : nodes) {
            if (!ids.add(node.id())) throw new IllegalArgumentException("Duplicate inline node ID: " + node.id());
        }
    }
}
