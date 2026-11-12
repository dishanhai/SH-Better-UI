// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * An explicit allowlist for passive extension payloads. Safe to share when the supplied validators
 * and parsers are pure and thread-safe. The default markup and document codecs do not use this policy.
 */
public final class RichExtensionInput {
    private final Map<UiId, Format> formats;

    /** With an empty map all extension nodes decode as inert placeholders. */
    public RichExtensionInput(Map<UiId, Format> formats) {
        if (formats.size() > 64) throw new IllegalArgumentException("Too many rich text extension formats");
        this.formats = Map.copyOf(formats);
        this.formats.keySet().forEach(RichExtensionNode::validateType);
    }

    /**
     * The validator accepts the canonical literal payload used in JSON/DFU and the renderer.
     * The optional markup parser translates the tag payload into that same representation.
     */
    public record Format(int version, Predicate<String> validator, UnaryOperator<String> markupParser) {
        public Format {
            RichExtensionNode.validateVersion(version);
            Objects.requireNonNull(validator, "validator");
            Objects.requireNonNull(markupParser, "markupParser");
        }

        public Format(int version, Predicate<String> validator) {
            this(version, validator, UnaryOperator.identity());
        }
    }

    RichExtensionNode read(RichExtensionNode envelope, boolean markup) {
        var format = match(envelope);
        if (format == null) return envelope;
        String payload = validate(envelope, format, markup);
        return new RichExtensionNode(envelope.id(), envelope.type(), envelope.version(), envelope.intrinsicWidth(),
                envelope.intrinsicHeight(), envelope.logicalHeight(), envelope.altText(), payload);
    }

    void validateForEncode(RichExtensionNode node) {
        if (!node.renderable()) return;
        var format = match(node);
        if (format == null) throw new IllegalArgumentException("Rich text extension is unavailable in this output policy");
        validate(node, format, false);
    }

    private Format match(RichExtensionNode node) {
        var format = formats.get(node.type());
        return format != null && format.version() == node.version() ? format : null;
    }

    private static String validate(RichExtensionNode node, Format format, boolean markup) {
        try {
            String payload = markup ? format.markupParser().apply(node.payload()) : node.payload();
            RichTextInput.utf8Bytes(payload, RichExtensionNode.MAX_PAYLOAD_BYTES);
            if (!format.validator().test(payload)) throw new IllegalArgumentException("Payload rejected");
            return payload;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid payload for rich text extension " + node.type(), invalid);
        }
    }
}
