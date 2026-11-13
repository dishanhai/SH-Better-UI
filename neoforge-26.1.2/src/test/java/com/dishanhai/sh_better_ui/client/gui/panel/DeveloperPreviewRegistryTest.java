// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialCatalog;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialEntry;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DeveloperPreviewRegistryTest {
    @Test
    void bundledCatalogRoutesResolveThroughTheGalleryRegistry() throws Exception {
        var registry = ShBetterUiApi.developerPreviews();
        assertEquals(registry.ids(), ShBetterUiApi.settings().developerSampleIds());
        assertTrue(registry.ids().contains("live_activity"));
        try (var stream = getClass().getResourceAsStream("/assets/sh_better_ui/material_catalog/catalog.json")) {
            assertNotNull(stream);
            var catalog = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            int routes = 0;
            for (var node : catalog.getAsJsonArray("materials")) {
                var id = node.getAsJsonObject().get("nativePreviewId");
                if (id.isJsonNull()) continue;
                assertEquals(id.getAsString(), registry.find(id.getAsString()).orElseThrow().id());
                routes++;
            }
            assertTrue(routes > 0);
        }
    }

    @Test
    void extensionIsDiscoverableAndFactoryStaysLazyDuringLookup() {
        var creations = new AtomicInteger();
        var spec = new PreviewSpec("test_addon:live", PreviewSpec.Category.MATERIALS, 1000,
                "gui.done", () -> {
                    creations.incrementAndGet();
                    return host -> {};
                });
        var settings = ShBetterUiApi.settings();
        try (var registration = ShBetterUiApi.developerPreviews().register(spec)) {
            assertTrue(settings.developerSampleIds().contains(spec.id()));
            assertEquals("gui.done", ShBetterUiApi.developerPreviews().find(spec.id()).orElseThrow().titleKey());
            assertEquals(0, creations.get());
        }
        assertFalse(settings.developerSampleIds().contains(spec.id()));
        assertThrows(IllegalArgumentException.class,
                () -> settings.developerSampleFullscreen(null, spec.id(), UiPreferences.DEFAULT));
    }

    @Test
    void materialRouteRejectsRemovedOrReplacedRegistrations() {
        var registry = new PreviewRegistry();
        var spec = new PreviewSpec("example:preview", PreviewSpec.Category.MATERIALS, 0, "gui.done", () -> host -> {});
        var first = registry.register(spec);
        var entry = material();
        var live = new AtomicReference<>(entry);
        var catalog = (MaterialCatalog) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MaterialCatalog.class}, (proxy, method, args) -> {
                    if (method.getName().equals("find")) return Optional.ofNullable(live.get());
                    throw new UnsupportedOperationException(method.getName());
                });
        var route = new NativeMaterialRoute(catalog, entry, registry.find(spec.id()).orElseThrow());
        assertTrue(route.available(registry, catalog));
        assertFalse(route.available(registry, null));
        live.set(material());
        assertFalse(route.available(registry, catalog));
        live.set(entry);
        first.close();
        assertFalse(route.available(registry, catalog));
        try (var replacement = registry.register(spec)) {
            assertFalse(route.available(registry, catalog));
        }
    }

    private static MaterialEntry material() {
        String symbol = "example.Preview#draw";
        return new MaterialEntry("1.0.0", "example:material", "1.0.0", "Example", Map.of(), List.of(), "glass",
                List.of(), "Example", List.of(), List.of(), "https://example.com", "fixture", List.of("Example.java"),
                List.of(symbol), symbol, List.of(), List.of(), Map.of(), null, "example:preview", null, null, null,
                List.of(), Map.of("forge", "unverified", "neoforge", "unverified"), "native-only", "unverified",
                List.of(), false, null);
    }
}
