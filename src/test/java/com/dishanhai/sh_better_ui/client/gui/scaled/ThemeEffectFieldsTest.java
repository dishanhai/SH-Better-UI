package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.ElectricLogoProfile;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.ThemeOverrides;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ThemeEffectFieldsTest {
    @Test
    void exposesTypedNestedSettingsAndEveryBuiltInGroup() {
        for (String group : ThemeEffects.groups()) {
            var fields = ThemeEffectFields.fields(group);
            assertFalse(fields.isEmpty(), group);
            var template = ThemeEffects.template(group);
            assertEquals(leaves(template), fields.size(), group);
            for (var field : fields) assertNotNull(ThemeEffectFields.get(template, field.path()), field.path().toString());
        }
        var fields = ThemeEffectFields.fields("dotMatrix");
        assertTrue(fields.stream().anyMatch(f -> f.path().equals(List.of("motion", "effect"))
                && f.options().contains("WAVE")));
        assertTrue(fields.stream().anyMatch(f -> f.path().equals(List.of("gradient", "flow"))
                && f.options().contains("OUTWARD")));
        assertTrue(fields.stream().anyMatch(f -> f.path().equals(List.of("gradient", "stops"))
                && f.kind() == ThemeEffectFields.Kind.STOPS));
        assertTrue(ThemeEffectFields.fields("electricLogo").stream().anyMatch(f ->
                f.path().equals(List.of("palette", "coreArgb")) && f.kind() == ThemeEffectFields.Kind.ARGB));
    }

    @Test
    void dotMatrixFormDefaultsMatchTheFullscreenBackground() {
        var settings = ClientUiSettings.DEFAULT;
        var inherited = new JsonObject();
        var local = new JsonObject();
        var values = ThemeEffectFields.values(settings, "dotMatrix", inherited, local);

        assertEquals(1f, values.getAsJsonObject("grid").get("density").getAsFloat());
        assertEquals(2, values.getAsJsonObject("grid").get("dotSize").getAsInt());
        assertEquals(96, values.getAsJsonObject("interaction").getAsJsonObject("pointerLens")
                .get("radius").getAsInt());
        assertTrue(ThemeEffectPresets.forGroup("dotMatrix").stream()
                .anyMatch(preset -> preset.name().equals("rainbow") && preset.matches(values)));

        var edited = ThemeEffectFields.edit(settings, "dotMatrix", inherited, local,
                List.of("grid", "density"), new JsonPrimitive(0.5f));
        var draft = ThemeEffects.previewSettings(settings, "dotMatrix", inherited, edited);
        var backdrop = UiDotBackground.defaultProfile(UiTheme.resolve(settings.theme()));
        assertEquals(0.5f, ThemeEffects.profile(draft.theme(), "dotMatrix", backdrop).grid().density());
        assertEquals(1f, ThemeEffectFields.values(settings, "dotMatrix", inherited, local)
                .getAsJsonObject("grid").get("density").getAsFloat());
    }

    @Test
    void partialEditPreservesInheritedAndUnrelatedValues() {
        var inherited = new JsonObject();
        var grid = new JsonObject();
        grid.addProperty("spacing", 14);
        inherited.add("grid", grid);
        var edited = ThemeEffectFields.edit(ClientUiSettings.DEFAULT, "dotMatrix", inherited, new JsonObject(),
                List.of("grid", "dotSize"), new JsonPrimitive(12));
        assertEquals(12, edited.getAsJsonObject("grid").get("dotSize").getAsInt());
        assertFalse(edited.getAsJsonObject("grid").has("spacing"));
        assertEquals(14, ThemeEffectFields.get(ThemeEffectFields.values(ClientUiSettings.DEFAULT, "dotMatrix", inherited, edited),
                List.of("grid", "spacing")).getAsInt());
        assertThrows(IllegalArgumentException.class, () -> ThemeEffectFields.edit(ClientUiSettings.DEFAULT, "dotMatrix", inherited, edited,
                List.of("grid", "dotSize"), new JsonPrimitive(15)));
    }

    @Test
    void dotMatrixPresetsReachTheRendererWithGalleryColorsAndKeepInheritedDirection() {
        String group = "dotMatrix";
        var inherited = JsonParser.parseString("""
                {"grid":{"spacing":12},"gradient":{"flow":"OUTWARD"}}
                """).getAsJsonObject();
        var local = JsonParser.parseString("""
                {"palette":{"glow":1}}
                """).getAsJsonObject();
        var gradients = List.of(DotMatrixProfile.Gradient.NONE, DotMatrixProfile.Gradient.RAINBOW,
                DotMatrixProfile.Gradient.REVERSE_RAINBOW, DotMatrixProfile.Gradient.AURORA);
        var presets = ThemeEffectPresets.forGroup(group);
        assertEquals(gradients.size(), presets.size());
        for (int i = 0; i < presets.size(); i++) {
            var preset = presets.get(i);
            local = preset.apply(group, local);
            var resolved = ThemeEffects.resolveEditor(ClientUiSettings.DEFAULT, group, inherited, local);
            assertTrue(preset.matches(resolved));
            assertEquals(1, presets.stream().filter(other -> other.matches(resolved)).count());

            var overrides = ThemeOverrides.EMPTY.withGroup(group, inherited)
                    .overlay(ThemeOverrides.EMPTY.withGroup(group, local));
            var theme = ThemeSettings.DEFAULT.overlayEffects(new ThemeOverrides(overrides.json()));
            var profile = ThemeEffects.profile(theme, group, DotMatrixProfile.VIOLET);
            assertEquals(gradients.get(i).stops(), profile.gradient().stops());
            assertEquals(gradients.get(i).periodMs(), profile.gradient().periodMs());
            assertSame(DotMatrixProfile.Flow.OUTWARD, profile.gradient().flow());
            assertEquals(12, profile.grid().spacing());
            assertEquals(1f, profile.palette().glow());
        }
        var solid = presets.get(0).apply(group, local);
        assertEquals(0, solid.getAsJsonObject("gradient").getAsJsonArray("stops").size());
        assertEquals(5000, solid.getAsJsonObject("gradient").get("periodMs").getAsInt());
    }

    @Test
    void electricPresetsResolveToDistinctStylesAndKeepSizeAndPointerSettings() {
        String group = "electricLogo";
        var local = JsonParser.parseString("""
                {"lines":{"scale":0.45},"interaction":{"enabled":false}}
                """).getAsJsonObject();
        var presets = ThemeEffectPresets.forGroup(group);
        assertEquals(List.of("violet", "storm", "neon", "outline"),
                presets.stream().map(ThemeEffectPresets.Preset::name).toList());
        var profiles = presets.stream().map(preset -> {
            var draft = preset.apply(group, local);
            var resolved = ThemeEffects.resolveEditor(ClientUiSettings.DEFAULT, group, new JsonObject(), draft);
            assertEquals(1, presets.stream().filter(other -> other.matches(resolved)).count());
            assertTrue(preset.matches(resolved));
            var theme = ThemeSettings.DEFAULT.overlayEffects(ThemeOverrides.EMPTY.withGroup(group, draft));
            var profile = ThemeEffects.profile(theme, group, ElectricLogoProfile.DEFAULT);
            assertEquals(0.45f, profile.lines().scale());
            assertFalse(profile.interaction().enabled());
            return profile;
        }).toList();
        assertEquals(4, profiles.stream().map(profile -> profile.palette().glowArgb()).distinct().count());
        assertEquals(4f, profiles.get(1).motion().arcs());
        assertEquals(0f, profiles.get(2).motion().arcs());
        assertEquals(0f, profiles.get(3).motion().speed());
        assertEquals(1, profiles.get(3).lines().strands());
    }

    @Test
    void newRegistrationsProduceControlsWithoutConsumerChanges() {
        Runnable unregister = ThemeEffects.registerProfile("test:effect_fields", new Extension(true, Mode.FIRST, 4));
        try {
            var fields = ThemeEffectFields.fields("test:effect_fields");
            assertEquals(3, fields.size());
            assertEquals(List.of("FIRST", "SECOND"), fields.get(1).options());
            assertEquals(ThemeEffectFields.Kind.NUMBER, fields.get(2).kind());
        } finally {
            unregister.run();
        }
    }

    private static long leaves(JsonObject object) {
        return object.entrySet().stream().mapToLong(entry -> entry.getValue().isJsonObject()
                ? leaves(entry.getValue().getAsJsonObject()) : 1).sum();
    }

    public enum Mode { FIRST, SECOND }
    public record Extension(boolean visible, Mode mode, int seed) {}
}
