// Author: dishanhai
package com.dishanhai.sh_better_ui.compat.kubejs.client;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.*;
import com.dishanhai.sh_better_ui.kernel.internal.NativeTextReadability.Options;
import com.dishanhai.sh_better_ui.kernel.internal.NativeTextReadability.PageOptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Converts the common Rhino object/array representation without retaining script objects. */
final class KubeJsPageSchema {
    private static final UiPageLimits LIMITS = UiPageLimits.DEFAULT;
    private static final long MAX_SAFE_SCRIPT_INTEGER = 9_007_199_254_740_991L;
    private static final Set<String> PAGE_KEYS = Set.of("type", "title", "root", "fields", "actions", "textReadability");
    private static final Set<String> NODE_KEYS = Set.of("type", "id", "properties", "bindings", "action", "children",
            "text", "label", "bind", "fallback", "textReadability");

    private int nodes;
    private int schemas;
    private int values;
    private final Map<String, Options> textNodes = new LinkedHashMap<>();

    record Definition(UiPageDefinition page, PageOptions text) {}

    static UiId scriptId(String id) {
        UiId parsed = UiId.parse(id);
        if (!parsed.namespace().equals("kubejs")) {
            throw new IllegalArgumentException("Client script pages and actions require the kubejs namespace");
        }
        return parsed;
    }

    static UiPageDefinition define(String id, Map<?, ?> source) {
        return defineWithText(id, source).page();
    }

    static Definition defineWithText(String id, Map<?, ?> source) {
        return new KubeJsPageSchema().readPage(scriptId(id), source);
    }

    static Map<String, UiValue> data(UiDataSchema schema, Map<?, ?> raw, boolean full) {
        Map<String, UiValue> converted = new KubeJsPageSchema().readData(schema.fields(), raw, 1);
        schema.validate(converted, full);
        return converted;
    }

    private Definition readPage(UiId id, Map<?, ?> source) {
        Map<String, ?> page = object(source, PAGE_KEYS.size(), "page");
        onlyKeys(page, PAGE_KEYS, "page");
        UiPageDefinition.Builder builder = UiPageDefinition.builder(id);
        if (page.containsKey("type")) {
            builder.type(enumValue(UiPageType.class, page.get("type")));
        }
        if (page.containsKey("title")) {
            builder.title(string(page.get("title"), "title"));
        }
        if (page.containsKey("fields")) {
            readFields(page.get("fields"), 1).forEach(builder::field);
        }
        if (page.containsKey("actions")) {
            for (Object action : list(page.get("actions"), LIMITS.maxActions(), "actions")) {
                builder.action(new UiActionDefinition(scriptId(string(action, "action")), UiActionPolicy.LOCAL,
                        UiDataSchema.EMPTY));
            }
        }
        UiPageDefinition definition = builder.root(readNode(page.get("root"), 1)).build();
        new UiPageValidator().requireValid(definition, false);
        var text = page.containsKey("textReadability") ? KubeJsTextReadabilityApi.options(page.get("textReadability"))
                : Options.INHERIT;
        if ((page.containsKey("textReadability") || !textNodes.isEmpty())
                && definition.type() != UiPageType.SCREEN && definition.type() != UiPageType.MODAL) {
            throw new IllegalArgumentException("Text readability requires a script Screen or modal page");
        }
        return new Definition(definition, new PageOptions(text, textNodes));
    }

