// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.Objects;

/** A versioned, fixed-size inline node. Its payload is immutable and carries no callback or action. */
public record RichExtensionNode(UiId id, UiId type, int version, int intrinsicWidth, int intrinsicHeight,
                                int logicalHeight, String altText, String payload, boolean renderable)
        implements InlineNode {
    static final int MAX_PAYLOAD_BYTES = 4096;

    public RichExtensionNode {
        Objects.requireNonNull(id, "id");
        validateType(type);
        validateVersion(version);
        if (intrinsicWidth <= 0 || intrinsicWidth > 4096 || intrinsicHeight <= 0 || intrinsicHeight > 4096
                || (long) intrinsicWidth * intrinsicHeight > 4 * 1024 * 1024
                || logicalHeight <= 0 || logicalHeight > 256) {
            throw new IllegalArgumentException("Invalid rich text extension dimensions");
        }
        RichTextInput.utf8Bytes(altText, 1024);
        RichTextInput.utf8Bytes(payload, MAX_PAYLOAD_BYTES);
    }

    /** Construct a node from trusted Java. Passive input must also pass its host's format policy. */
    public RichExtensionNode(UiId id, UiId type, int version, int intrinsicWidth, int intrinsicHeight,
                             int logicalHeight, String altText, String payload) {
        this(id, type, version, intrinsicWidth, intrinsicHeight, logicalHeight, altText, payload, true);
    }

    static void validateType(UiId type) {
        String name = Objects.requireNonNull(type, "type").toString();
        RichTextInput.utf8Bytes(name, 128);
        RichTextInput.resource(name);
    }

    static void validateVersion(int version) {
        if (version <= 0 || version > 65_535) throw new IllegalArgumentException("Invalid rich text extension version");
    }
}
