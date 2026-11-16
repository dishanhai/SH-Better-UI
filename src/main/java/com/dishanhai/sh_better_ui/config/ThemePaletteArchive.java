package com.dishanhai.sh_better_ui.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.*;

/** Immutable, bounded per-theme RGB overrides. The empty ID is the original custom palette. */
public final class ThemePaletteArchive {
    public static final ThemePaletteArchive EMPTY = new ThemePaletteArchive("{}");
    public static final int MAX_LENGTH = 32767, MAX_PALETTES = 128;
    public record Palette(ThemeSettings.Base base, String background, String surface, String border, String text, String accent) {
        public Palette {
            Objects.requireNonNull(base);
            background = ThemeSettings.normalize(background); surface = ThemeSettings.normalize(surface);
            border = ThemeSettings.normalize(border); text = ThemeSettings.normalize(text); accent = ThemeSettings.normalize(accent);
        }
        public static Palette empty() { return new Palette(ThemeSettings.Base.DARK, "", "", "", "", ""); }
    }
    private final Map<String, Palette> values;
    public ThemePaletteArchive(String json) {
        if (json == null || json.length() > MAX_LENGTH) throw new IllegalArgumentException("Palette archive too large");
        try {
            var root = JsonParser.parseString(json).getAsJsonObject();
            if (root.size() > MAX_PALETTES) throw new IllegalArgumentException("Too many palettes");
            var parsed = new LinkedHashMap<String, Palette>();
            for (var entry : root.entrySet()) {
                if (!ThemeSettings.validRegisteredThemeId(entry.getKey())) throw new IllegalArgumentException("Invalid theme ID");
                var item = entry.getValue().getAsJsonObject();
                parsed.put(entry.getKey(), new Palette(ThemeSettings.Base.valueOf(item.get("base").getAsString()),
                        item.get("background").getAsString(), item.get("surface").getAsString(), item.get("border").getAsString(),
                        item.get("text").getAsString(), item.get("accent").getAsString()));
            }
            values = Collections.unmodifiableMap(parsed);
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid palette archive", invalid); }
    }
    public Palette get(String id) { return values.getOrDefault(id, Palette.empty()); }
    public ThemePaletteArchive with(String id, Palette palette) {
        if (!ThemeSettings.validRegisteredThemeId(id)) throw new IllegalArgumentException("Invalid theme ID");
        Objects.requireNonNull(palette);
        var root = JsonParser.parseString(json()).getAsJsonObject();
        // Browsing registrations must not consume archive capacity for inherited defaults.
        if (palette.equals(Palette.empty())) root.remove(id);
        else root.add(id, encode(palette));
        return new ThemePaletteArchive(root.toString());
    }
    private static JsonObject encode(Palette p) {
        var item = new JsonObject(); item.addProperty("base", p.base().name());
        item.addProperty("background", p.background()); item.addProperty("surface", p.surface());
        item.addProperty("border", p.border()); item.addProperty("text", p.text()); item.addProperty("accent", p.accent());
        return item;
    }
    public String json() { var root = new JsonObject(); values.forEach((id,p) -> root.add(id, encode(p))); return root.toString(); }
    public static boolean valid(Object value) { try { if (!(value instanceof String s)) return false; new ThemePaletteArchive(s); return true; } catch (IllegalArgumentException e) { return false; } }
    @Override public boolean equals(Object other) { return other instanceof ThemePaletteArchive a && values.equals(a.values); }
    @Override public int hashCode() { return values.hashCode(); }
}
