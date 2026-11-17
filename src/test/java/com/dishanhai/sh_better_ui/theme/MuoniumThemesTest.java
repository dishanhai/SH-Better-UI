// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MuoniumThemesTest {
    private static final List<String> IDS = List.of(MuoniumThemes.VELVET_ROSE,
            MuoniumThemes.MIDNIGHT_GRAPHITE, MuoniumThemes.PORCELAIN_INK,
            MuoniumThemes.OBSIDIAN_RED, MuoniumThemes.AURORA_TIDE, MuoniumThemes.SUNSET_EMBER,
            MuoniumThemes.SAGE_MIST, MuoniumThemes.PEARL_LIGHT, MuoniumThemes.XIANGYUN_QINGCHUAN,
            MuoniumThemes.CANGMING_YUYOU, MuoniumThemes.SHUANGWAN_XIUYAN, MuoniumThemes.MEIGUI_YUEBAI,
            MuoniumThemes.QINGLAN_YUNZHI);
    private static final java.util.Set<String> LIGHT_IDS = java.util.Set.of(MuoniumThemes.PORCELAIN_INK,
            MuoniumThemes.PEARL_LIGHT, MuoniumThemes.XIANGYUN_QINGCHUAN, MuoniumThemes.CANGMING_YUYOU,
            MuoniumThemes.SHUANGWAN_XIUYAN, MuoniumThemes.MEIGUI_YUEBAI, MuoniumThemes.QINGLAN_YUNZHI);

    @Test void presetsAreRegisteredSelectableAndSurviveReload() {
        try (var manager = new ThemeManager()) {
            manager.replaceResourceThemes(List.of(), List.of());
            for (String id : IDS) {
                assertTrue(BuiltinThemes.IDS.contains(id));
                assertTrue(manager.registry().find(id).isPresent());
                manager.preferences(manager.preferences().withReducedMotion(true).withFixedTheme(id));
                assertFalse(manager.showingEnvironmentDefault());
                assertEquals(LIGHT_IDS.contains(id), manager.snapshot().resolve(ThemeTokens.LIGHT));
                assertEquals(0f, manager.snapshot().resolve(ThemeTokens.BLUR));
                assertFalse(manager.snapshot().resolve(ThemeTokens.LIQUID_GLASS));
            }
        }
    }

    @Test void galleryShowsAllPresetsWithoutChangingLiveSelection() {
        try (var manager = new ThemeManager(); var preview = new ThemePreviewSession(null)) {
            var original = manager.preferences();
            preview.sync(manager, original.withReducedMotion(true));
            for (String id : IDS) {
                assertTrue(preview.themes().stream().anyMatch(theme -> theme.id().equals(id)));
                preview.select(id);
                assertEquals(id, preview.selected());
                assertEquals(original, manager.preferences());
            }
        }
    }

    @Test void normalTextAndPrimaryActionsHaveReadableContrast() {
        try (var manager = new ThemeManager()) {
            for (String id : IDS) {
                manager.preferences(manager.preferences().withReducedMotion(true).withFixedTheme(id));
                var snapshot = manager.snapshot();
                for (var surface : List.of(ThemeTokens.BACKGROUND, ThemeTokens.SURFACE,
                        ThemeTokens.SURFACE_SECONDARY, ThemeTokens.SURFACE_ELEVATED)) {
                    for (var text : List.of(ThemeTokens.TEXT, ThemeTokens.TEXT_SECONDARY)) {
                        assertTrue(contrast(snapshot.resolve(text), snapshot.resolve(surface)) >= 4.5,
                                () -> id + " " + text.id() + " on " + surface.id());
                    }
                }
                assertTrue(contrast(snapshot.resolve(ThemeTokens.ON_PRIMARY), snapshot.resolve(ThemeTokens.PRIMARY)) >= 4.5, id);
                assertTrue(contrast(snapshot.resolve(ThemeTokens.BORDER_FOCUSED), snapshot.resolve(ThemeTokens.SURFACE)) >= 3, id);
            }
        }
    }

    @Test void accessibilityOverridesRemainAuthoritative() {
        try (var manager = new ThemeManager()) {
            for (String id : IDS) {
                manager.preferences(manager.preferences().withFixedTheme(id).withHighContrast(true).withReducedMotion(true));
                var scope = manager.global();
                assertEquals(21d, contrast(scope.resolve(ThemeTokens.TEXT), scope.resolve(ThemeTokens.SURFACE)), .001);
                assertEquals(2f, scope.resolve(ThemeTokens.BORDER_WIDTH));
                assertEquals(0f, scope.resolve(ThemeTokens.BLUR));
                assertEquals(0f, scope.resolve(ThemeTokens.GLOW));
            }
        }
    }

    @Test void glassPresetsCarryActualNativeMaterialsAndLayeredTransparency() {
        try (var manager = new ThemeManager(); var preview = new ThemePreviewSession(null)) {
            manager.replaceResourceThemes(List.of(), List.of());
            preview.sync(manager, manager.preferences().withReducedMotion(true));
            for (String id : List.of(MuoniumThemes.LIQUID_GLASS, MuoniumThemes.AURORA_FROST, MuoniumThemes.CRYSTAL)) {
                assertTrue(BuiltinThemes.IDS.contains(id));
                preview.select(id);
                var scope = preview.scope();
                assertTrue(scope.resolve(ThemeTokens.LIQUID_GLASS));
                assertEquals(!id.equals(MuoniumThemes.AURORA_FROST), scope.resolve(ThemeTokens.HUD_GLASS));
                assertTrue(scope.resolve(ThemeTokens.BACKDROP));
                int rootAlpha = scope.resolve(ThemeTokens.SURFACE).argb() >>> 24;
                assertTrue(rootAlpha > 0 && rootAlpha < 255);
                assertTrue(scope.resolve(ThemeTokens.SURFACE_SECONDARY).argb() >>> 24 < rootAlpha);
                assertTrue(scope.resolve(ThemeTokens.SURFACE_OVERLAY).argb() >>> 24 > rootAlpha);
                assertTrue(scope.resolve(ThemeTokens.BACKGROUND).argb() >>> 24 < 255);
                assertEquals(id.equals(MuoniumThemes.AURORA_FROST) ? 6f : .75f, scope.resolve(ThemeTokens.BLUR));
                assertTrue(scope.resolve(ThemeTokens.RADIUS_LARGE) >= 12f);
                var nativeTheme = com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme.fromScope(scope);
                assertTrue(nativeTheme.liquidGlass());
                assertEquals(!id.equals(MuoniumThemes.AURORA_FROST), nativeTheme.hudGlass());
                assertEquals(rootAlpha, nativeTheme.surface() >>> 24);
            }
        }
    }

    @Test void glassHasSolidHighContrastAndNoBackdropFallbacks() {
        try (var manager = new ThemeManager()) {
            for (String id : List.of(MuoniumThemes.LIQUID_GLASS, MuoniumThemes.AURORA_FROST, MuoniumThemes.CRYSTAL)) {
                manager.preferences(manager.preferences().withFixedTheme(id).withHighContrast(true).withReducedMotion(true));
                var scope = manager.global();
                assertEquals(255, scope.resolve(ThemeTokens.SURFACE).argb() >>> 24);
                assertEquals(21d, contrast(scope.resolve(ThemeTokens.TEXT), scope.resolve(ThemeTokens.SURFACE)), .001);
                assertFalse(scope.resolve(ThemeTokens.BACKDROP));
                assertEquals(0f, scope.resolve(ThemeTokens.BLUR));
            }
        }
    }

    private static double contrast(ColorValue a, ColorValue b) {
        double x = luminance(a.argb()), y = luminance(b.argb());
        return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);
    }
    private static double luminance(int argb) {
        return .2126 * channel(argb >>> 16 & 255) + .7152 * channel(argb >>> 8 & 255) + .0722 * channel(argb & 255);
    }
    private static double channel(int value) {
        double c = value / 255d;
        return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4);
    }
}
