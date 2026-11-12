// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.CapabilityStatus;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.VersionRange;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialEntry;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ClientMaterialCatalogTest {
    private static final String GLASS = "sh_better_ui:liquid_glass";

    @Test
    void bothPlatformsPublishTheSameRendererFreeDirectory() {
        String hash = null;
        for (var platform : List.of(ModuleDescriptor.Platform.FORGE_1_20_1, ModuleDescriptor.Platform.NEOFORGE_1_21_1)) {
            var publication = new EnginePublication();
            var manager = new ThemeManager();
            try (var client = ClientUiKernel.install(platform, publication, manager, manager::close)) {
                var engine = publication.current();
                var catalog = engine.require(UIApis.MATERIAL_CATALOG);
                assertEquals(CapabilityStatus.AVAILABLE, engine.capabilities().status(UIApis.MATERIAL_DISCOVERY));
                var original = engine.require(UIApis.GLASS).diagnostics();
                var glass = catalog.find(GLASS).orElseThrow();
                assertSame(glass, CompletableFuture.supplyAsync(() -> catalog.find("Ｌｉｑｕｉｄ＿Ｇｌａｓｓ").orElseThrow()).join());
                assertEquals(GLASS, glass.nativePreviewId());
                assertEquals("sh_better_ui:clear_glass", catalog.find("clear_glass").orElseThrow().id());
                assertFalse(catalog.byCategory("glass").isEmpty());
                assertTrue(catalog.byCapability("sh_better_ui:render").contains(glass));
                assertThrows(UnsupportedOperationException.class, () -> catalog.entries().clear());
                assertThrows(UnsupportedOperationException.class, () -> glass.defaultValues().clear());
                assertEquals(original, engine.require(UIApis.GLASS).diagnostics());
                var nativeDefault = GlassMaterial.preset(GlassVariant.REGULAR);
                var optics = nativeDefault.optics();
                var expected = Map.of("transmission", optics.transmission(), "refraction", optics.refraction(),
                        "lensing", optics.lensing(), "scattering", optics.scattering(), "edgeHighlight", optics.edgeHighlight(),
                        "thickness", optics.thickness(), "blurRadius", optics.blurRadius());
                expected.forEach((key, value) -> assertEquals(value.floatValue(), ((Number) glass.defaultValues().get(key)).floatValue()));
                assertEquals(nativeDefault.quality().name(), glass.defaultValues().get("quality"));
                assertEquals(nativeDefault.foreground().name(), glass.defaultValues().get("foreground"));
                if (hash != null) assertEquals(hash, catalog.snapshot().contentHash());
                hash = catalog.snapshot().contentHash();
            }
        }
    }

    @Test
    void sharedPresetFixtureMigratesAndInvalidImportsLeavePriorValuesUsable() throws Exception {
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            var catalog = fixture.publication.current().require(UIApis.MATERIAL_CATALOG);
            String legacy = Files.readString(Path.of("catalog/fixtures/presets/liquid-glass-draft.json"));
            var migrated = catalog.importPreset(legacy);
            assertEquals(GLASS, migrated.materialId());
            assertEquals(7.25, migrated.parameters().get("blurRadius"));
            assertEquals("DARK", migrated.parameters().get("foreground"));
            var exported = catalog.exportPreset(GLASS, migrated.parameters());
            assertEquals(migrated, catalog.importPreset(exported));
            assertThrows(IllegalArgumentException.class, () -> catalog.importPreset(exported.replace("7.25", "-7.25")));
            assertThrows(IllegalArgumentException.class, () -> catalog.importPreset(exported.replace("\"DARK\"", "\"BLUE\"")));
            assertThrows(IllegalArgumentException.class, () -> catalog.importPreset(exported.replace("\"DARK\"", "null")));
            assertThrows(IllegalArgumentException.class, () -> catalog.importPreset(exported.replace("\"1.0.0\"", "\"2.0.0\"")));
            assertThrows(IllegalArgumentException.class, () -> catalog.importPreset(" ".repeat(65_537)));
            assertThrows(IllegalArgumentException.class, () -> catalog.importPreset(exported + " trailing"));
            assertEquals(migrated, catalog.importPreset(exported));
        }
    }

    @Test
    void moduleOwnsItsRegistrationAndSnapshotsSurviveRevocation() {
        var fixture = new ClientServiceFixture(Set.of(), Set.of());
        try (fixture) {
            var catalog = fixture.publication.current().require(UIApis.MATERIAL_CATALOG);
            var base = catalog.find(GLASS).orElseThrow();
            assertThrows(SecurityException.class, () -> catalog.register(fixture.module, base));
            assertThrows(IllegalArgumentException.class, () -> catalog.register(fixture.module,
                    extension("other_glass", List.of("liquid_glass"), List.of(), List.of(), "Conflicting")));
            assertThrows(IllegalArgumentException.class, () -> catalog.register(fixture.module,
                    extension("other_glass", List.of(), List.of("example:missing"), List.of(), "No capability")));
            assertThrows(IllegalArgumentException.class, () -> catalog.register(fixture.module,
                    extension("other_glass", List.of(), List.of(), List.of("example:missing.png"), "No resource")));
            var original = extension("other_glass", List.of("example_glass"), List.of(), List.of(), "Test surface");
            var binding = catalog.register(fixture.module, original);
            assertTrue(binding.isActive());
            assertThrows(IllegalArgumentException.class, () -> catalog.register(fixture.module, original));
            var frozen = catalog.snapshot();
            var edited = extension("other_glass", List.of("example_glass"), List.of(), List.of(), "Updated");
            binding.replace(edited);
            assertSame(edited, catalog.find("example_glass").orElseThrow());
            assertSame(original, frozen.find("example_glass").orElseThrow());
            binding.close();
            binding.close();
            assertFalse(binding.isActive());
            assertTrue(catalog.find("example_glass").isEmpty());
            var tied = catalog.register(fixture.module, original);
            fixture.client.close();
            assertFalse(tied.isActive());
            assertTrue(catalog.find(original.id()).isEmpty());
        }
    }

    @Test
    void resourceLossAndRestorationPublishWholeSnapshots() {
        var platform = ModuleDescriptor.Platform.FORGE_1_20_1;
        var publication = new EnginePublication();
        try (var host = new KernelHost(platform, publication)) {
            var present = new AtomicBoolean(true);
            var catalog = new ClientMaterialCatalog(host, platform, ignored -> present.get());
            var entry = extension("reload", List.of(), List.of(), List.of("example:textures/one.png"), "Reloadable");
            var plugin = new UIModule() {
                @Override public ModuleDescriptor descriptor() {
                    return ModuleDescriptor.builder().id(new UiId("example", "reload")).version("1.0.0")
                            .requiredCapability(UIApis.MATERIAL_DISCOVERY, VersionRange.ANY).build();
                }
                @Override public void register(ModuleContext context) {}
                @Override public void start(ModuleContext context) {
                    context.engine().require(UIApis.MATERIAL_CATALOG).register(context, entry);
                }
                @Override public void stop(ModuleContext context) {}
            };
            host.startNative(List.of(BuiltinService.candidate(UIApis.MATERIAL_CATALOG, catalog, UIApis.MATERIAL_DISCOVERY),
                    new ModuleDiscovery.Candidate("example", "fixture", plugin, Set.of(), false)));
            var original = catalog.snapshot();
            assertSame(entry, original.find(entry.id()).orElseThrow());
            present.set(false);
            catalog.refreshResources();
            assertTrue(catalog.find(entry.id()).isEmpty());
            assertTrue(catalog.snapshot().generation() > original.generation());
            assertSame(entry, original.find(entry.id()).orElseThrow());
            present.set(true);
            catalog.refreshResources();
            assertSame(entry, catalog.find(entry.id()).orElseThrow());
            host.stopModule(new UiId("example", "reload"));
            assertTrue(catalog.find(entry.id()).isEmpty());
        }
    }

    private static MaterialEntry extension(String name, List<String> aliases, List<String> capabilities, List<String> assets,
                                           String description) {
        String symbol = "example.Glass#render";
        return new MaterialEntry("1.0.0", "example:" + name, "1.0.0", "Example glass", Map.of(), aliases, "glass",
                List.of(), description, List.of(), List.of(), "https://example.com/ui", "fixture", List.of("example/Glass.java"),
                List.of(symbol), symbol, capabilities, assets, Map.of(), null, null, null, null, null, List.of(),
                Map.of("forge", "unverified", "neoforge", "unverified"), "native-only", "unverified", List.of(), false, null);
    }
}
