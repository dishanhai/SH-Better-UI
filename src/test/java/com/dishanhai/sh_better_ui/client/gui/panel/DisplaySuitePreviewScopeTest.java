// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.theme.ThemeBinding;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.style.StyleInvalidation;
import org.junit.jupiter.api.Test;
import static com.dishanhai.sh_better_ui.theme.token.ThemeTokens.*;
import static org.junit.jupiter.api.Assertions.*;

class DisplaySuitePreviewScopeTest {
    @Test void actualPreviewInitializationUsesChildAndLeavesRootUnchanged() {
        try(var manager=new ThemeManager()) {
            var root=manager.global();
            var original=root.snapshot();
            var preferences=manager.preferences();
            var colors=new UiTheme(false,0xff102030,0xff203040,0xff405060,0xfffafafa,0xff70ccaa,31);
            var scope=DisplaySuitePreviewScreen.createPreviewScope(manager,colors,UiPreferences.DEFAULT);
            assertSame(root,scope.parent());
            assertNotSame(root,scope);
            assertEquals(colors.background(),scope.resolve(BACKGROUND).argb());
            assertEquals(colors.surface(),scope.resolve(SURFACE).argb());
            assertEquals(colors.border(),scope.resolve(BORDER).argb());
            assertEquals(colors.text(),scope.resolve(TEXT).argb());
            assertEquals(colors.accent(),scope.resolve(ACCENT).argb());
            assertEquals(colors.accent(),scope.resolve(PRIMARY).argb());
            assertSame(original,root.snapshot());
            assertSame(original,manager.snapshot());
            assertEquals(preferences,manager.preferences());
            var owner=scope.invalidationOwner(ignored->{});
            var binding=scope.bind(ThemeBinding.of(ACCENT),owner,StyleInvalidation.PAINT);
            assertFalse(binding.isClosed());
            scope.close();
            assertTrue(scope.isClosed());
            assertTrue(binding.isClosed());
            assertFalse(root.isClosed());
            assertSame(original,root.snapshot());
            // Reopening creates a fresh usable scope, not a stale released binding tree.
            try(var reopened=DisplaySuitePreviewScreen.createPreviewScope(manager,colors,UiPreferences.DEFAULT)) {
                assertEquals(colors.accent(),reopened.resolve(ACCENT).argb());
            }
        }
    }
}
