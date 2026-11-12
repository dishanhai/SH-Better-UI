package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 山海：从效果记录派生可编辑字段，保留记录本身的默认值和校验。 */
final class ThemeEffectFields {
    enum Kind { TOGGLE, OPTION, ARGB, RGB, NUMBER, TEXT, PALETTE, STOPS }

    record Field(List<String> path, Kind kind, Class<?> type, List<String> options) {
        String name() { return path.get(path.size() - 1); }
    }

    private ThemeEffectFields() {}

    static List<Field> fields(String group) {
        var result = new ArrayList<Field>();
        collect(ThemeEffects.defaults(group).getClass(), ThemeEffects.template(group), List.of(), result);
        return List.copyOf(result);
    }

    private static void collect(Class<?> type, JsonObject defaults, List<String> parent, List<Field> output) {
        for (var component : type.getRecordComponents()) {
            String name = component.getName();
            if (!defaults.has(name)) continue;
            var path = new ArrayList<>(parent);
            path.add(name);
            Class<?> raw = component.getType();
            if (raw.isRecord()) {
                collect(raw, defaults.getAsJsonObject(name), path, output);
                continue;
            }
            List<String> options = raw.isEnum() ? Arrays.stream(raw.getEnumConstants()).map(e -> ((Enum<?>) e).name()).toList()
                    : raw == DotMatrixProfile.Effect.class ? List.of("STEADY", "TWINKLE", "WAVE")
                    : raw == DotMatrixProfile.Flow.class ? List.of("RIGHT", "DOWN", "DIAGONAL", "OUTWARD") : List.of();
            Kind kind;
            if (!options.isEmpty()) kind = Kind.OPTION;
            else if (raw == boolean.class || raw == Boolean.class) kind = Kind.TOGGLE;
            else if (raw == int.class && isColor(name)) kind = Kind.ARGB;
            else if (raw == String.class) kind = isColor(name) && (type == ThemeEffects.Material.class || type == ThemeEffects.Local.class)
                    ? Kind.RGB : Kind.TEXT;
            else if (defaults.get(name).isJsonPrimitive() && defaults.getAsJsonPrimitive(name).isNumber()) kind = Kind.NUMBER;
            else if (component.getGenericType() instanceof ParameterizedType list && raw == List.class
                    && list.getActualTypeArguments()[0] == Integer.class && isColor(name)) kind = Kind.PALETTE;
            else if (component.getGenericType() instanceof ParameterizedType list && raw == List.class
                    && list.getActualTypeArguments()[0] == DotMatrixProfile.ColorStop.class) kind = Kind.STOPS;
            else continue;
            output.add(new Field(List.copyOf(path), kind, raw, options));
        }
    }

    private static boolean isColor(String name) {
        return name.endsWith("Argb") || name.endsWith("Color") || switch (name) {
            case "argb", "color", "colors", "fill", "edge", "text", "accent" -> true;
            default -> false;
        };
    }

    static JsonObject values(ClientUiSettings settings, String group, JsonObject inherited, JsonObject local) {
        try {
            return ThemeEffects.resolveEditor(settings, group, inherited, local);
        } catch (IllegalArgumentException invalid) {
            return ThemeEffects.resolveEditor(settings, group, new JsonObject(), new JsonObject());
        }
    }

    static JsonElement get(JsonObject root, List<String> path) {
        JsonElement value = root;
        for (String key : path) {
            if (value == null || !value.isJsonObject()) return null;
            value = value.getAsJsonObject().get(key);
        }
        return value;
    }

    static JsonObject edit(ClientUiSettings settings, String group, JsonObject inherited, JsonObject local, List<String> path, JsonElement value) {
        var draft = local.deepCopy();
        put(draft, path, 0, value);
        var resolved = ThemeEffects.resolveEditor(settings, group, inherited, draft);
        if (value != null) put(draft, path, 0, get(resolved, path));
        return draft;
    }

    private static void put(JsonObject root, List<String> path, int index, JsonElement value) {
        String key = path.get(index);
        if (index == path.size() - 1) {
            if (value == null) root.remove(key);
            else root.add(key, value.deepCopy());
            return;
        }
        var child = root.has(key) ? root.getAsJsonObject(key) : new JsonObject();
        put(child, path, index + 1, value);
        if (child.size() == 0) root.remove(key);
        else root.add(key, child);
    }
}
