package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlassThemeMaterialMappingTest {
    @Test void tokenBlurAndSurfaceAlphaReachNativePaletteWithoutChangingTextOpacity() {
        try (var manager = new ThemeManager()) {
            var scope = manager.global().child("glass");
            scope.configure(s -> s.override(ThemeTokens.LIQUID_GLASS, true)
                    .override(ThemeTokens.BLUR, 19f).override(ThemeTokens.SURFACE, ColorValue.argb(0x80334455)));
            var theme = UiTheme.fromScope(scope);
            assertEquals(19f, theme.materialBlurRadius(7));
            assertEquals(0x80334455, theme.surface(0xff111111));
            assertEquals(0x40334455, theme.surface(0x80111111));
            assertEquals(64, theme.controlFill(0x80111111, 0) >>> 24);
            assertEquals(255, theme.text(0xffeeeeee) >>> 24);
            UiTheme.preview(theme, () -> {
                assertEquals(19f, LiquidGlassTheme.themeBlurRadius(7));
                assertEquals(255, LiquidGlassTheme.opaqueFallback(theme.surface(0xff111111)) >>> 24);
            });
        }
    }
    @Test void legacyHudBlurSettingRemainsEffective() {
        var settings = com.dishanhai.sh_better_ui.config.ThemeSettings.DEFAULT
                .withMode(com.dishanhai.sh_better_ui.config.ThemeSettings.Mode.HUD_GLASS).withHudBlurRadius(9);
        assertEquals(9f, UiTheme.resolve(settings).materialBlurRadius(4));
    }
    @Test void fractionalOpticalBlurIsNotRoundedToZero() {
        assertEquals(.75f, LiquidGlassTheme.clearMaterial(true, .75f).optics().blurRadius());
        assertThrows(IllegalArgumentException.class, () -> LiquidGlassTheme.clearMaterial(true, Float.NaN));
    }
    @Test void legacyConstructorAndNonGlassTokensKeepCallerOpacityAndBlur() {
        var legacy = new UiTheme(false, 0xff000000, 0x80334455, 0xff555555, -1, -1, 31);
        assertEquals(7f, legacy.materialBlurRadius(7));
        assertEquals(0xff334455, legacy.surface(0xff111111));
        assertEquals(128, legacy.controlFill(0x80111111, 0) >>> 24);
    }
    @Test void nativeButtonUsesLiveLocalBlurButExplicitOverrideWins() {
        try (var manager = new ThemeManager()) {
            var scope = manager.global().child("glass");
            scope.configure(s -> s.override(ThemeTokens.LIQUID_GLASS, true).override(ThemeTokens.BLUR, 19f));
            var button = new UiButton(0, 0, 80, 20, Component.empty(), b -> {});
            button.bindTheme(scope);
            assertEquals(19f, button.getBackgroundBlurRadius());
            scope.configure(s -> s.override(ThemeTokens.BLUR, 5f));
            assertEquals(5f, button.getBackgroundBlurRadius());
            button.setBackgroundBlurRadius(3);
            scope.configure(s -> s.override(ThemeTokens.BLUR, 12f));
            assertEquals(3f, button.getBackgroundBlurRadius());
            button.setBackgroundBlurRadius(0);
            assertEquals(0f, button.getBackgroundBlurRadius());
        }
    }
    @Test void disabledBackdropAndHighContrastUseOpaquePaint() {
        try (var manager = new ThemeManager()) {
            var scope = manager.global().child("glass");
            scope.configure(s -> s.override(ThemeTokens.LIQUID_GLASS, true)
                    .override(ThemeTokens.SURFACE, ColorValue.argb(0x80334455)));
            var button = new UiButton(0, 0, 80, 20, Component.empty(), b -> {});
            button.bindTheme(scope);
            var idle = new DarkModeTheme.State(0, 0, 0, false, true, false);
            assertTrue((button.themePaint(idle, false).fill() >>> 24) < 255);
            scope.configure(s -> s.override(ThemeTokens.BACKDROP, false));
            assertEquals(255, button.themePaint(idle, false).fill() >>> 24);
            UiTheme.preview(UiTheme.fromScope(scope), () -> assertFalse(LiquidGlassTheme.useBackdrop(UiPreferences.DEFAULT)));
            scope.configure(s -> s.override(ThemeTokens.BACKDROP, true));
            var contrast = new DarkModeTheme.State(0, 0, 0, false, true, true, true);
            assertEquals(255, button.themePaint(contrast, false).fill() >>> 24);
            assertFalse(LiquidGlassTheme.useBackdrop(new UiPreferences(true, false, true)));
        }
    }
    @Test void clearHudBaselineIsUntintedWhileNewMaterialsExplicitlyOptIn() {
        assertFalse(UiTheme.fromTokens(com.dishanhai.sh_better_ui.theme.BuiltinThemes.hudGlassSnapshot()).glassRootTint());
        try (var manager = new ThemeManager()) {
            manager.setTheme(com.dishanhai.sh_better_ui.theme.MuoniumThemes.CRYSTAL);
            var theme = UiTheme.fromScope(manager.global());
            assertTrue(theme.glassRootTint());
            assertEquals(0x20, theme.rootGlassFill(0xe6000000) >>> 24);
            assertEquals(0x10, theme.innerGlassFill(0xe6000000) >>> 24);
            assertEquals(0xb8, theme.overlayGlassFill(0x33000000) >>> 24);
            assertEquals(theme.elevatedSurface(), theme.elevatedGlassFill(0x33000000));
        }
    }
    @Test void independentButtonBackdropAndFallbackDoNotLeakGlobalPalette() {
        try (var manager = new ThemeManager()) {
            var scope = manager.global().child("local");
            scope.configure(s -> s.override(ThemeTokens.LIQUID_GLASS, true).override(ThemeTokens.BACKDROP, true)
                    .override(ThemeTokens.BACKGROUND, ColorValue.argb(0xff103050)));
            var button = new UiButton(0, 0, 80, 20, Component.empty(), b -> {});
            button.bindTheme(scope); button.setCornerRadius(3);
            var paint = button.themePaint(new DarkModeTheme.State(0, 0, 0, false, true, false), false);
            var global = new UiTheme(false, 0xffff0000, 0xffff0000, -1, -1, -1, 0, 0, false);
            UiTheme.preview(global, () -> {
                assertTrue(LiquidGlassTheme.useBackdrop(UiPreferences.DEFAULT, paint));
                assertFalse(LiquidGlassTheme.useBackdrop(new UiPreferences(false, false, false), paint));
                assertEquals(0xff103050, LiquidGlassTheme.opaqueFallback(0x00000000, paint));
            });
        }
    }
}
