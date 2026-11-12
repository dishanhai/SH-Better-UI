package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassQuality;
import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证全库主题的解析与草稿预览隔离。 */
class UiThemeTest {
    @Test
    void hudBlurUsesFilteredOpticsWhileZeroKeepsClearRefraction() {
        var clear = LiquidGlassTheme.clearMaterial(true, 0);
        var soft = LiquidGlassTheme.clearMaterial(true, BuiltinThemes.DEFAULT_HUD_BLUR_RADIUS);
        assertEquals(GlassQuality.PERFORMANCE, clear.quality());
        assertEquals(GlassQuality.BALANCED, soft.quality());
        assertEquals((float) BuiltinThemes.DEFAULT_HUD_BLUR_RADIUS, soft.optics().blurRadius());
        assertEquals(clear.optics().refraction(), soft.optics().refraction());
        assertEquals(clear.foreground(), soft.foreground());
        assertTrue(soft.optics().scattering() > clear.optics().scattering());
    }

    @Test
    void presetsResolveDistinctMaterialsAndIcons() {
        var dark = UiTheme.resolve(ThemeSettings.DEFAULT);
        var light = UiTheme.resolve(ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.LIGHT));
        var glass = UiTheme.resolve(ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.LIQUID_GLASS));
        assertEquals(DarkModeTheme.SURFACE, dark.surface());
        assertNotEquals(dark.surface(), light.surface());
        assertNotEquals(dark.text(), light.text());
        assertSame(GraphicIcon.DARK_MODE, UiTheme.icon(ThemeSettings.Mode.DARK));
        assertSame(GraphicIcon.LIGHT_MODE, UiTheme.icon(ThemeSettings.Mode.LIGHT));
        assertSame(GraphicIcon.CUSTOM_THEME, UiTheme.icon(ThemeSettings.Mode.CUSTOM));
        assertSame(GraphicIcon.BLUR, UiTheme.icon(ThemeSettings.Mode.LIQUID_GLASS));
        assertTrue(glass.liquidGlass());
        assertEquals(UiTheme.fromTokens(BuiltinThemes.liquidGlassSnapshot()), glass);
        var hud = UiTheme.resolve(ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.HUD_GLASS));
        assertTrue(hud.hudGlass());
        assertTrue(hud.liquidGlass());
        assertFalse(glass.hudGlass());
        assertEquals(UiTheme.fromTokens(BuiltinThemes.hudGlassSnapshot()), hud);
    }

    @Test
    void customOverridesInheritLightBaseAndSurviveOtherSettingEdits() {
        var settings = new ThemeSettings(ThemeSettings.Mode.CUSTOM, ThemeSettings.Base.LIGHT,
                "", "#f6efe6", "", "", "6b3e87");
        var palette = UiTheme.resolve(settings);
        var light = UiTheme.resolve(settings.withMode(ThemeSettings.Mode.LIGHT));
        assertEquals("#F6EFE6", settings.surface());
        assertEquals(0xFFF6EFE6, palette.surface());
        assertEquals(0xFF6B3E87, palette.accent());
        assertEquals(light.background(), palette.background());
        assertEquals(light.text(), palette.text());
        assertEquals(settings, ClientUiSettings.DEFAULT.withTheme(settings)
                .withDisplay(true, true, false).theme());
        assertFalse(ThemeSettings.validColor("#abcdef00"));
    }

    @Test
    void draftPreviewChangesMaterialCallsAndRestoresAfterException() {
        var configured = UiTheme.current();
        var light = UiTheme.resolve(ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.LIGHT));
        assertThrows(IllegalStateException.class, () -> UiTheme.preview(light, () -> {
            assertEquals(light.text(), LiquidGlassTheme.controlText(true));
            assertEquals(light.background(), DarkModeTheme.backgroundColor());
            assertEquals(configured, UiTheme.resolve(ClientUiSettings.current().theme()));
            throw new IllegalStateException();
        }));
        assertSame(configured, UiTheme.current());
    }
}
