package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.BackgroundSpec;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.ElectricLogoProfile;
import com.dishanhai.sh_better_ui.config.*;
import com.dishanhai.sh_better_ui.theme.ThemeId;
import com.google.gson.*;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 山海：将主题中的声明式覆写应用于现有材质和效果配置。 */
public final class ThemeEffects {
    public record Local(boolean enabled, String color, float strength, float speed) {
        public Local {
            if (!ThemeSettings.validColor(color) || !(strength >= 0 && strength <= 2)
                    || !(speed >= 0.1f && speed <= 4)) throw new IllegalArgumentException();
        }
        public int tint(int fallback) {
            int alpha = Math.min(255, Math.round((fallback >>> 24) * strength));
            return ink(alpha << 24 | fallback & 0xFFFFFF);
        }

        public int ink(int fallback) {
            if (color.isEmpty()) return fallback;
            return (fallback & 0xFF000000) | Integer.parseInt(color.replace("#", ""), 16);
        }
    }

    public record Material(String fill, String edge, String text, String accent, float opacity) {
        public Material {
            if (!ThemeSettings.validColor(fill) || !ThemeSettings.validColor(edge)
                    || !ThemeSettings.validColor(text) || !ThemeSettings.validColor(accent)
                    || !(opacity >= 0 && opacity <= 2)) throw new IllegalArgumentException();
        }
        public int fill(int color) {
            int alpha = Math.min(255, Math.round((color >>> 24) * opacity));
            return rgb(fill, alpha << 24 | color & 0xFFFFFF);
        }
        public int edge(int color) { return rgb(edge, color); }
        public int text(int color) { return rgb(text, color); }
        public int accent(int color) { return rgb(accent, color); }
        private static int rgb(String value, int color) {
            return value.isEmpty() ? color : color & 0xFF000000 | Integer.parseInt(value.replace("#", ""), 16);
        }
    }

    private static final Material MATERIAL = new Material("", "", "", "", 1);
    private static final Gson GSON = new Gson();
    private static final Local LOCAL = new Local(true, "", 1, 1);
    private static final Map<String, Record> DEFAULTS = defaults();
    private static final Map<String, Extension> EXTENSIONS = new LinkedHashMap<>();
    private static final ThreadLocal<ClientUiSettings> PREVIEW = new ThreadLocal<>();
    private static final Map<String, Cached> CACHE = new LinkedHashMap<>();

    private ThemeEffects() {}

    private static Map<String, Record> defaults() {
        var defaults = new LinkedHashMap<String, Record>();
        defaults.put("glass", GlassBackdropSettings.DEFAULT);
        defaults.put("dotMatrix", DotMatrixProfile.VIOLET);
        defaults.put("antigravity", AntigravityProfile.DEFAULT);
        defaults.put("lightRays", LightRaysProfile.DEFAULT);
        defaults.put("laserFlow", LaserFlowProfile.DEFAULT);
        defaults.put("codeRain", CodeRainProfile.DEFAULT);
        defaults.put("ascii", CodeRainProfile.ASCII);
        defaults.put("patternWaves", PatternWavesProfile.Preset.SILK.profile());
        defaults.put("shapeWaves", ShapeWavesProfile.DEFAULT);
        defaults.put("electricLogo", ElectricLogoProfile.DEFAULT);
        defaults.put("reveal", RevealProfile.DEFAULT);
        defaults.put("feedback", ControlFeedbackSettings.DEFAULT);
        defaults.put("tabs", TabScrollSettings.DEFAULT);
        for (String id : new String[]{"classic", "clearGlass", "smokeGlass", "slate", "liquidGlass", "network"}) {
            defaults.put(id, MATERIAL);
        }
        for (String id : new String[]{"clickRipple", "flowBorder", "breathingGlow", "pageSlide", "recipeTransition", "capsule", "screenReturn"}) {
            defaults.put(id, LOCAL);
        }
        return java.util.Collections.unmodifiableMap(defaults);
    }

    public static synchronized Set<String> groups() {
        var groups = new java.util.LinkedHashSet<>(DEFAULTS.keySet());
        groups.addAll(EXTENSIONS.keySet());
        return java.util.Collections.unmodifiableSet(groups);
    }

    /** Installs a namespaced record schema and returns its removal callback. */
    public static synchronized Runnable registerProfile(String group, Record defaults) {
        ThemeId.require(group);
        Objects.requireNonNull(defaults);
        // Verify the template and canonical constructor before publishing.
        patch(defaults, new JsonObject());
        fields(defaults);
        var extension = new Extension(defaults);
        if (EXTENSIONS.putIfAbsent(group, extension) != null) throw new IllegalArgumentException(group);
        return () -> {
            synchronized (ThemeEffects.class) {
                if (EXTENSIONS.get(group) == extension) {
                    EXTENSIONS.remove(group);
                    CACHE.remove(group);
                }
            }
        };
    }

    static synchronized Record defaults(String group) {
        Record defaults = DEFAULTS.get(group);
        var extension = EXTENSIONS.get(group);
        return extension == null ? defaults : extension.defaults();
    }

