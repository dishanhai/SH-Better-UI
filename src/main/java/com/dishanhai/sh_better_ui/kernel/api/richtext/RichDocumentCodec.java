// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

/** Versioned passive rich text JSON and DFU codec shared by both loaders. */
public final class RichDocumentCodec {
    public static final int VERSION = 1;
    public static final Codec<RichDocument> CODEC = codecInternal(null);
    private static final Set<String> STYLE_FIELDS = Set.of("color", "font", "bold", "italic",
            "underlined", "strikethrough", "obfuscated");

    private RichDocumentCodec() {}

    /** Canonical JSON with stable field order, limited to 256 KiB UTF-8 and 4096 nodes. */
    public static String encode(RichDocument document) { return boundedJson(encodeTree(document, null)); }

    /** Encode known passive extensions and preserve inert envelopes with the caller's explicit policy. */
    public static String encode(RichDocument document, RichExtensionInput extensions) {
        return boundedJson(encodeTree(document, Objects.requireNonNull(extensions, "extensions")));
    }

    /** Bounded raw JSON. Rejects duplicate fields, unknown schema versions and unsupported node kinds. */
    public static RichDocument decode(String json) {
        return decodeInternal(json, null);
    }

    /** Explicitly enable passive extension envelopes. Missing types and versions are inert. */
    public static RichDocument decode(String json, RichExtensionInput extensions) {
        return decodeInternal(json, Objects.requireNonNull(extensions, "extensions"));
    }

    /** Create a DFU codec with the same passive extension policy as the JSON overloads. */
    public static Codec<RichDocument> codec(RichExtensionInput extensions) {
        return codecInternal(Objects.requireNonNull(extensions, "extensions"));
    }

    private static Codec<RichDocument> codecInternal(RichExtensionInput extensions) {
        return Codec.PASSTHROUGH.flatXmap(input -> readDynamic(input, extensions), document -> {
            try {
                var tree = encodeTree(document, extensions);
                boundedJson(tree);
                return DataResult.success(new Dynamic<>(JsonOps.INSTANCE, tree));
            } catch (IllegalArgumentException invalid) {
                return DataResult.error(invalid::getMessage);
            }
        });
    }

