package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：保证控件与材质图库的所有示例都能通过公开 ID 打开。 */
class DeveloperGalleryFullscreenTest {
    @Test
    void everyGallerySampleIdOpensItsOwnFullscreenScreen() {
        var settings = ShBetterUiApi.settings();
        var registry = ShBetterUiApi.developerPreviews();
        var ids = settings.developerSampleIds();
        assertTrue(ids.containsAll(List.of("underlined_tabs", "stepper")));
        assertTrue(ids.indexOf("underlined_tabs") < ids.indexOf("stepper"));
        assertEquals(PreviewSpec.Category.CONTROLS, registry.find("bell_toggle").orElseThrow().category());
        assertTrue(ids.containsAll(List.of("bottom_sheet", "interruptible_animation", "electric_logo", "status_icon",
                "code_rain", "laser_flow", "effect_appearance", "ascii_field", "shape_waves", "visual_reveal")));
        assertEquals(registry.ids(), ids);
        assertEquals(ids.size(), new HashSet<>(ids).size());

        for (String id : ids) {
            var screen = settings.developerSampleFullscreen(null, id, UiPreferences.DEFAULT);
            var title = assertInstanceOf(TranslatableContents.class, screen.getTitle().getContents());
            assertEquals(registry.find(id).orElseThrow().titleKey(), title.getKey());
        }
        assertThrows(IllegalArgumentException.class,
                () -> settings.developerSampleFullscreen(null, "missing", UiPreferences.DEFAULT));
    }

    @Test
    void managedLiquidGlassHasItsOwnMaterialPreview() {
        var registry = ShBetterUiApi.developerPreviews();
        var optical = registry.find("sh_better_ui:liquid_glass").orElseThrow();
        assertSame(optical, registry.find("liquid_glass").orElseThrow());
        assertEquals(PreviewSpec.Category.MATERIALS, optical.category());
        assertNotEquals(registry.find("clear_glass").orElseThrow().titleKey(), optical.titleKey());
        var screen = ShBetterUiApi.settings().developerSampleFullscreen(null, optical.id(), UiPreferences.DEFAULT);
        assertEquals(optical.titleKey(), assertInstanceOf(TranslatableContents.class, screen.getTitle().getContents()).getKey());
    }

    @Test
    void everyBundledNativePreviewIdResolvesToTheDeclaredGalleryRegistration() throws Exception {
        var registry = ShBetterUiApi.developerPreviews();
        try (var stream = getClass().getResourceAsStream("/assets/sh_better_ui/material_catalog/catalog.json")) {
            assertNotNull(stream, "Bundled catalog must be available to each loader");
            var catalog = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            int routes = 0;
            for (var node : catalog.getAsJsonArray("materials")) {
                var entry = node.getAsJsonObject();
                var nativeId = entry.get("nativePreviewId");
                if (nativeId.isJsonNull()) continue;
                String id = nativeId.getAsString();
                var preview = registry.find(id).orElseThrow(() -> new AssertionError("Missing native preview " + id));
                assertEquals(id, preview.id(), entry.get("id").getAsString());
                routes++;
            }
            assertTrue(routes > 0);
        }
    }

    @Test
    void previewHeightTakesPriorityOverTheCodeDrawer() {
        var tight = DeveloperGalleryScreen.fitPreview(427, 240, 114, true);
        assertFalse(tight.inlineCode());
        assertFalse(tight.needsFullscreen());

        var roomy = DeveloperGalleryScreen.fitPreview(427, 360, 166, true);
        assertTrue(roomy.inlineCode());
        assertFalse(roomy.needsFullscreen());

        var tooShort = DeveloperGalleryScreen.fitPreview(427, 240, 166, true);
        assertTrue(tooShort.needsFullscreen());
        assertEquals(tight.previewHeight(), tooShort.previewHeight());
        assertEquals(tight.top(), tooShort.top());

        var narrow = DeveloperGalleryScreen.fitPreview(220, 360, 46, false);
        assertTrue(narrow.needsFullscreen());
        assertEquals(roomy.previewHeight(), narrow.previewHeight());
        assertEquals(roomy.top(), narrow.top());
    }

    @Test
    void everyGallerySampleKeepsTheSamePanelGeometryAtEachWindowSize() {
        for (int height : List.of(156, 240, 360, 720)) {
            var empty = DeveloperGalleryScreen.fitPreview(427, height, 0, false);
            assertTrue(empty.previewHeight() >= 46);
            assertTrue(empty.top() >= 4);
            for (var spec : ShBetterUiApi.developerPreviews().entries()) {
                var fit = DeveloperGalleryScreen.fitPreview(427, height, spec.preferredHeight(), !spec.code().isBlank());
                assertEquals(empty.previewHeight(), fit.previewHeight(), spec.id());
                assertEquals(empty.top(), fit.top(), spec.id());
            }
        }
    }

    @Test
    void newRegistrationUsesTheExistingCatalogueAndFullscreenEntryPoint() {
        var settings = ShBetterUiApi.settings();
        var creations = new AtomicInteger();
        var spec = new PreviewSpec("test_addon:extra_widget", PreviewSpec.Category.MATERIALS, 1000,
                "gui.done", () -> {
                    creations.incrementAndGet();
                    return host -> {};
                });
        try (var registration = ShBetterUiApi.client().registerDeveloperPreview(spec)) {
            assertTrue(settings.developerSampleIds().contains(spec.id()));
            var screen = settings.developerSampleFullscreen(null, spec.id(), UiPreferences.DEFAULT);
            assertEquals("gui.done", assertInstanceOf(TranslatableContents.class, screen.getTitle().getContents()).getKey());
            assertEquals(0, creations.get());
        }
        assertFalse(settings.developerSampleIds().contains(spec.id()));
        assertThrows(IllegalArgumentException.class,
                () -> settings.developerSampleFullscreen(null, spec.id(), UiPreferences.DEFAULT));
    }
}
