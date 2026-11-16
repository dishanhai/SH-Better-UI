package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.ThemeOverrides;
import com.dishanhai.sh_better_ui.kernel.api.AppearanceService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class BlackHoleBackdropIntegrationTest {
    private static final String[] IDS = {BlackHoleBackdrop.ID};
    private static final BlackHoleBackdropProfile[] DEFAULTS = {BlackHoleBackdropProfile.DEFAULT};
    private static final AppearanceService APPEARANCE = new AppearanceService() {
        @Override public Screen edit(Screen parent, ClientUiSettings settings, String group, JsonObject inherited,
                                     JsonObject local, Consumer<JsonObject> apply) { throw new UnsupportedOperationException(); }
    };

    @Test void blackHoleSharesUukRegistryAndOwnsIndependentRenderers() {
        var registry = APPEARANCE.backgrounds();
        assertSame(ShBetterUiApi.backgrounds(), registry);
        for (int i = 0; i < IDS.length; i++) {
            var spec = registry.find(IDS[i]).orElseThrow();
            assertEquals(IDS[i], spec.settingsGroup());
            assertEquals("sh_better_ui.panel.theme.group." + IDS[i], spec.titleKey());
            assertEquals(DEFAULTS[i], spec.defaultProfile());
            var first = spec.factory().create(null);
            var second = spec.factory().create(null);
            assertInstanceOf(BlackHoleBackdrop.class, first);
            assertNotSame(first, second);
            assertDoesNotThrow(first::close);
            assertDoesNotThrow(second::close);
        }
    }

    @Test void allTenEditorFieldsAndTheirRangesMatchProfile() {
        APPEARANCE.backgrounds();
        for (String id : IDS) {
            assertTrue(APPEARANCE.groups().contains(id));
            var fields = ThemeEffectFields.fields(id);
            assertEquals(Set.of("speed", "size", "tilt", "diskWidth", "lensing", "brightness", "glow", "opacity", "color", "backgroundColor"),
                    fields.stream().map(ThemeEffectFields.Field::name).collect(java.util.stream.Collectors.toSet()));
            assertEquals(10, APPEARANCE.template(id).size());
            for (var field : fields) {
                assertEquals(List.of(field.name()), field.path());
                if ((field.name().equals("color") || field.name().endsWith("Color"))) {
                    assertEquals(ThemeEffectFields.Kind.ARGB, field.kind());
                } else {
                    assertEquals(ThemeEffectFields.Kind.NUMBER, field.kind());
                    var range = ThemeEffectRanges.find(id, field);
                    assertNotNull(range, id + ":" + field.name());
                    double unit = Math.pow(10, range.scale());
                    double min = switch (field.name()) { case "size" -> .5; case "tilt" -> -.7; case "diskWidth" -> .05; default -> 0; };
                    double max = switch (field.name()) { case "speed", "brightness" -> 2; case "size" -> 1.8; case "tilt" -> .7; case "diskWidth" -> .35; case "lensing" -> 1.5; default -> 1; };
                    assertEquals(min, range.min() / unit);
                    assertEquals(max, range.max() / unit);
                }
            }
        }
    }

    @Test void editorValuesRoundTripAndPreviewIsIsolatedForEachBackground() {
        APPEARANCE.backgrounds();
        for (int i = 0; i < IDS.length; i++) {
            String id = IDS[i]; var defaults = DEFAULTS[i];
            JsonObject local = new JsonObject();
            local = ThemeEffectFields.edit(ClientUiSettings.DEFAULT, id, new JsonObject(), local, List.of("speed"), new JsonPrimitive(.7f));
            local = ThemeEffectFields.edit(ClientUiSettings.DEFAULT, id, new JsonObject(), local, List.of("color"), new JsonPrimitive("#80556677"));
            var persisted = new ThemeOverrides(ThemeOverrides.EMPTY.withGroup(id, local).json());
            assertDoesNotThrow(() -> ThemeEffects.validate(persisted));
            var draft = APPEARANCE.draft(ClientUiSettings.DEFAULT, id, new JsonObject(), persisted.group(id));
            var before = ThemeEffects.settings();
            APPEARANCE.preview(draft, () -> {
                var resolved = APPEARANCE.profile(id, defaults);
                assertEquals(.7f, resolved.speed());
                assertEquals(0x80556677, resolved.color());
                assertEquals(defaults.diskWidth(), resolved.diskWidth());
            });
            assertSame(before, ThemeEffects.settings());
            var template = APPEARANCE.template(id); template.addProperty("speed", 1.9);
            assertEquals(.18f, APPEARANCE.template(id).get("speed").getAsFloat());
        }
    }

    @Test void titlesArePresentInAllBundledLanguages() throws Exception {
        APPEARANCE.backgrounds();
        for (String language : new String[]{"en_us", "zh_cn", "zh_tw"}) {
            try (var stream = getClass().getResourceAsStream("/assets/sh_better_ui/lang/" + language + ".json")) {
                assertNotNull(stream, language);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (String id : IDS) {
                    String key = APPEARANCE.backgrounds().find(id).orElseThrow().titleKey();
                    assertTrue(json.has(key), language + ":" + key);
                    assertFalse(json.get(key).getAsString().isBlank());
                    for (var field : ThemeEffectFields.fields(id)) {
                        String label = "sh_better_ui.theme_editor.field." + field.name();
                        assertTrue(json.has(label), language + ":" + label);
                        assertFalse(json.get(label).getAsString().isBlank());
                    }
                }
            }
        }
    }
    @Test void inaccessibleOrTransparentBackgroundNeedsNoGraphicsContext() {
        APPEARANCE.backgrounds();
        var backdrop = new BlackHoleBackdrop();
        var area = new com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds(0, 0, 300, 200);
        assertDoesNotThrow(() -> backdrop.draw(null, null, area, null, Double.NaN, Double.NaN, 0,
                new UiPreferences(true, false, false)));
        var empty = new com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds(0, 0, 0, 200);
        assertDoesNotThrow(() -> backdrop.draw(null, null, empty, null, Double.NaN, Double.NaN, 100,
                UiPreferences.DEFAULT));
        var values = new JsonObject(); values.addProperty("opacity", 0);
        var draft = APPEARANCE.draft(ClientUiSettings.DEFAULT, BlackHoleBackdrop.ID, new JsonObject(), values);
        APPEARANCE.preview(draft, () -> assertDoesNotThrow(() -> backdrop.draw(null, null, area, null,
                Double.NaN, Double.NaN, 200, UiPreferences.DEFAULT)));
        assertDoesNotThrow(backdrop::close);
    }

}