    private static RichDocument decodeInternal(String json, RichExtensionInput extensions) {
        RichTextInput.utf8Bytes(json, RichTextInput.MAX_UTF8_BYTES);
        try (var reader = new JsonReader(new StringReader(json))) {
            var root = readJson(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("Unexpected trailing content");
            return decodeTree(object(root), false, extensions);
        } catch (IOException | IllegalStateException malformed) {
            throw new IllegalArgumentException("Invalid rich text JSON", malformed);
        }
    }

    private static JsonObject encodeTree(RichDocument document, RichExtensionInput extensions) {
        if (document == null) throw invalid("Missing rich document");
        if (document.nodes().size() > RichTextInput.MAX_NODES) throw invalid("Too many nodes");
        var root = new JsonObject();
        root.addProperty("version", VERSION);
        var nodes = new JsonArray();
        root.add("nodes", nodes);
        for (var node : document.nodes()) {
            var out = new JsonObject();
            out.addProperty("id", node.id().toString());
            if (node instanceof TextRun run) {
                out.addProperty("type", "text");
                out.addProperty("text", run.text());
                writeStyle(out, run.style());
            } else if (node instanceof LocalizedText localized) {
                if (localized.arguments().size() > RichTextInput.MAX_ARGUMENTS) throw invalid("Too many arguments");
                out.addProperty("type", "localized");
                out.addProperty("key", localized.key());
                out.addProperty("fallback", localized.fallback());
                var args = new JsonArray();
                localized.arguments().forEach(args::add);
                out.add("arguments", args);
                writeStyle(out, localized.style());
            } else if (node instanceof ParagraphBreak paragraph) {
                out.addProperty("type", "paragraph");
                out.addProperty("spacing", spacing(paragraph.spacing()));
            } else if (node instanceof ImageNode image) {
                out.addProperty("type", "image");
                out.addProperty("texture", RichTextInput.image(image.texture().toString(),
                        image.intrinsicWidth(), image.intrinsicHeight()).toString());
                out.addProperty("width", image.intrinsicWidth());
                out.addProperty("height", image.intrinsicHeight());
                out.addProperty("alt", image.altText());
            } else if (node instanceof RichExtensionNode extension && extensions != null) {
                extensions.validateForEncode(extension);
                out.addProperty("type", "extension");
                out.addProperty("extension", extension.type().toString());
                out.addProperty("version", extension.version());
                out.addProperty("width", extension.intrinsicWidth());
                out.addProperty("height", extension.intrinsicHeight());
                out.addProperty("logicalHeight", extension.logicalHeight());
                out.addProperty("alt", extension.altText());
                out.addProperty("payload", extension.payload());
            } else {
                throw invalid("Node kind is unavailable in the passive v1 codec");
            }
            nodes.add(out);
        }
        return root;
    }

    private static RichDocument decodeTree(JsonObject root, boolean numericFlags, RichExtensionInput extensions) {
        fields(root, Set.of("version", "nodes"));
        if (integer(root.get("version")) != VERSION) throw invalid("Unsupported version");
        var nodes = array(root.get("nodes"), RichTextInput.MAX_NODES);
        var decoded = new ArrayList<InlineNode>(nodes.size());
        for (var value : nodes) {
            var node = object(value);
            var id = UiId.parse(string(node.get("id")));
            decoded.add(switch (string(node.get("type"))) {
                case "text" -> {
                    fields(node, Set.of("id", "type", "text", "style"));
                    yield new TextRun(id, string(node.get("text")), readStyle(node, numericFlags));
                }
                case "localized" -> {
                    fields(node, Set.of("id", "type", "key", "fallback", "arguments", "style"));
                    var arguments = new ArrayList<String>();
                    array(node.get("arguments"), RichTextInput.MAX_ARGUMENTS).forEach(arg -> arguments.add(string(arg)));
                    yield new LocalizedText(id, string(node.get("key")), string(node.get("fallback")),
                            arguments, readStyle(node, numericFlags));
                }
                case "paragraph" -> {
                    fields(node, Set.of("id", "type", "spacing"));
                    yield new ParagraphBreak(id, spacing(integer(node.get("spacing"))));
                }
                case "image" -> {
                    fields(node, Set.of("id", "type", "texture", "width", "height", "alt"));
                    int width = integer(node.get("width")), height = integer(node.get("height"));
                    var texture = RichTextInput.image(string(node.get("texture")), width, height);
                    yield new ImageNode(id, texture, width, height, string(node.get("alt")));
                }
                case "extension" -> {
                    if (extensions == null) throw invalid("Extension envelope requires an explicit input policy");
                    fields(node, Set.of("id", "type", "extension", "version", "width", "height", "logicalHeight",
                            "alt", "payload"));
                    yield extensions.read(new RichExtensionNode(id, UiId.parse(string(node.get("extension"))),
                            integer(node.get("version")), integer(node.get("width")), integer(node.get("height")),
                            integer(node.get("logicalHeight")), string(node.get("alt")),
                            string(node.get("payload")), false), false);
                }
                default -> throw invalid("Unknown node kind");
            });
        }
        return new RichDocument(decoded);
    }

    private static void writeStyle(JsonObject out, RichStyle style) {
        if (style.equals(RichStyle.EMPTY)) return;
        var value = new JsonObject();
        if (style.color() != null) value.addProperty("color", style.color());
        if (style.font() != null) value.addProperty("font", RichTextInput.resource(style.font().toString()).toString());
        if (style.bold()) value.addProperty("bold", true);
        if (style.italic()) value.addProperty("italic", true);
        if (style.underlined()) value.addProperty("underlined", true);
        if (style.strikethrough()) value.addProperty("strikethrough", true);
        if (style.obfuscated()) value.addProperty("obfuscated", true);
        out.add("style", value);
    }

    private static RichStyle readStyle(JsonObject node, boolean numericFlags) {
        if (!node.has("style")) return RichStyle.EMPTY;
        var style = object(node.get("style"));
        fields(style, STYLE_FIELDS);
        Integer color = style.has("color") ? integer(style.get("color")) : null;
        UiId font = style.has("font") ? RichTextInput.resource(string(style.get("font"))) : null;
        return new RichStyle(color, flag(style, "bold", numericFlags), flag(style, "italic", numericFlags),
                flag(style, "underlined", numericFlags), flag(style, "strikethrough", numericFlags),
                flag(style, "obfuscated", numericFlags), font);
    }

    private static boolean flag(JsonObject style, String field, boolean numericFlags) {
        if (!style.has(field)) return false;
        var value = style.get(field);
        if (value instanceof JsonPrimitive primitive && primitive.isBoolean()) return primitive.getAsBoolean();
        // DFU's NBT ops convert boolean tags to byte-valued JSON numbers.
        if (numericFlags) {
            int bit = integer(value);
            if (bit == 0 || bit == 1) return bit == 1;
        }
        throw invalid("Expected a boolean");
    }

    private static int spacing(int pixels) {
        if (pixels < 0 || pixels > 256) throw invalid("Paragraph spacing must be within 0–256 pixels");
        return pixels;
    }

    private static JsonObject object(JsonElement value) {
        if (value instanceof JsonObject object) return object;
        throw invalid("Expected an object");
    }

    private static JsonArray array(JsonElement value, int max) {
        if (value instanceof JsonArray array && array.size() <= max) return array;
        throw invalid("Expected a bounded array");
    }

    private static void fields(JsonObject object, Set<String> permitted) {
        if (!permitted.containsAll(object.keySet())) throw invalid("Unknown object field");
    }

    private static String string(JsonElement value) {
        if (value instanceof JsonPrimitive primitive && primitive.isString()) return primitive.getAsString();
        throw invalid("Expected a string");
    }

    private static int integer(JsonElement value) {
        if (value instanceof JsonPrimitive primitive && primitive.isNumber()) return parseInteger(primitive.getAsString());
        throw invalid("Expected an integer");
    }

    private static int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException bad) {
            throw invalid("Expected a signed 32-bit integer");
        }
    }

    private static JsonElement readJson(JsonReader reader, int depth) throws IOException {
        checkDepth(depth);
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    RichTextInput.utf8Bytes(name, RichTextInput.MAX_UTF8_BYTES);
                    if (object.has(name)) throw invalid("Duplicate JSON field");
                    object.add(name, readJson(reader, depth + 1));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                var values = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    if (values.size() >= RichTextInput.MAX_NODES) throw invalid("Array has too many elements");
                    values.add(readJson(reader, depth + 1));
                }
                reader.endArray();
                yield values;
            }
            case STRING -> {
                var text = reader.nextString();
                RichTextInput.utf8Bytes(text, RichTextInput.MAX_UTF8_BYTES);
                yield new JsonPrimitive(text);
            }
            case NUMBER -> new JsonPrimitive(parseInteger(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            default -> throw invalid("Unsupported JSON value");
        };
    }

    private static DataResult<RichDocument> readDynamic(Dynamic<?> input, RichExtensionInput extensions) {
        try {
            var ops = input.getOps();
            // Check size and depth before Dynamic.convert can walk a caller-owned tree.
            checkDynamic(input, 0, new Budget());
            var json = object(input.convert(JsonOps.INSTANCE).getValue());
            RichTextInput.utf8Bytes(json.toString(), RichTextInput.MAX_UTF8_BYTES);
            return DataResult.success(decodeTree(json, ops != JsonOps.INSTANCE && ops != JsonOps.COMPRESSED,
                    extensions));
        } catch (IllegalArgumentException invalid) {
            return DataResult.error(invalid::getMessage);
        }
    }

    private static <T> void checkDynamic(Dynamic<T> input, int depth, Budget budget) {
        checkDynamic(input.getOps(), input.getValue(), depth, budget);
    }

    private static <T> void checkDynamic(DynamicOps<T> ops, T value, int depth, Budget budget) {
        checkDepth(depth);
        budget.take(1);
        var entries = ops.getMapValues(value).result();
        if (entries.isPresent()) {
            try (var stream = entries.orElseThrow()) {
                var iterator = stream.iterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    budget.take(ops.getStringValue(entry.getFirst()).result()
                            .orElseThrow(() -> invalid("Expected a string key")));
                    checkDynamic(ops, entry.getSecond(), depth + 1, budget);
                }
            }
            return;
        }
        var elements = ops.getStream(value).result();
        if (elements.isPresent()) {
            try (var stream = elements.orElseThrow()) {
                var iterator = stream.iterator();
                int count = 0;
                while (iterator.hasNext()) {
                    if (++count > RichTextInput.MAX_NODES) throw invalid("Array has too many elements");
                    checkDynamic(ops, iterator.next(), depth + 1, budget);
                }
            }
            return;
        }
        ops.getStringValue(value).result().ifPresent(budget::take);
    }

    private static String boundedJson(JsonObject tree) {
        checkDynamic(JsonOps.INSTANCE, tree, 0, new Budget());
        String encoded = tree.toString();
        RichTextInput.utf8Bytes(encoded, RichTextInput.MAX_UTF8_BYTES);
        return encoded;
    }

    private static void checkDepth(int depth) {
        if (depth > RichTextInput.MAX_NESTING) throw invalid("JSON nesting limit exceeded");
    }

    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException("Rich text codec: " + detail);
    }

    private static final class Budget {
        private int remaining = RichTextInput.MAX_UTF8_BYTES;

        private void take(String text) { take(RichTextInput.utf8Bytes(text, remaining)); }

        private void take(int amount) {
            remaining -= amount;
            if (remaining < 0) throw invalid("Document exceeds size limit");
        }
    }
}
