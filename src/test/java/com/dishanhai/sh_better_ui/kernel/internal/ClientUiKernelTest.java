// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiScreenBuilder;
import com.dishanhai.sh_better_ui.client.gui.display.ElectricLogoProfile;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.PatternWavesProfile;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.DynamicThemeSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityStatus;
import com.dishanhai.sh_better_ui.kernel.api.HudService;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIPermission;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceSettings;
import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.dishanhai.sh_better_ui.theme.transition.TransitionSpec;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ClientUiKernelTest {
    @Test
    void shutdownRemovesTheActiveComposedPageBeforeReleasingItsTheme() {
        int[] removals = {0};
        var screen = new UiScreenBuilder(Component.empty()).backButton(false)
                .onRemoved(page -> removals[0]++).build();
        var context = screen.context();

        ClientUiKernel.removeActivePage(screen);

        assertEquals(1, removals[0]);
        assertTrue(context.isClosed());
        assertDoesNotThrow(() -> screen.render(null, 0, 0, 0));
    }

    @Test
    void registersHardwareAndHudServicesWithNativeCapabilities() throws Exception {
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of(UIPermission.REGISTER_RENDER_PASS))) {
            var engine = fixture.module.engine();
            assertEquals(CapabilityStatus.AVAILABLE, engine.capabilities().status(UIApis.COMPUTER_STATUS_READ));
            assertEquals(CapabilityStatus.AVAILABLE, engine.capabilities().status(UIApis.NATIVE_HUD));
            assertSame(com.dishanhai.sh_better_ui.client.system.ClientComputerStatus.INSTANCE,
                    engine.require(UIApis.COMPUTER_STATUS));
            var empty = engine.require(UIApis.COMPUTER_STATUS).read(Set.of());
            assertTrue(empty.memory().isEmpty() && empty.cpu().isEmpty() && empty.gpu().isEmpty() && empty.disk().isEmpty());
            var hud = engine.require(UIApis.HUD);
            var handle = hud.mount(fixture.module, frame -> fail("A CPU-only frame cannot draw HUDs"));
            assertThrows(IllegalStateException.class, () -> hud.textPalette(fixture.module,
                    new HudService.Frame(null, null, 0), UiId.parse("example:panel"), new Rect(0, 0, 10, 10),
                    0xff000000, 0xff000000));
            fixture.client.runFrame();
            handle.close();
            handle.close();
        }
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            var hud = fixture.module.engine().require(UIApis.HUD);
            assertThrows(SecurityException.class, () -> hud.mount(fixture.module, frame -> {}));
        }
    }

    @Test
    void scopedThemeOverridesInheritAndExpireWithTheirKernelNodes() {
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            var service = fixture.publication.current().require(UIApis.THEME);
            var root = fixture.tree();
            var node = root.openChild(ScopeKind.COMPONENT, UiId.parse("example:theme_node"));
            var child = service.scope(fixture.module, node);
            var parent = service.scope(fixture.module, root);
            var token = ThemeTokens.SPACING_SM;
            var global = service.snapshot().resolve(token);
            parent.configure(builder -> builder.override(token, global + 3));
            assertEquals(global + 3, child.resolve(token));
            child.configure(builder -> builder.override(token, global + 1));
            assertEquals(global + 1, child.resolve(token));
            assertEquals(global, service.snapshot().resolve(token));
            root.close();
            assertFalse(child.isOpen());
            assertThrows(IllegalStateException.class, () -> child.resolve(token));
        }
    }

    @Test
    void builtInServiceUsesTheOriginalManagerAndExpiresWithTheHost() {
        for (var platform : List.of(ModuleDescriptor.Platform.FORGE_1_20_1, ModuleDescriptor.Platform.NEOFORGE_1_21_1)) {
            var publication = new EnginePublication();
            var manager = new ThemeManager();
            var host = ClientUiKernel.install(platform, publication, manager, manager::close);
            var engine = publication.current();
            var service = engine.require(UIApis.THEME);
            try (host) {
                assertNotNull(engine.require(UIApis.APPEARANCE));
                assertEquals(CapabilityStatus.AVAILABLE, engine.capabilities().status(UIApis.DYNAMIC_THEME));
                assertTrue(service.availableThemes().contains(BuiltinThemes.LIGHT_ID));
                assertSame(manager.snapshot(), service.snapshot());
                manager.transitionTo(BuiltinThemes.LIGHT_ID, TransitionSpec.immediate());
                assertSame(manager.snapshot(), service.snapshot());
                assertEquals(BuiltinThemes.LIGHT_ID, service.snapshot().themeIds().get(0));
                assertTrue(service.availableThemes().contains(BuiltinThemes.LIQUID_GLASS));
                manager.setTheme(BuiltinThemes.LIQUID_GLASS);
                assertEquals(List.of(BuiltinThemes.LIQUID_GLASS), service.snapshot().themeIds());
                assertTrue(service.snapshot().resolve(ThemeTokens.LIQUID_GLASS));
                assertEquals(12f, service.snapshot().resolve(ThemeTokens.BLUR));
                assertTrue(service.availableThemes().contains(BuiltinThemes.HUD_GLASS));
                manager.setTheme(BuiltinThemes.HUD_GLASS);
                assertEquals(List.of(BuiltinThemes.HUD_GLASS), service.snapshot().themeIds());
                assertTrue(service.snapshot().resolve(ThemeTokens.HUD_GLASS));
                host.runFrame();
                assertEquals(1, engine.scheduler().snapshot().frame());
            }
            assertNull(publication.current());
            assertTrue(manager.isClosed());
            assertThrows(IllegalStateException.class, service::snapshot);
            assertThrows(IllegalStateException.class, service::availableThemes);
        }
    }

    @Test
    void appearanceAndMaterialCatalogResolveTheSameNativeEffectsAndPresets() {
        var publication = new EnginePublication();
        var manager = new ThemeManager();
        try (var host = ClientUiKernel.install(ModuleDescriptor.Platform.FORGE_1_20_1, publication, manager, manager::close)) {
            var engine = publication.current();
            var appearance = engine.require(UIApis.APPEARANCE);
            assertSame(ShBetterUiApi.backgrounds(), appearance.backgrounds());
            for (var background : appearance.backgrounds().entries()) {
                assertTrue(appearance.groups().contains(background.settingsGroup()));
                assertTrue(appearance.template(background.settingsGroup()).size() > 0);
            }
            var catalog = engine.require(UIApis.MATERIAL_CATALOG);
            for (String id : List.of("dot_matrix", "electric_logo", "pattern_waves", "ascii_field", "shape_waves",
                    "visual_reveal", "antigravity", "light_rays", "laser_flow", "code_rain")) {
                var material = catalog.find("sh_better_ui:" + id).orElseThrow();
                assertEquals("sh_better_ui:" + id, material.nativePreviewId());
                assertTrue(ShBetterUiApi.developerPreviews().find(material.nativePreviewId()).isPresent());
            }

            var settings = ClientUiSettings.DEFAULT;
            var empty = new JsonObject();
            assertEquals(4, appearance.presets("dotMatrix").size());
            assertEquals(4, appearance.presets("electricLogo").size());
            assertEquals(6, appearance.presets("patternWaves").size());
            var rainbow = appearance.applyPreset(settings, "dotMatrix", "rainbow", empty, empty);
            var storm = appearance.applyPreset(settings, "electricLogo", "storm", empty, empty);
            var pond = appearance.applyPreset(settings, "patternWaves", "pond", empty, empty);
            var draft = appearance.draft(settings, "dotMatrix", empty, rainbow);
            draft = appearance.draft(draft, "electricLogo", empty, storm);
            draft = appearance.draft(draft, "patternWaves", empty, pond);
            appearance.preview(draft, () -> {
                assertEquals(DotMatrixProfile.Gradient.RAINBOW, appearance.profile("dotMatrix", DotMatrixProfile.VIOLET).gradient());
                assertEquals(ElectricLogoProfile.Preset.STORM.profile(), appearance.profile("electricLogo", ElectricLogoProfile.DEFAULT));
                assertEquals(PatternWavesProfile.Preset.POND.profile(),
                        appearance.profile("patternWaves", PatternWavesProfile.Preset.SILK.profile()));
            });
            assertEquals(0, empty.size());
            assertThrows(IllegalArgumentException.class, () -> appearance.applyPreset(settings, "electricLogo", "rainbow", empty, empty));
        }
    }

    @Test
    void importsLegacySettingsAndOwnsThePageThemeLifetime() {
        var previous = ClientUiSettings.current();
        var next = previous.withDynamicTheme(new DynamicThemeSettings("example:theme", 125, true));
        ClientUiSettings.update(next);
        var publication = new EnginePublication();
        var manager = new ThemeManager();
        try (var host = ClientUiKernel.install(ModuleDescriptor.Platform.FORGE_1_20_1, publication, manager, manager::close)) {
            var configs = publication.current().configs();
            assertEquals(next, configs.get(UIApis.CLIENT_SETTINGS));
            assertThrows(SecurityException.class, () -> configs.set(UIApis.CLIENT_SETTINGS, next));
            ClientUiSettings.update(previous);
            host.runFrame();
            assertEquals(next, configs.get(UIApis.CLIENT_SETTINGS));
            var page = manager.global().child("screen");
            var lifetime = host.attachPageTheme(page);
            lifetime.close();
            assertTrue(page.isClosed());
            var remount = host.attachPageTheme(manager.global().child("screen"));
            assertNotEquals(lifetime.generation(), remount.generation());
        } finally {
            ClientUiSettings.update(previous);
        }
    }

    @Test
    void userGlobalSettingsDriveCompatibilityReadersAndSurviveRestart(@TempDir Path storage) {
        var legacy = ClientUiSettings.current();
        var imported = legacy.withDisplay(true, false, false);
        var effects = new JsonObject();
        effects.addProperty("raysOrigin", "TOP_RIGHT");
        var theme = imported.theme().withMode(ThemeSettings.Mode.CUSTOM).withBackgroundEffect("lightRays");
        var saved = imported.withTheme(theme.withEffects(theme.effects().withGroup("lightRays", effects)))
                .withDynamicTheme(new DynamicThemeSettings(BuiltinThemes.NETHER, 125, true));
        var publication = EnginePublication.CLIENT;
        try {
            ClientUiSettings.update(imported);
            var manager = new ThemeManager();
            try (var client = ClientUiKernel.install(ModuleDescriptor.Platform.FORGE_1_20_1, publication, manager,
                    manager::close, System::nanoTime, List.of(), LiveSurfaceSettings.DEFAULT, storage)) {
                assertEquals(imported, ClientUiSettings.current());
                assertTrue(client.saveSettings(saved));
                assertEquals(saved, publication.current().configs().get(UIApis.CLIENT_SETTINGS));
                assertEquals(saved, ShBetterUiApi.settings().current());
                ClientUiSettings.update(legacy);
                client.runFrame();
                assertEquals(saved, ClientUiSettings.current());
            }

            var reopened = new ThemeManager();
            try (var client = ClientUiKernel.install(ModuleDescriptor.Platform.FORGE_1_20_1, publication, reopened,
                    reopened::close, System::nanoTime, List.of(), LiveSurfaceSettings.DEFAULT, storage)) {
                assertEquals(saved, publication.current().configs().get(UIApis.CLIENT_SETTINGS));
                assertEquals(saved, ClientUiSettings.current());
            }
        } finally {
            ClientUiSettings.update(legacy);
        }
    }

    @Test
    void kernelSettingsSavedBeforeBackgroundSelectionStillLoad() {
        var original = ClientUiSettings.DEFAULT;
        var encoded = com.google.gson.JsonParser.parseString(ClientSettingsCodec.CODEC.encode(original)).getAsJsonObject();
        encoded.getAsJsonObject("theme").remove("backgroundEffect");
        encoded.getAsJsonObject("theme").remove("hudBackgroundEffect");
        encoded.getAsJsonObject("theme").remove("hudBlurRadius");
        assertEquals(original, ClientSettingsCodec.CODEC.decode(encoded.toString()));
    }

    @Test
    void corruptSettingsKeepTheirOriginalBytesAndCanBeSavedAgain(@TempDir Path directory) throws Exception {
        String key = UIApis.CLIENT_SETTINGS.id().toString().replace(":", "\\:");
        String type = ClientUiSettings.class.getName();
        String validSettings = ClientSettingsCodec.CODEC.encode(ClientUiSettings.DEFAULT);
        List<String> invalid = List.of(key + ".version=1\n", "broken=\\uXXZZ\n",
                key + ".version=2147483647\n" + key + ".type=" + type + "\n" + key + ".value=" + validSettings + "\n",
                key + ".version=1\n" + key + ".type=" + type + "\n" + key + ".value={\n");
        var previous = ClientUiSettings.current();
        var imported = ClientUiSettings.DEFAULT.withDisplay(true, false, false);
        var saved = imported.withDynamicTheme(new DynamicThemeSettings(BuiltinThemes.NETHER, 125, true));
        try {
            ClientUiSettings.update(imported);
            for (int index = 0; index < invalid.size(); index++) {
                Path storage = Files.createDirectory(directory.resolve("case-" + index));
                Path file = storage.resolve(UIApis.CLIENT_SETTINGS.id().namespace() + ".properties");
                String original = invalid.get(index);
                Files.writeString(file, original);
                var publication = new EnginePublication();
                var manager = new ThemeManager();
                try (var client = ClientUiKernel.install(ModuleDescriptor.Platform.FORGE_1_20_1, publication, manager,
                        manager::close, System::nanoTime, List.of(), LiveSurfaceSettings.DEFAULT, storage)) {
                    assertEquals(imported, publication.current().configs().get(UIApis.CLIENT_SETTINGS));
                    assertTrue(client.saveSettings(saved));
                }
                try (var files = Files.list(storage)) {
                    var backups = files.filter(path -> path.toString().endsWith(".properties.corrupt")).toList();
                    assertEquals(1, backups.size());
                    assertEquals(original, Files.readString(backups.get(0)));
                }
                var reopened = new ThemeManager();
                try (var client = ClientUiKernel.install(ModuleDescriptor.Platform.FORGE_1_20_1, publication, reopened,
                        reopened::close, System::nanoTime, List.of(), LiveSurfaceSettings.DEFAULT, storage)) {
                    assertEquals(saved, publication.current().configs().get(UIApis.CLIENT_SETTINGS));
                }
            }
        } finally {
            ClientUiSettings.update(previous);
        }
    }

    @Test
    void liquidGlassModeAndDynamicThemeRoundTripTogether() {
        for (var mode : List.of(ThemeSettings.Mode.LIQUID_GLASS, ThemeSettings.Mode.HUD_GLASS)) {
            var settings = ClientUiSettings.DEFAULT.withTheme(ThemeSettings.DEFAULT.withMode(mode)
                            .withBackgroundEffect("lightRays").withHudBackgroundEffect("dotMatrix").withHudBlurRadius(0))
                    .withDynamicTheme(new DynamicThemeSettings(BuiltinThemes.END, 125, true));
            assertEquals(settings, ClientSettingsCodec.CODEC.decode(ClientSettingsCodec.CODEC.encode(settings)));
        }
    }

    @Test
    void permittedModulesCanWriteUserGlobalSettingsWithoutFramePollingOverwritingThem() {
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of(UIPermission.MODIFY_GLOBAL_CONFIG))) {
            var configs = fixture.module.engine().configs();
            var next = configs.get(UIApis.CLIENT_SETTINGS).withDynamicTheme(
                    new DynamicThemeSettings(BuiltinThemes.END, 0, false));
            configs.beginTransaction().set(configs.userGlobal(), UIApis.CLIENT_SETTINGS, next).commit();
            fixture.client.runFrame();
            assertEquals(next, fixture.publication.current().configs().get(UIApis.CLIENT_SETTINGS));
        }
    }
}
