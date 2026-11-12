package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.GlassBackdropSettings;
import com.dishanhai.sh_better_ui.config.ThemeOverrides;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证全组模板、部分覆写和预览隔离。 */
class ThemeEffectsTest {
    @Test
    void allTemplatesRoundTripThroughClientConfig() {
        var overrides = ThemeOverrides.EMPTY;
        for (String group : ThemeEffects.groups()) {
            overrides = overrides.withGroup(group, ThemeEffects.template(group));
        }
        assertEquals(26, overrides.groups().size());
        var persisted = new ThemeOverrides(overrides.json());
        assertEquals(overrides, persisted);
        assertDoesNotThrow(() -> ThemeEffects.validate(persisted));
    }

    @Test
    void partialProfilesKeepFallbackAndAreDormantInOtherModes() {
        var overrides = new ThemeOverrides("""
                {"dotMatrix":{"grid":{"spacing":12},"palette":{"dotArgb":"#7755AA"}},
                 "glass":{"strength":0.65},"feedback":{"tooltipDelayMs":200}}
                """);
        var custom = ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.CUSTOM).withEffects(overrides);
        var original = DotMatrixProfile.VIOLET;
        var resolved = ThemeEffects.profile(custom, "dotMatrix", original);
        assertEquals(12, resolved.grid().spacing());
        assertEquals(original.grid().dotSize(), resolved.grid().dotSize());
        assertEquals(0xFF7755AA, resolved.palette().dotArgb());
        assertSame(original, ThemeEffects.profile(custom.withMode(ThemeSettings.Mode.LIGHT), "dotMatrix", original));

        var before = ThemeEffects.settings();
        var draft = ClientUiSettings.DEFAULT.withTheme(custom);
        assertThrows(IllegalStateException.class, () -> ThemeEffects.preview(draft, () -> {
            assertEquals(0.65f, ThemeEffects.glass().strength());
            assertEquals(200, UiPreferences.configured().feedback().tooltipDelayMs());
            throw new IllegalStateException();
        }));
        assertSame(before, ThemeEffects.settings());
    }

    @Test
    void hudBackgroundReadsOnlyItsOwnEffectAndKeepsThemeInEditorPreview() {
        var overrides = new ThemeOverrides("""
                {"dotMatrix":{"grid":{"spacing":12}},"codeRain":{"density":0.25},"glass":{"strength":0.65}}
                """);
        var hud = ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.HUD_GLASS)
                .withHudBackgroundEffect("dotMatrix").withEffects(overrides);
        var original = DotMatrixProfile.VIOLET;
        assertEquals(12, ThemeEffects.profile(hud, "dotMatrix", original).grid().spacing());
        assertSame(CodeRainProfile.DEFAULT, ThemeEffects.profile(hud, "codeRain", CodeRainProfile.DEFAULT));
        assertSame(GlassBackdropSettings.DEFAULT, ThemeEffects.profile(hud, "glass", GlassBackdropSettings.DEFAULT));
        assertSame(original, ThemeEffects.profile(hud.withHudBackgroundEffect(""), "dotMatrix", original));
        assertEquals(0.25f, ThemeEffects.profile(hud.withHudBackgroundEffect("codeRain"),
                "codeRain", CodeRainProfile.DEFAULT).density());

        var draft = ThemeEffects.previewSettings(ClientUiSettings.DEFAULT.withTheme(hud), "dotMatrix",
                overrides.group("dotMatrix"), JsonParser.parseString("{\"grid\":{\"spacing\":16}}").getAsJsonObject());
        assertEquals(ThemeSettings.Mode.HUD_GLASS, draft.theme().mode());
        assertEquals("dotMatrix", draft.theme().hudBackgroundEffect());
        assertEquals(16, ThemeEffects.profile(draft.theme(), "dotMatrix", original).grid().spacing());
        assertEquals(12, ThemeEffects.profile(hud, "dotMatrix", original).grid().spacing());
    }

    @Test
    void invalidGroupDoesNotDisableOtherGroups() {
        var overrides = new ThemeOverrides("""
                {"glass":{"strength":8},"codeRain":{"density":0.25}}
                """);
        assertThrows(IllegalArgumentException.class, () -> ThemeEffects.validate(overrides));
        var custom = ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.CUSTOM).withEffects(overrides);
        assertSame(GlassBackdropSettings.DEFAULT, ThemeEffects.profile(custom, "glass", GlassBackdropSettings.DEFAULT));
        assertEquals(0.25f, ThemeEffects.profile(custom, "codeRain", CodeRainProfile.DEFAULT).density());
    }
}
