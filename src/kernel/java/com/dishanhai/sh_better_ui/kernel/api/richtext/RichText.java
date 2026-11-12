// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Fluent construction of immutable rich documents. Builders are confined to their owner. */
public final class RichText {
    private RichText() {}

    public static Builder document(UiId prefix) {
        return new Builder(prefix);
    }

    public static final class Builder {
        private final UiId prefix;
        private final List<InlineNode> nodes = new ArrayList<>();
        private final HashSet<UiId> ids = new HashSet<>();
        private long nextId;

        private Builder(UiId prefix) {
            this.prefix = Objects.requireNonNull(prefix, "prefix");
        }

        public Builder add(InlineNode node) {
            Objects.requireNonNull(node, "node");
            if (!ids.add(node.id())) throw new IllegalArgumentException("Duplicate inline node ID: " + node.id());
            nodes.add(node);
            return this;
        }

        public Builder text(String text) { return text(text, RichStyle.EMPTY); }

        public Builder text(String text, RichStyle style) { return text(nextId(), text, style); }

        public Builder text(UiId id, String text, RichStyle style) {
            return add(new TextRun(id, text, style));
        }

        public Builder localized(String key, String fallback, List<String> arguments, RichStyle style) {
            return add(new LocalizedText(nextId(), key, fallback, arguments, style));
        }

        public Builder paragraph(int spacing) {
            return add(new ParagraphBreak(nextId(), spacing));
        }

        public Builder image(UiId texture, int width, int height, String altText) {
            return add(new ImageNode(nextId(), texture, width, height, altText));
        }

        public Builder extension(UiId type, int version, int width, int height, int logicalHeight,
                                 String altText, String payload) {
            return add(new RichExtensionNode(nextId(), type, version, width, height, logicalHeight, altText, payload));
        }

        public RichDocument build() { return new RichDocument(nodes); }

        UiId nextId() {
            UiId candidate = new UiId(prefix.namespace(), prefix.path() + "/node_" + nextId);
            while (ids.contains(candidate)) {
                nextId++;
                candidate = new UiId(prefix.namespace(), prefix.path() + "/node_" + nextId);
            }
            // The ID is consumed only by a successful add; a rejected node never shifts later IDs.
            return candidate;
        }
    }
}
