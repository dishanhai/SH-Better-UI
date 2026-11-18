// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.dishanhai.sh_better_ui.theme.token.TokenRegistry;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.dishanhai.sh_better_ui.theme.token.ThemeTokens.*;
import static org.junit.jupiter.api.Assertions.*;

class DisplayStyleSuitesTest {
    private ThemeSnapshot snapshot(String id) {
        var tokens = new TokenRegistry();
        ThemeTokens.all().forEach(tokens::register);
        return new ThemeResolver().resolve(BuiltinThemes.registry(), tokens.snapshot(), List.of(id), null);
    }

    @Test void stableIdsAreImmutableUniqueAndNotLegacyAliases() {
        assertEquals(4, DisplayStyleSuites.IDS.stream().distinct().count());
        assertThrows(UnsupportedOperationException.class, () -> DisplayStyleSuites.IDS.add("test:invalid"));
        assertFalse(DisplayStyleSuites.matches(null));
        assertFalse(DisplayStyleSuites.matches(BuiltinThemes.DARK));
        assertFalse(DisplayStyleSuites.matches(MuoniumThemes.PORCELAIN_INK));
        for (String id : DisplayStyleSuites.IDS) {
            assertTrue(DisplayStyleSuites.matches(id));
            assertTrue(BuiltinThemes.IDS.contains(id));
            var theme = BuiltinThemes.registry().find(id).orElseThrow();
            assertEquals("SHBUI original display suite", theme.source());
            assertEquals("sh_better_ui.theme.dynamic." + id.split(":")[1], theme.nameKey());
            assertTrue(snapshot(id).diagnostics().isEmpty());
            // Do not override a player's separately selected local font or layout.
            assertFalse(theme.tokens().containsKey(FONT));
            assertFalse(theme.tokens().containsKey(FONT_SIZE));
        }
    }

    @Test void suiteForegroundsAndFocusedBordersAreReadableOnEverySurface() {
        for (String id : DisplayStyleSuites.IDS) {
            var s = snapshot(id);
            int backdrop = s.resolve(BACKGROUND).argb();
            for (var surface : List.of(BACKGROUND, SURFACE, SURFACE_SECONDARY, SURFACE_ELEVATED, SURFACE_OVERLAY)) {
                int fill = s.resolve(surface).argb();
                assertEquals(255, fill >>> 24);
                for (var text : List.of(TEXT, TEXT_SECONDARY, TEXT_DISABLED)) {
                    assertTrue(ContrastPolicy.ratio(s.resolve(text).argb(), fill, backdrop) >= 4.5,
                            () -> id + " " + text.id() + " on " + surface.id());
                }
                assertTrue(ContrastPolicy.ratio(s.resolve(BORDER_FOCUSED).argb(), fill, backdrop) >= 3, id);
            }
            assertTrue(ContrastPolicy.ratio(s.resolve(ON_PRIMARY).argb(), s.resolve(PRIMARY).argb(), backdrop) >= 4.5, id);
        }
    }

    @Test void interactiveStatesRemainDistinctAndPrimaryTextRemainsReadable() {
        for (String id : DisplayStyleSuites.IDS) {
            var s = snapshot(id);
            int surface = s.resolve(SURFACE).argb();
            int hover = s.resolve(HOVER).argb(), pressed = s.resolve(PRESSED).argb();
            assertNotEquals(hover, pressed);
            for (int fill : List.of(surface, ContrastPolicy.composite(hover, surface),
                    ContrastPolicy.composite(pressed, surface),
                    ContrastPolicy.composite(s.resolve(SELECTED).argb(), surface),
                    ContrastPolicy.composite(pressed, ContrastPolicy.composite(hover,
                            ContrastPolicy.composite(s.resolve(SELECTED).argb(), surface))))) {
                assertTrue(ContrastPolicy.ratio(s.resolve(TEXT).argb(), fill, surface) >= 4.5, id);
            }
            assertTrue(s.resolve(RADIUS_SMALL) < s.resolve(RADIUS_MEDIUM));
            assertTrue(s.resolve(RADIUS_MEDIUM) < s.resolve(RADIUS_LARGE));
            assertTrue(s.resolve(FAST).compareTo(s.resolve(NORMAL)) < 0);
            assertTrue(s.resolve(NORMAL).compareTo(s.resolve(SLOW)) < 0);
            assertEquals(0f, s.resolve(BLUR));
            assertFalse(s.resolve(BACKDROP));
        }
        assertNotEquals(snapshot(DisplayStyleSuites.PORCELAIN).resolve(RADIUS_SMALL),
                snapshot(DisplayStyleSuites.WARM_PAPER).resolve(RADIUS_SMALL));
    }

    @Test void resourceReloadPreservesSuitesAndPreviewDoesNotMutateLiveSelection() {
        try (var manager = new ThemeManager(); var preview = new ThemePreviewSession(null)) {
            var original = manager.preferences();
            manager.replaceResourceThemes(List.of(), List.of());
            preview.sync(manager, original.withReducedMotion(true));
            for (String id : DisplayStyleSuites.IDS) {
                assertTrue(preview.themes().stream().anyMatch(t -> t.id().equals(id)));
                preview.select(id);
                assertEquals(id, preview.selected());
                assertEquals(original, manager.preferences());
                manager.preferences(original.withFixedTheme(id).withReducedMotion(true));
                assertEquals(snapshot(id).resolve(ACCENT), manager.snapshot().resolve(ACCENT));
                manager.preferences(original);
            }
        }
    }

    @Test void highContrastAndReducedMotionTakePrecedence() {
        try (var manager = new ThemeManager()) {
            for (String id : DisplayStyleSuites.IDS) {
                manager.preferences(manager.preferences().withFixedTheme(id).withHighContrast(true).withReducedMotion(true));
                var scope = manager.global();
                assertEquals(21d, ContrastPolicy.ratio(scope.resolve(TEXT).argb(),
                        scope.resolve(SURFACE).argb(), scope.resolve(BACKGROUND).argb()), .001);
                assertEquals(2f, scope.resolve(BORDER_WIDTH));
                assertFalse(scope.preferences().animate());
                assertTrue(scope.preferences().reducedMotion());
                assertEquals(0f, scope.resolve(BLUR));
            }
        }
    }
}
