package com.dishanhai.sh_better_ui.client.theme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.config.*;
import com.dishanhai.sh_better_ui.theme.*;
import com.dishanhai.sh_better_ui.theme.token.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RegisteredPaletteRuntimeTest {
    @Test void selectedGlassPreservesMaterialAndNativeScopeAgreeOnAlphaAndPrimary() {
        var settings = ThemeSettings.DEFAULT.withRegisteredTheme(MuoniumThemes.CRYSTAL)
                .withPalette(ThemeSettings.Base.DARK, "", "123456", "", "", "ffffff");
        var nativeTheme = UiTheme.resolve(settings); var base = UiTheme.resolveBase(settings);
        assertTrue(nativeTheme.liquidGlass()); assertTrue(nativeTheme.hudGlass()); assertTrue(nativeTheme.glassRootTint());
        assertEquals(base.blurRadius(), nativeTheme.blurRadius()); assertEquals(base.surface() >>> 24, nativeTheme.surface() >>> 24);
        assertEquals(base.secondarySurface() >>> 24, nativeTheme.secondarySurface() >>> 24);
        assertEquals(0x123456, nativeTheme.secondarySurface() & 0xffffff);
        try (var manager = new ThemeManager()) {
            var derived = MinecraftThemes.configuredPalette(manager, settings, "test:configured");
            manager.register(derived); manager.setTheme(derived.id());
            var scope = manager.global();
            assertEquals(nativeTheme.surface(), scope.resolve(ThemeTokens.SURFACE).argb());
            assertEquals(nativeTheme.accent(), scope.resolve(ThemeTokens.PRIMARY).argb());
            assertEquals(nativeTheme.hudGlass(), scope.resolve(ThemeTokens.HUD_GLASS));
            assertEquals(nativeTheme.glassRootTint(), scope.resolve(ThemeTokens.GLASS_ROOT_TINT));
            assertEquals(nativeTheme.blurRadius(), scope.resolve(ThemeTokens.BLUR));
            assertTrue(com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy.ratio(scope.resolve(ThemeTokens.ON_PRIMARY).argb(),
                    scope.resolve(ThemeTokens.PRIMARY).argb(), 0xff000000) >= 4.5);
        }
    }
    @Test void missingRegistrationKeepsSavedSelectionAndRecoversOnNewRegistrySnapshot() {
        var settings = ThemeSettings.DEFAULT.withRegisteredTheme("test:resource").withPalette(ThemeSettings.Base.LIGHT, "", "123456", "", "", "");
        assertEquals("test:resource", settings.registeredThemeId()); assertEquals(0xff123456, UiTheme.resolve(settings).surface());
        try (var manager = new ThemeManager()) {
            assertNull(MinecraftThemes.registeredSnapshot(manager.registry(), manager.tokenCatalog(), "test:resource"));
            manager.register(Theme.builder("test:resource").parent(MuoniumThemes.CRYSTAL).build());
            var first = MinecraftThemes.registeredSnapshot(manager.registry(), manager.tokenCatalog(), "test:resource");
            assertSame(first, MinecraftThemes.registeredSnapshot(manager.registry(), manager.tokenCatalog(), "test:resource"));
            assertTrue(first.resolve(ThemeTokens.HUD_GLASS));
            manager.register(Theme.builder("test:resource").parent(MuoniumThemes.AURORA_FROST).build(), ThemeRegistry.DuplicatePolicy.REPLACE_CODE);
            var next = MinecraftThemes.registeredSnapshot(manager.registry(), manager.tokenCatalog(), "test:resource");
            assertNotSame(first, next); assertFalse(next.resolve(ThemeTokens.HUD_GLASS));
        }
    }
    @Test void paletteDraftIsNeverReplacedByPublishedDynamicSelection() {
        var settings = ThemeSettings.DEFAULT.withRegisteredTheme(MuoniumThemes.AURORA_FROST);
        var draft = ClientUiSettings.DEFAULT.withTheme(settings); var preview = UiTheme.resolve(settings);
        assertSame(preview, MinecraftThemes.previewPalette(draft, preview));
        var classic = ClientUiSettings.DEFAULT.withTheme(ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.CUSTOM));
        assertSame(preview, MinecraftThemes.previewPalette(classic, preview));
    }
    @Test void existingDynamicSelectionIsPreservedForLegacyCustomAndRestoredAfterRegisteredTheme() {
        var classic = ClientUiSettings.DEFAULT.withTheme(ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.CUSTOM))
                .withDynamicTheme(new DynamicThemeSettings(BuiltinThemes.NETHER, 100, false));
        var initial = com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences.DEFAULT;
        var old = MinecraftThemes.selectionPreferences(classic, initial, "test:derived");
        assertEquals(BuiltinThemes.NETHER, old.fixedThemeId());
        var registered = classic.withTheme(classic.theme().withRegisteredTheme(MuoniumThemes.CRYSTAL));
        var selected = MinecraftThemes.selectionPreferences(registered, old, "test:derived");
        assertEquals("test:derived", selected.fixedThemeId());
        assertEquals(BuiltinThemes.NETHER, MinecraftThemes.selectionPreferences(classic, selected, "test:derived").fixedThemeId());
    }
    @Test void internalPaletteNeverOverwritesAnExistingRegistration() {
        try (var manager = new ThemeManager()) {
            var user = Theme.builder(MinecraftThemes.CONFIGURED_PALETTE_ID).parent(BuiltinThemes.LIGHT_ID).build();
            manager.register(user);
            var available = MinecraftThemes.availableConfiguredId(manager, null, null);
            assertNotEquals(user.id(), available); assertSame(user, manager.registry().find(user.id()).orElseThrow());
        }
    }
    @Test void galleryFilterHidesOwnedCollisionSuffixButKeepsExternalSameNamedTheme() {
        try (var manager = new ThemeManager()) {
            var external = Theme.builder(MinecraftThemes.CONFIGURED_PALETTE_ID).parent(BuiltinThemes.DARK).build();
            manager.register(external);
            var suffix = MinecraftThemes.availableConfiguredId(manager, null, null);
            var owned = MinecraftThemes.configuredPalette(manager,
                    ThemeSettings.DEFAULT.withRegisteredTheme(MuoniumThemes.CRYSTAL), suffix);
            manager.register(owned);
            try (var session = new ThemePreviewSession(suffix, candidate -> candidate != owned)) {
                session.sync(manager, manager.preferences());
                assertTrue(session.themes().stream().anyMatch(t -> t == external));
                assertFalse(session.themes().stream().anyMatch(t -> t.id().equals(suffix)));
                assertNotEquals(suffix, session.selected());
                assertThrows(IllegalArgumentException.class, () -> session.select(suffix));
                // If another registrant replaces the same ID, object ownership no longer matches.
                var replacement = Theme.builder(suffix).parent(BuiltinThemes.DARK).build();
                manager.register(replacement, ThemeRegistry.DuplicatePolicy.REPLACE_CODE);
                session.sync(manager, manager.preferences());
                assertTrue(session.themes().stream().anyMatch(t -> t == replacement));
            }
        }
    }
}