    public static ClientUiSettings settings() {
        var preview = PREVIEW.get();
        return preview != null ? preview : ClientUiSettings.current();
    }

    public static void preview(ClientUiSettings draft, Runnable render) {
        var previous = PREVIEW.get();
        PREVIEW.set(draft);
        try {
            render.run();
        } finally {
            if (previous == null) PREVIEW.remove();
            else PREVIEW.set(previous);
        }
    }

    public static ClientUiSettings previewSettings(ClientUiSettings settings, String group,
                                                   JsonObject inherited, JsonObject local) {
        var combined = ThemeOverrides.EMPTY.withGroup(group, inherited)
                .overlay(ThemeOverrides.EMPTY.withGroup(group, local));
        var theme = settings.theme();
        if (!group.equals(hudBackgroundGroup(theme))) theme = theme.overlayEffects(combined);
        return settings.withTheme(theme.withEffects(theme.effects().withGroup(group, combined.group(group))));
    }

    /** Returns the editable profile group for the current HUD background, if it has one. */
    public static String hudBackgroundGroup(ThemeSettings theme) {
        if (theme.mode() != ThemeSettings.Mode.HUD_GLASS || theme.hudBackgroundEffect().isEmpty()) return null;
        return ShBetterUiApi.backgrounds().find(theme.hudBackgroundEffect())
                .map(BackgroundSpec::settingsGroup).orElse(null);
    }

    public static GlassBackdropSettings glass() {
        var settings = settings();
        return profile(settings.theme(), "glass", settings.backdrop());
    }

    public static Material material(String group) {
        var settings = settings();
        return settings.highContrast() ? MATERIAL : profile(settings.theme(), group, MATERIAL);
    }

    public static Local local(String group) { return profile(group, LOCAL); }

    public static <T extends Record> T profile(String group, T fallback) {
        return profile(settings().theme(), group, fallback);
    }

    public static <T extends Record> T profile(ThemeSettings theme, String group, T fallback) {
        if (!theme.effects().contains(group)
                || (theme.mode() != ThemeSettings.Mode.CUSTOM && !group.equals(hudBackgroundGroup(theme)))) return fallback;
        return resolve(theme.effects(), group, fallback);
    }

    private static synchronized <T extends Record> T resolve(ThemeOverrides overrides, String group, T fallback) {
        Cached cached = CACHE.get(group);
        if (cached == null || cached.overrides != overrides || !cached.fallback.equals(fallback)) {
            Record resolved = fallback;
            try {
                resolved = apply(group, fallback, overrides.group(group));
            } catch (IllegalArgumentException ignored) {
                // 外部文件中的无效分组回退，其他分组仍可用。
            }
            cached = new Cached(overrides, fallback, resolved);
            CACHE.put(group, cached);
        }
        @SuppressWarnings("unchecked")
        T result = (T) cached.resolved;
        return result;
    }

    public static void validate(ThemeOverrides overrides) {
        for (String group : overrides.groups()) validate(group, overrides.group(group));
    }

    public static void validate(String group, JsonObject values) {
        Record fallback = defaults(group);
        if (fallback == null) throw new IllegalArgumentException(group);
        apply(group, fallback, values);
    }

    public static JsonObject template(String group) {
        Record fallback = defaults(group);
        if (fallback == null) throw new IllegalArgumentException(group);
        JsonObject result = fields(fallback);
        switch (group) {
            case "pageSlide" -> { result.remove("color"); result.remove("strength"); }
            case "recipeTransition" -> result.remove("color");
            case "capsule" -> { result.remove("speed"); result.remove("strength"); }
        }
        return result;
    }

