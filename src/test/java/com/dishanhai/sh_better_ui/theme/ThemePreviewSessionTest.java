// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ThemePreviewSessionTest {
    @Test void enumeratesRegistrationsAndKeepsSelectionIsolated() {
        try (var live = new ThemeManager(); var preview = new ThemePreviewSession(null)) {
            var original = live.preferences();
            var snapshot = live.snapshot();
            preview.sync(live, original.withReducedMotion(true));
            assertEquals(live.registry().themes().size(), preview.themes().size());
            preview.select(BuiltinThemes.LIGHT_ID);
            assertTrue(preview.scope().resolve(ThemeTokens.LIGHT));
            assertEquals(original, live.preferences());
            assertSame(snapshot, live.snapshot());
            assertFalse(preview.sync(live, original.withReducedMotion(true)));
        }
    }

    @Test void resourceReloadsAndRemovalRefreshWithoutLeakingScopes() {
        try (var live = new ThemeManager(); var preview = new ThemePreviewSession(null)) {
            var theme = Theme.builder("test:gallery").nameKey("test.gallery").parent(BuiltinThemes.LIGHT_ID)
                    .token(ThemeTokens.ACCENT, ColorValue.argb(0xff123456)).build();
            live.replaceResourceThemes(List.of(theme), List.of());
            preview.sync(live, live.preferences().withReducedMotion(true));
            preview.select(theme.id());
            assertEquals(0xff123456, preview.scope().resolve(ThemeTokens.ACCENT).argb());
            var oldScope = preview.scope();
            live.replaceResourceThemes(List.of(), List.of());
            assertTrue(preview.sync(live, live.preferences()));
            assertTrue(oldScope.isClosed());
            assertEquals(BuiltinThemes.DARK, preview.selected());
            preview.close();
            assertTrue(preview.sync(live, live.preferences()));
        }
    }
}