    private UiNode readNode(Object source, int depth) {
        if (++nodes > LIMITS.maxNodes() || depth > LIMITS.maxDepth()) {
            throw new IllegalArgumentException("Script page exceeds its tree budget");
        }
        Map<String, ?> node = object(source, NODE_KEYS.size(), "node");
        onlyKeys(node, NODE_KEYS, "node");
        String type = string(node.get("type"), "type");
        UiId typeId = type.indexOf(':') < 0 ? UiBuiltinComponents.id(type) : UiId.parse(type);
        UiComponentDescriptor descriptor = UiBuiltinComponents.descriptors().get(typeId);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown built-in script component: " + typeId);
        }
        String nodeId = string(node.get("id"), "id");
        UiNode.Builder builder = UiNode.builder(nodeId, typeId);
        if (node.containsKey("textReadability")) {
            textNodes.put(nodeId, KubeJsTextReadabilityApi.options(node.get("textReadability")));
        }
        if (node.containsKey("properties")) {
            object(node.get("properties"), LIMITS.maxProperties(), "properties").forEach((key, raw) -> {
                UiPropertyDescriptor property = descriptor.properties().get(key);
                if (property == null) {
                    throw new IllegalArgumentException("Unknown script component property: " + key);
                }
                builder.property(key, readValue(property.schema(), raw, 1));
            });
        }
        if (node.containsKey("text")) {
            if (typeId.equals(UiBuiltinComponents.id("button"))) {
                builder.label(string(node.get("text"), "text"));
            } else {
                builder.text(string(node.get("text"), "text"));
            }
        }
        if (node.containsKey("label")) {
            builder.label(string(node.get("label"), "label"));
        }
        if (node.containsKey("bindings")) {
            object(node.get("bindings"), LIMITS.maxProperties(), "bindings").forEach(
                    (key, field) -> builder.bind(key, string(field, "binding")));
        }
        if (node.containsKey("bind")) {
            String property = descriptor.properties().containsKey("text") ? "text" : "label";
            builder.bind(property, string(node.get("bind"), "bind"));
        }
        if (node.containsKey("fallback")) {
            builder.fallback(enumValue(UiNodeFallback.class, node.get("fallback")));
        }
        if (node.containsKey("action")) {
            builder.action(scriptId(string(node.get("action"), "action")));
        }
        if (node.containsKey("children")) {
            for (Object child : list(node.get("children"), LIMITS.maxChildren(), "children")) {
                builder.child(readNode(child, depth + 1));
            }
        }
        return builder.build();
    }

    private Map<String, UiValueSchema> readFields(Object source, int depth) {
        Map<String, UiValueSchema> fields = new LinkedHashMap<>();
        object(source, LIMITS.maxDataFields(), "fields").forEach((name, type) ->
                fields.put(name, readType(type, depth)));
        return fields;
    }

    private UiValueSchema readType(Object source, int depth) {
        if (++schemas > 8192 || depth > 12) {
            throw new IllegalArgumentException("Script page data schema exceeds its budget");
        }
        Map<String, ?> spec = source instanceof CharSequence
                ? Map.of("type", source) : object(source, 4, "data type");
        return switch (enumValue(UiValueType.class, spec.get("type"))) {
            case BOOLEAN -> {
                onlyKeys(spec, Set.of("type"), "boolean");
                yield UiValueSchema.bool();
            }
            case INTEGER -> {
                onlyKeys(spec, Set.of("type", "min", "max"), "integer");
                yield UiValueSchema.integer((int) bound(spec, "min", Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE),
                        (int) bound(spec, "max", Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE));
            }
            case LONG -> {
                onlyKeys(spec, Set.of("type", "min", "max"), "long");
                yield UiValueSchema.longInt(bound(spec, "min", -MAX_SAFE_SCRIPT_INTEGER,
                                -MAX_SAFE_SCRIPT_INTEGER, MAX_SAFE_SCRIPT_INTEGER),
                        bound(spec, "max", MAX_SAFE_SCRIPT_INTEGER, -MAX_SAFE_SCRIPT_INTEGER, MAX_SAFE_SCRIPT_INTEGER));
            }
            case DOUBLE -> {
                onlyKeys(spec, Set.of("type", "min", "max"), "double");
                yield UiValueSchema.decimal(spec.containsKey("min") ? number(spec.get("min")) : -Double.MAX_VALUE,
                        spec.containsKey("max") ? number(spec.get("max")) : Double.MAX_VALUE);
            }
            case STRING -> {
                onlyKeys(spec, Set.of("type", "maxLength"), "string");
                yield UiValueSchema.text((int) bound(spec, "maxLength", 256, 0, 4096));
            }
            case ENUM -> {
                onlyKeys(spec, Set.of("type", "choices"), "enum");
                Set<String> options = new LinkedHashSet<>();
                for (Object choice : list(spec.get("choices"), 512, "choices")) {
                    if (!options.add(string(choice, "choice"))) {
                        throw new IllegalArgumentException("Duplicate script enum choice");
                    }
                }
                yield UiValueSchema.choice(options);
            }
            case LIST -> {
                onlyKeys(spec, Set.of("type", "element", "maxEntries"), "list");
                yield UiValueSchema.list(readType(spec.get("element"), depth + 1),
                        (int) bound(spec, "maxEntries", 64, 0, 512));
            }
            case OBJECT -> {
                onlyKeys(spec, Set.of("type", "fields"), "object");
                yield UiValueSchema.object(readFields(spec.get("fields"), depth + 1));
            }
            case ITEM_REFERENCE -> {
                onlyKeys(spec, Set.of("type"), "item_reference");
                yield UiValueSchema.itemReference();
            }
        };
    }

    private Map<String, UiValue> readData(Map<String, UiValueSchema> fields, Object raw, int depth) {
        Map<String, UiValue> data = new LinkedHashMap<>();
        object(raw, fields.size(), "data").forEach((name, input) -> {
            UiValueSchema type = fields.get(name);
            if (type == null) {
                throw new IllegalArgumentException("Undeclared script page field: " + name);
            }
            data.put(name, readValue(type, input, depth));
        });
        return Map.copyOf(data);
    }

    private UiValue readValue(UiValueSchema schema, Object raw, int depth) {
        if (++values > 8192 || depth > 12) {
            throw new IllegalArgumentException("Script page values exceed their budget");
        }
        return switch (schema.type()) {
            case BOOLEAN -> {
                if (!(raw instanceof Boolean flag)) {
                    throw new IllegalArgumentException("Expected a script boolean");
                }
                yield new UiValue.Bool(flag);
            }
            case INTEGER -> new UiValue.Int((int) integer(raw, Integer.MIN_VALUE, Integer.MAX_VALUE));
            case LONG -> new UiValue.LongInt(integer(raw, -MAX_SAFE_SCRIPT_INTEGER, MAX_SAFE_SCRIPT_INTEGER));
            case DOUBLE -> new UiValue.Decimal(number(raw));
            case STRING -> new UiValue.Text(string(raw, "text"));
            case ENUM -> new UiValue.Choice(string(raw, "choice"));
            case LIST -> {
                List<?> entries = list(raw, schema.maxSize(), "value list");
                List<UiValue> result = new ArrayList<>(entries.size());
                UiValueSchema element = schema.element().orElseThrow();
                for (Object entry : entries) {
                    result.add(readValue(element, entry, depth + 1));
                }
                yield new UiValue.Sequence(result);
            }
            case OBJECT -> new UiValue.ObjectValue(readData(schema.fields(), raw, depth + 1));
            case ITEM_REFERENCE -> {
                Map<String, ?> item = object(raw, 2, "item reference");
                onlyKeys(item, Set.of("id", "count"), "item reference");
                yield new UiValue.ItemReference(UiId.parse(string(item.get("id"), "item ID")),
                        (int) bound(item, "count", 1, 0, Integer.MAX_VALUE));
            }
        };
    }

    static Map<String, ?> object(Object raw, int maximum, String label) {
        if (!(raw instanceof Map<?, ?> map) || map.size() > maximum) {
            throw new IllegalArgumentException("Expected a bounded script object for " + label);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (result.size() >= maximum) {
                throw new IllegalArgumentException("Script object exceeds its field budget");
            }
            result.put(string(entry.getKey(), "object key"), entry.getValue());
        }
        return result;
    }

    private static List<?> list(Object raw, int maximum, String label) {
        if (!(raw instanceof List<?> entries) || entries.size() > maximum) {
            throw new IllegalArgumentException("Expected a bounded script array for " + label);
        }
        return entries;
    }

    static void onlyKeys(Map<String, ?> object, Set<String> allowed, String label) {
        if (!allowed.containsAll(object.keySet())) {
            throw new IllegalArgumentException("Unknown script keys in " + label);
        }
    }

    private static String string(Object value, String label) {
        if (!(value instanceof CharSequence text) || text.length() > 4096) {
            throw new IllegalArgumentException("Expected bounded script text for " + label);
        }
        return text.toString();
    }

    static <T extends Enum<T>> T enumValue(Class<T> type, Object value) {
        return Enum.valueOf(type, string(value, type.getSimpleName()).toUpperCase(Locale.ROOT));
    }

    static double number(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("Expected a finite script number");
        }
        return number.doubleValue();
    }

    private static long integer(Object raw, long minimum, long maximum) {
        double value = number(raw);
        if (value < minimum || value > maximum || Math.rint(value) != value) {
            throw new IllegalArgumentException("Expected an in-range script integer");
        }
        return (long) value;
    }

    private static long bound(Map<String, ?> spec, String key, long defaultValue, long minimum, long maximum) {
        return spec.containsKey(key) ? integer(spec.get(key), minimum, maximum) : defaultValue;
    }
}