    static JsonObject fields(Record value) {
        JsonObject result = new JsonObject();
        try {
            for (var component : value.getClass().getRecordComponents()) {
                Object field = component.getAccessor().invoke(value);
                if (field instanceof Record record) result.add(component.getName(), fields(record));
                else if (field instanceof DotMatrixProfile.Effect) {
                    if (field == DotMatrixProfile.Effect.STEADY) result.addProperty(component.getName(), "STEADY");
                    if (field == DotMatrixProfile.Effect.TWINKLE) result.addProperty(component.getName(), "TWINKLE");
                    if (field == DotMatrixProfile.Effect.WAVE) result.addProperty(component.getName(), "WAVE");
                } else if (field instanceof DotMatrixProfile.Flow) {
                    if (field == DotMatrixProfile.Flow.RIGHT) result.addProperty(component.getName(), "RIGHT");
                    if (field == DotMatrixProfile.Flow.DOWN) result.addProperty(component.getName(), "DOWN");
                    if (field == DotMatrixProfile.Flow.DIAGONAL) result.addProperty(component.getName(), "DIAGONAL");
                    if (field == DotMatrixProfile.Flow.OUTWARD) result.addProperty(component.getName(), "OUTWARD");
                }
                else if (component.getType().isInterface() && !(field instanceof java.util.List<?>)) continue;
                else result.add(component.getName(), GSON.toJsonTree(field, component.getGenericType()));
            }
            return result;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** Validates a local override in the same context in which it will render. */
    public static void validate(String group, JsonObject inherited, JsonObject local) {
        resolveEditor(group, inherited, local);
    }

    static JsonObject resolveEditor(String group, JsonObject inherited, JsonObject local) {
        return resolveEditor(settings(), group, inherited, local);
    }

    static JsonObject resolveEditor(ClientUiSettings base, String group, JsonObject inherited, JsonObject local) {
        var merged = ThemeOverrides.EMPTY.withGroup(group, inherited)
                .overlay(ThemeOverrides.EMPTY.withGroup(group, local)).group(group);
        Record fallback = switch (group) {
            case "dotMatrix" -> UiDotBackground.defaultProfile(UiTheme.resolve(base.theme()));
            case "glass" -> base.backdrop();
            case "tabs" -> base.tabs();
            case "feedback" -> base.feedback();
            default -> defaults(group);
        };
        if (fallback == null) throw new IllegalArgumentException(group);
        var resolved = fields(apply(group, fallback, merged));
        resolved.keySet().retainAll(template(group).keySet());
        return resolved;
    }

    private static Record apply(String group, Record fallback, JsonObject overrides) {
        Record expected = defaults(group);
        if (expected == null || expected.getClass() != fallback.getClass()) throw new IllegalArgumentException(group);
        if (fallback instanceof Local && !template(group).keySet().containsAll(overrides.keySet())) {
            throw new IllegalArgumentException(group);
        }
        Record result = patch(fallback, overrides);
        if (result instanceof GlassBackdropSettings g
                && (g.blurRadiusScale() < 0 || g.blurRadiusScale() > 2
                || g.strength() < 0 || g.strength() > 1 || g.tintScale() < 0 || g.tintScale() > 2
                || g.grainScale() < 0 || g.grainScale() > 2 || g.saturationScale() < 0 || g.saturationScale() > 2
                || g.lift() < 0 || g.lift() > 0.15f || g.rimScale() < 0 || g.rimScale() > 2
                || g.refractionScale() < 0 || g.refractionScale() > 2)) {
            throw new IllegalArgumentException(group);
        }
        return result;
    }

    private static Record patch(Record fallback, JsonObject overrides) {
        var type = fallback.getClass();
        var components = type.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        int matched = 0;
        try {
            for (int i = 0; i < components.length; i++) {
                var component = components[i];
                types[i] = component.getType();
                Object original = component.getAccessor().invoke(fallback);
                JsonElement value = overrides.get(component.getName());
                arguments[i] = original;
                if (value != null) {
                    arguments[i] = original instanceof Record nested ? patch(nested, value.getAsJsonObject())
                            : parse(component.getGenericType(), types[i], original, value);
                    matched++;
                }
            }
            if (matched != overrides.size()) throw new IllegalArgumentException(overrides.toString());
            return (Record) type.getConstructor(types).newInstance(arguments);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalArgumentException("Invalid theme override for " + type.getSimpleName(), e);
        }
    }

    private static Object parse(Type type, Class<?> raw, Object original, JsonElement value) {
        if (original instanceof DotMatrixProfile.Effect && !raw.isEnum()) {
            return switch (value.getAsString()) {
                case "STEADY" -> DotMatrixProfile.Effect.STEADY;
                case "TWINKLE" -> DotMatrixProfile.Effect.TWINKLE;
                case "WAVE" -> DotMatrixProfile.Effect.WAVE;
                default -> throw new IllegalArgumentException();
            };
        }
        if (raw == DotMatrixProfile.Flow.class) {
            return switch (value.getAsString()) {
                case "RIGHT" -> DotMatrixProfile.Flow.RIGHT;
                case "DOWN" -> DotMatrixProfile.Flow.DOWN;
                case "DIAGONAL" -> DotMatrixProfile.Flow.DIAGONAL;
                case "OUTWARD" -> DotMatrixProfile.Flow.OUTWARD;
                default -> throw new IllegalArgumentException();
            };
        }
        if (value.isJsonNull() || (raw.isInterface() && raw != java.util.List.class)) {
            throw new IllegalArgumentException();
        }
        if (raw == int.class && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String color = value.getAsString();
            if (!color.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) throw new IllegalArgumentException();
            return color.length() == 7 ? 0xFF000000 | Integer.parseInt(color.substring(1), 16)
                    : (int) Long.parseLong(color.substring(1), 16);
        }
        if (raw == int.class || raw == long.class) {
            if (!value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
            var number = value.getAsBigDecimal();
            return raw == int.class ? (Object) number.intValueExact() : number.longValueExact();
        }
        if (raw == boolean.class && !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
        if ((raw == float.class || raw == double.class) && !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException();
        }
        Object result = GSON.fromJson(value, type);
        if (result == null || (result instanceof Float f && !Float.isFinite(f))
                || (result instanceof Double d && !Double.isFinite(d))) throw new IllegalArgumentException();
        return result;
    }

    private record Cached(ThemeOverrides overrides, Record fallback, Record resolved) {}
    private record Extension(Record defaults) {}
}
